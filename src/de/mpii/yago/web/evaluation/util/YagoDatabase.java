package de.mpii.yago.web.evaluation.util;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import javatools.datatypes.ArrayQueue;
import mpi.database.DBConnection;
import mpi.database.DBSettings;
import mpi.database.DataManager;
import mpi.database.interfaces.DBPreparedStatementInterface;
import mpi.database.interfaces.DBStatementInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mpii.yago.web.evaluation.model.EvaluationEntry;
import de.mpii.yago.web.evaluation.model.Fact;

public class YagoDatabase {

  public static final String EVAL_TARGET = "Evaluation Target";

  public static final String EVALS = "Evaluations";

  public static final String CORRECT = "Correct";

  public static final String RATIO = "Ratio (%)";

  public static final String WILSON_CENTER = "Wilson Center (%)";

  public static final String WILSON_WIDTH = "Wilson Width (%)";

  public static final String PROGRESS = "Progress";

  private Set<String> separateTechniques;

  private Set<String> excludeTechniques;

  private Set<String> excludeRelations;

  private Set<String> literalClasses;

  private Set<String> economicRelations;

  private static String FACTS_TABLE;

  private static String ID;

  private static String SUBJECT;

  private static String PREDICATE;

  private static String OBJECT;

  private static String YAGO_LITERAL = "rdfs:Literal";

  private static String SUBCLASS_OF = "rdfs:subClassOf";

  private String YAGO_FACT = "rdf:Statement";

  private String DOMAIN = "rdfs:domain";

  private String RANGE = "rdfs:range";

  private String USING = "<extractionTechnique>";

  private Logger logger = LoggerFactory.getLogger(YagoDatabase.class);

  private DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(Locale.ENGLISH);

  public YagoDatabase() throws SQLException {
    nf.applyPattern("#.##");

    separateTechniques = loadValuesFromSettings("separate_technique");
    excludeTechniques = loadValuesFromSettings("exclude_technique");
    excludeRelations = loadValuesFromSettings("exclude_relation");
    economicRelations = loadValuesFromSettings("economic_relation");
    literalClasses = loadLiteralClasses();
  }

  private Set<String> loadLiteralClasses() throws SQLException {
    Set<String> literals = new HashSet<String>();
    DBConnection con = null;
    try {
      con = DataManager.getConnection("literals");
      DBStatementInterface stmt = con.getStatement();
      String sql = "WITH RECURSIVE subclassOf(" + SUBJECT + ", " + OBJECT + ") AS (" + "SELECT " + SUBJECT + ", " + OBJECT + " FROM " + FACTS_TABLE + " WHERE " + OBJECT + " = '" + YAGO_LITERAL + "' AND " + PREDICATE + " = '" + SUBCLASS_OF + "' "
          + "UNION ALL " + "SELECT f." + SUBJECT + ", f." + OBJECT + " " + "FROM subclassOf c, " + FACTS_TABLE + " f " + "WHERE f." + PREDICATE + " = '" + SUBCLASS_OF + "' AND c." + SUBJECT + " = f." + OBJECT + ") " + "SELECT " + SUBJECT + ", "
          + OBJECT + " FROM subclassOf";
      ResultSet rs = stmt.executeQuery(sql);
      while (rs.next()) {
        String s = rs.getString(SUBJECT);
        String o = rs.getString(OBJECT);
        literals.add(s);
        literals.add(o);
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return literals;
  }

  private Set<String> loadValuesFromSettings(String key) throws SQLException {
    Set<String> values = new HashSet<String>();
    DBConnection con = null;
    try {
      con = DataManager.getConnection("settings");
      DBStatementInterface stmt = con.getStatement();
      String sql = "SELECT value FROM evaluation_settings WHERE key='" + key + "'";
      ResultSet rs = stmt.executeQuery(sql);
      while (rs.next()) {
        String value = rs.getString("value");
        values.add(value);
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return values;
  }

  public List<EvaluationEntry> getEvaluationEntriesFromDB() throws IOException {
    return getEvaluationEntriesFromDB(false);
  }

  /**
   * Returns all FIRST evaluation entries (not those done to measure inter-annotator agreement)
   * 
   * @param includeIgnoredFacts Set to true if 'ignored' facts should be included
   * @return  All evaluation entries
   * @throws IOException
   */
  public List<EvaluationEntry> getEvaluationEntriesFromDB(boolean includeIgnoredFacts) throws IOException {
    List<EvaluationEntry> entries = new LinkedList<EvaluationEntry>();
    Set<String> allIds = new HashSet<String>();
    DBConnection con = null;
    try {
      con = DataManager.getConnection("StandingsPage");
      DBStatementInterface stmt = con.getStatement();
      StringBuilder sql = new StringBuilder("SELECT * FROM evaluation");
      if (!includeIgnoredFacts) {
        sql.append(" WHERE eval<>'" + EvaluationManager.IGNORE + "'");
      }
      sql.append(" ORDER BY timepoint ASC");
      ResultSet rs = stmt.executeQuery(sql.toString());
      while (rs.next()) {
        String predicate = rs.getString(PREDICATE);
        String technique = rs.getString("technique");
        String factid = rs.getString("factid");
        if (excludeRelations.contains(predicate) || excludeTechniques.contains(technique)) {
          continue;
        }
        // only add first evaluation, second evaluation is just for inter-annotator-agreement
        if (!allIds.contains(factid)) {
          Fact f = new Fact(rs.getString(SUBJECT), predicate, rs.getString(OBJECT), factid);
          EvaluationEntry e = new EvaluationEntry(rs.getDate("timepoint"), f, technique, rs.getString("username"), rs.getString("eval"), rs.getString("target"));
          entries.add(e);
          allIds.add(factid);
        }
      }
      rs.close();
    } catch (SQLException e) {
      logger.warn("Error when retrieving standings: " + e);
    } finally {
      DataManager.releaseConnection(con);
    }
    return entries;
  }

  public List<Map<String, String>> groupEntriesByEvaluationTarget(List<EvaluationEntry> entries, String evaluationTarget) {
    return groupEntriesByEvaluationTarget(entries, evaluationTarget, EVAL_TARGET, true);
  }

  public List<Map<String, String>> groupEntriesByEvaluationTarget(List<EvaluationEntry> entries, String evaluationTarget, final String sortKey, final boolean sortOrder) {
    List<Map<String, String>> groupedEntries = new LinkedList<Map<String, String>>();

    Map<String, Integer> totals = new HashMap<String, Integer>();
    Map<String, Integer> corrects = new HashMap<String, Integer>();

    for (EvaluationEntry e : entries) {
      if (!e.getTarget().equals(evaluationTarget)) {
        continue; // only look at entries for current target
      }

      Integer total = totals.get(e.getValueForTarget(evaluationTarget));

      if (total == null) {
        totals.put(e.getValueForTarget(evaluationTarget), 1);
        corrects.put(e.getValueForTarget(evaluationTarget), 0);
      } else {
        totals.put(e.getValueForTarget(evaluationTarget), total + 1);
      }

      Integer correct = corrects.get(e.getValueForTarget(evaluationTarget));

      if (e.isCorrect()) {
        corrects.put(e.getValueForTarget(evaluationTarget), correct + 1);
      }
    }

    for (String pool : totals.keySet()) {
      Map<String, String> row = new HashMap<String, String>();
      row.put(EVAL_TARGET, pool);

      Integer totalEvals = totals.get(pool);
      Integer correctEvals = corrects.get(pool);

      row.put(EVALS, Integer.toString(totalEvals));
      row.put(CORRECT, Integer.toString(correctEvals));

      double ratio = (double) correctEvals / (double) totalEvals;

      row.put(RATIO, nf.format(ratio * 100));

      double[] wilson = Wilson.wilson(totalEvals, correctEvals);

      row.put(WILSON_CENTER, nf.format(wilson[0] * 100));
      row.put(WILSON_WIDTH, nf.format(wilson[1] * 100));

      row.put(PROGRESS, nf.format(Wilson.progress(totalEvals, correctEvals)));

      groupedEntries.add(row);
    }

    Collections.sort(groupedEntries, new Comparator<Map<String, String>>() {

      @Override
      public int compare(Map<String, String> one, Map<String, String> two) {
        if (sortOrder) {
          return one.get(sortKey).compareTo(two.get(sortKey));
        } else {
          return two.get(sortKey).compareTo(one.get(sortKey));
        }
      }
    });

    return groupedEntries;
  }

  /**
   * Fills the list of candidate random facts with all associated facts from
   * the database.
   * @throws SQLException 
   */
  public Queue<Fact> randomFactsForRelation(String relation, int numberOfFacts) throws SQLException {
    DBConnection con = null;
    Queue<Fact> result = new ArrayQueue<Fact>();
    try {
      StringBuffer query = new StringBuffer("SELECT first,count FROM facts_rel_tech_meta fs WHERE " + PREDICATE + "=");
      query.append("'" + relation + "'");
      //    for (String technique : separateTechniques)
      //      query.append(" AND fs.technique<>").append("'"+technique+"'");
      for (String technique : excludeTechniques)
        query.append(" AND fs.technique<>").append("'" + technique + "'");

      query.append(" AND fs.technique NOT LIKE '\"RuleExtractor%'");
      con = DataManager.getConnection("randomRelations");
      DBStatementInterface stmt = con.getStatement();
      ResultSet dbResults = stmt.executeQuery(query.toString());
      List<Integer[]> intervals = new ArrayList<Integer[]>();
      int totalFacts = 0;
      while (dbResults.next()) {
        Integer[] interval = new Integer[2];
        interval[0] = dbResults.getInt(1);
        interval[1] = dbResults.getInt(2);
        intervals.add(interval);
        totalFacts += interval[1];
      }

      if (intervals.size() < 1) {
        // nothing to evaluate for this relation
        dbResults.close();
        return result;
      }

      Set<Integer> randomIdsSet = new HashSet<Integer>();

      StringBuilder randomIds = new StringBuilder();

      Random rg = new Random();

      // choose randomly from intervals, weighted according to size
      double intervalChooser = rg.nextDouble();
      double current = 0.0;

      int chosenInterval = 0;

      for (; chosenInterval < intervals.size(); chosenInterval++) {
        int size = intervals.get(chosenInterval)[1];

        double weight = (double) size / (double) totalFacts;

        if (intervalChooser < (current + weight)) {
          break;
        } else {
          current += weight;
        }
      }

      Integer[] interval = intervals.get(chosenInterval);

      for (int i = 0; i < numberOfFacts; i++) {
        // choose random number for all possible relation facts
        int randomInt = interval[0] + (int) (rg.nextDouble() * interval[1]);

        if (!randomIdsSet.contains(randomInt)) {
          randomIds.append(Integer.toString(randomInt));
          randomIds.append(",");

          randomIdsSet.add(randomInt);
        }
      }

      dbResults.close();

      if (randomIds.length() == 0) {
        return result;
      }

      query = new StringBuffer("SELECT " + FACTS_TABLE + "." + SUBJECT + ", " + FACTS_TABLE + "." + PREDICATE + ", " + FACTS_TABLE + "." + OBJECT + ", " + FACTS_TABLE + "." + ID + ", facts_rel_tech_sorted.technique " + "FROM " + FACTS_TABLE
          + ", facts_rel_tech_sorted " + "WHERE " + FACTS_TABLE + "." + ID + "=facts_rel_tech_sorted.yagoid " + "AND facts_rel_tech_sorted.id IN (");
      query.append(randomIds.substring(0, randomIds.length() - 1)).append(") ");
      query.append("AND " + FACTS_TABLE + "." + ID + " NOT IN (SELECT factid FROM evaluation)");

      dbResults = stmt.executeQuery(query.toString());

      // get facts
      while (dbResults.next()) {
        Fact f = new Fact(dbResults.getString(1), dbResults.getString(2), dbResults.getString(3), dbResults.getString(4));
        f.setTechnique(dbResults.getString(5));
        result.add(f);
      }

      dbResults.close();

    } finally {
      DataManager.releaseConnection(con);
    }
    return result;
  }

  /* not used
  private static boolean isIncluded(List<Integer[]> intervals, int number) {
    for (Integer[] interval : intervals) {
      if (number >= interval[0] && number < (interval[0] + interval[1])) {
        return true;
      }
    }
    
    return false;
  }
  */
  /**
   * Returns a list of candidate random facts from the database for a given
   * technique.
   */
  public Queue<Fact> randomFactsForTechnique(String technique, int numberOfFacts) throws SQLException {
    DBConnection con = null;
    Queue<Fact> result = new ArrayQueue<Fact>();
    try {
      StringBuffer query = new StringBuffer("SELECT first,count FROM facts_tech_rel_meta WHERE technique=");
      query.append("E'" + technique + "'");
      con = DataManager.getConnection("randomTechniques");
      DBStatementInterface stmt = con.getStatement();
      ResultSet dbResults = stmt.executeQuery(query.toString());

      if (!dbResults.next()) {
        dbResults.close();
        return result;
      }

      int first = dbResults.getInt(1);
      int count = dbResults.getInt(2);

      Set<Integer> randomIdsSet = new HashSet<Integer>();

      StringBuilder randomIds = new StringBuilder();

      Random rg = new Random();

      for (int i = 0; i < numberOfFacts; i++) {
        int randomInt = (int) (rg.nextDouble() * (double) count + first);

        if (!randomIdsSet.contains(randomInt)) {
          randomIds.append(Integer.toString(randomInt));
          randomIds.append(",");

          randomIdsSet.add(randomInt);
        }
      }

      dbResults.close();

      if (randomIds.length() == 0) {
        return result;
      }

      query = new StringBuffer("SELECT f." + ID + " AS " + ID + ", f." + SUBJECT + " AS " + SUBJECT + ", f." + PREDICATE + " AS " + PREDICATE + ", f." + OBJECT + " AS " + OBJECT + " " + "FROM " + FACTS_TABLE
          + " f, facts_tech_rel_sorted fs, evaluation e " + "WHERE f." + ID + "=fs.yagoid AND fs.id IN (");
      query.append(randomIds.substring(0, randomIds.length() - 1)).append(") ");
      query.append("AND f." + ID + " NOT IN (SELECT factid FROM evaluation)");

      dbResults = stmt.executeQuery(query.toString());

      while (dbResults.next()) {
        Fact f = new Fact(dbResults.getString(SUBJECT), dbResults.getString(PREDICATE), dbResults.getString(OBJECT), dbResults.getString(ID));
        f.setTechnique(technique);
        result.add(f);
      }
      dbResults.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return (result);
  }

  public static synchronized void connectToDBWithProperties(String dbSettings) throws IOException {
    if (!DataManager.isConnected()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      Properties p = new Properties();
      p.load(classLoader.getResourceAsStream(dbSettings));
      FACTS_TABLE = p.getProperty("FACTS_TABLE", "yagofacts");
      ID = p.getProperty("ID", "id");
      SUBJECT = p.getProperty("SUBJECT", "subject");
      PREDICATE = p.getProperty("PREDICATE", "predicate");
      OBJECT = p.getProperty("OBJECT", "object");
      DBSettings settings = new DBSettings(p.getProperty("hostname"), Integer.parseInt(p.getProperty("port")), p.getProperty("user"), p.getProperty("pass"), 20, p.getProperty("type"), p.getProperty("database"));
      DataManager.connect(settings);
    }
  }

  public List<Fact> getRelatedFactsForFact(Fact fact) throws SQLException {
    List<Fact> relatedFacts = new LinkedList<Fact>();
    DBConnection con = null;
    try {
      StringBuilder query = new StringBuilder("SELECT " + ID + "," + PREDICATE + "," + OBJECT + " FROM " + FACTS_TABLE + " WHERE " + SUBJECT + "='" + fact.getId() + "'");
      for (String relation : excludeRelations)
        query.append(" AND " + PREDICATE + "<>").append("'" + relation + "'");
      con = DataManager.getConnection("relatedFacts");
      DBStatementInterface stmt = con.getStatement();
      ResultSet dbResults = stmt.executeQuery(query.toString());
      int count = 1;
      while (dbResults.next()) {
        String id = dbResults.getString(ID);
        if (id == null) {
          id = fact.getId() + count;
          count++;
        }
        Fact relatedFact = new Fact(fact.getId(), dbResults.getString(PREDICATE), dbResults.getString(OBJECT), id);
        relatedFacts.add(relatedFact);
      }
      dbResults.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return relatedFacts;
  }

  public static String getArg2(String arg1, String relation) throws SQLException {
    String arg2 = null;
    DBConnection con = null;
    try {
      con = DataManager.getConnection("gloss");
      DBStatementInterface stmt = con.getStatement();
      ResultSet rs = stmt.executeQuery("SELECT " + OBJECT + " FROM " + FACTS_TABLE + " WHERE " + PREDICATE + "='" + relation + "' AND " + SUBJECT + "='" + arg1 + "'");
      if (rs.next()) {
        arg2 = rs.getString(OBJECT);
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return arg2;
  }

  public double[] getAgreementAndKappa() throws SQLException {
    double kappa = 0;
    double agreementProb = 0;
    DBConnection con = null;
    try {
      con = DataManager.getConnection("kappa");
      DBStatementInterface stmt = con.getStatement();
      Map<String, Integer> userCorrectCount = new HashMap<String, Integer>();
      Map<String, Integer> userTotalCount = new HashMap<String, Integer>();
      String query = "SELECT username, eval FROM evaluation WHERE eval='@right' OR eval='@wrong'"; // WHERE timepoint > TIMESTAMP '2011-01-01 00:00:00'";
      ResultSet rs = stmt.executeQuery(query);
      while (rs.next()) {
        String user = rs.getString(1);
        if (user != null) {
          boolean correct = rs.getString("eval").equals(EvaluationManager.RIGHT);
          if (correct) {
            Integer correctCount = userCorrectCount.get(user);
            if (correctCount == null) {
              userCorrectCount.put(user, 1);
            } else {
              userCorrectCount.put(user, correctCount + 1);
            }
          }
          Integer totalCount = userTotalCount.get(user);
          if (totalCount == null) {
            userTotalCount.put(user, 1);
          } else {
            userTotalCount.put(user, totalCount + 1);
          }
        }
      }
      rs.close();
      Map<String, Double> userCorrectProbability = new HashMap<String, Double>();
      for (String user : userTotalCount.keySet()) {
        Integer correctCount = userCorrectCount.get(user);
        if (correctCount == null) {
          correctCount = 0;
        }
        double correctProb = (double) correctCount / (double) userTotalCount.get(user);
        userCorrectProbability.put(user, correctProb);
      }
      query = "SELECT e.factid,e.username,e.eval FROM " + "(SELECT COUNT(factid) AS fid, factid FROM evaluation " + "WHERE eval='@right' OR eval='@wrong' " +
      //    "WHERE timepoint > TIMESTAMP '2011-01-01 00:00:00' " +
          "GROUP BY factid " + "HAVING COUNT(factid) = 2) cc " + "INNER JOIN evaluation e ON e.factid = cc.factid " + "ORDER BY cc.factid";
      rs = stmt.executeQuery(query);
      int totalSubjects = 0;
      int totalAgreement = 0;
      int sumRight = 0;
      int sumWrong = 0;
      int sumAgreement = 0;
      String lastUser = "";
      String lastFactid = "";
      boolean lastCorrect = false;
      while (rs.next()) {
        String factid = rs.getString(1);
        String user = rs.getString(2);
        boolean correct = rs.getString(3).equals(EvaluationManager.RIGHT);
        if (user == null) user = "";
        if (lastFactid.equals(factid) && !lastUser.equals(user)) {
          totalSubjects++;
          int r = 0;
          int w = 0;
          if (correct) {
            sumRight++;
            r++;
          } else {
            sumWrong++;
            w++;
          }
          if (lastCorrect) {
            sumRight++;
            r++;
          } else {
            sumWrong++;
            w++;
          }
          sumAgreement += (Math.pow(r, 2) + Math.pow(w, 2) - 2) / 2;
          if (lastCorrect == correct) {
            totalAgreement++;
          }
        }
        lastUser = user;
        lastFactid = factid;
        lastCorrect = correct;
      }
      rs.close();
      double expected = Math.pow((double) sumRight / ((double) totalSubjects * 2), 2) + Math.pow((double) sumWrong / ((double) totalSubjects * 2), 2);
      double avrg = (double) sumAgreement / (double) totalSubjects;
      kappa = (avrg - expected) / (1 - expected);
      agreementProb = (double) totalAgreement / (double) totalSubjects;
      if (Double.isNaN(agreementProb)) {
        agreementProb = 0.0;
      }
      if (Double.isNaN(kappa)) {
        kappa = 0.0;
      }
    } finally {
      DataManager.releaseConnection(con);
    }
    return new double[] { agreementProb * 100, kappa };
  }

  public double[] getTwiceEvaluatedFactsTotalAndProgress() throws SQLException {
    DBConnection con = null;
    double progress = 1.0;
    int totalDoubleFacts = 0;
    try {
      con = DataManager.getConnection("kappa");
      DBStatementInterface stmt = con.getStatement();
      //      String query = "SELECT e.factid,e.username FROM " + "(SELECT COUNT(factid) AS fid, factid FROM evaluation " + "WHERE eval='@right' OR eval='@wrong' " +
      //          "GROUP BY factid " + "HAVING COUNT(factid) = 2) cc " + "INNER JOIN evaluation e ON e.factid = cc.factid " + "ORDER BY cc.factid";
      String query = "SELECT e.factid,e.username FROM (SELECT COUNT(factid) AS fid, factid FROM evaluation WHERE (eval='@right' OR eval='@wrong') AND predicate not in (SELECT value from evaluation_settings where key = 'exclude_relation') AND technique not in (SELECT value from evaluation_settings where key = 'exclude_technique') GROUP BY factid HAVING COUNT(factid) = 2) cc  INNER JOIN evaluation e ON e.factid = cc.factid ORDER BY cc.factid";

      ResultSet rs = stmt.executeQuery(query);
      String lastUser = "";
      String lastFactid = "";
      while (rs.next()) {
        String factid = rs.getString(1);
        String user = rs.getString(2);
        if (user == null) user = "";
        if (lastFactid.equals(factid) && !lastUser.equals(user)) {
          totalDoubleFacts++;
        }
        lastUser = user;
        lastFactid = factid;
      }
      rs.close();
      //      query = "SELECT COUNT(*) FROM evaluation WHERE eval='@right' OR eval='@wrong'";
      query = "SELECT count(*) FROM evaluation WHERE (eval='@right' OR eval='@wrong') AND predicate not in (SELECT value from evaluation_settings where key = 'exclude_relation') AND technique not in (SELECT value from evaluation_settings where key = 'exclude_technique')";
      rs = stmt.executeQuery(query);
      rs.next();
      int totalFacts = rs.getInt(1);
      rs.close();
      double fractionDone = (double) totalDoubleFacts / ((double) totalFacts - totalDoubleFacts);
      progress = fractionDone / EvaluationManager.DOUBLE_EVALUATION_FRACTION;
      if (progress > 1.0) progress = 1.0;
    } finally {
      DataManager.releaseConnection(con);
    }
    return new double[] { totalDoubleFacts, progress };
  }

  public Fact getFactForSecondEvaluation(String excludedUser) throws SQLException {
    DBConnection con = null;
    Fact f = null;
    try {
      con = DataManager.getConnection("secondeval");
      DBStatementInterface stmt = con.getStatement();
      //      String query = "SELECT e." + SUBJECT + ",e." + PREDICATE + ",e." + OBJECT + ",e.factid,e.target,e.technique FROM " + "(SELECT COUNT(factid) AS fid, factid FROM evaluation " + "WHERE NOT " + SUBJECT + " LIKE '#%'"
      //          + "AND eval='@right' OR eval='@wrong' " +
      //          "GROUP BY factid " + "HAVING COUNT(factid) = 1) cc " + "INNER JOIN evaluation e ON e.factid = cc.factid " + "WHERE e.username<>'" + excludedUser + "'" + "ORDER BY RANDOM()";
      String query = "SELECT e." + SUBJECT + ",e." + PREDICATE + ",e." + OBJECT + ",e.factid,e.target,e.technique FROM " + "(SELECT COUNT(factid) AS fid, factid FROM evaluation " + "WHERE NOT " + SUBJECT + " LIKE '#%'"
          + "AND (eval='@right' OR eval='@wrong') " + "AND predicate not in (SELECT value from evaluation_settings where key = 'exclude_relation') " + "AND technique not in (SELECT value from evaluation_settings where key = 'exclude_technique') "
          + "GROUP BY factid " + "HAVING COUNT(factid) = 1) cc " + "INNER JOIN evaluation e ON e.factid = cc.factid " + "WHERE e.username<>'" + excludedUser + "'" + "ORDER BY RANDOM()";
      ResultSet rs = stmt.executeQuery(query);
      if (rs.next()) {
        f = new Fact(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
        f.setEvaluationTarget(rs.getString(5));
        f.setTechnique(rs.getString(6));
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return f;
  }

  public Set<String> getAllRelations() throws SQLException {
    return getAllRelations(true);
  }

  public Set<String> getAllRelations(boolean includeMetaRelations) throws SQLException {
    DBConnection con = null;
    Set<String> relations = new HashSet<String>();
    try {
      con = DataManager.getConnection("secondeval");
      DBStatementInterface stmt = con.getStatement();
      StringBuilder query = new StringBuilder("SELECT DISTINCT " + PREDICATE + " FROM facts_rel_tech_meta");
      String appendWord = "WHERE";
      for (String technique : excludeTechniques) {
        query.append(" " + appendWord + " technique<>").append("'" + technique + "'");
        appendWord = "AND";
      }
      if (excludeTechniques.isEmpty()) {
        appendWord = "WHERE";
      }
      for (String relation : excludeRelations) {
        query.append(" " + appendWord + " " + PREDICATE + "<>").append("'" + relation + "'");
        appendWord = "AND";
      }
      ResultSet rs = stmt.executeQuery(query.toString());
      while (rs.next()) {
        String relation = rs.getString(1);
        if (!includeMetaRelations && !getDomain(relation).equals(YAGO_FACT)) {
          relations.add(relation);
        }
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return relations;
  }

  public Set<String> getAllTechniques() {
    return new HashSet<String>(separateTechniques);
  }

  public boolean isLiteral(String yagoClass) {
    return literalClasses.contains(yagoClass);
  }

  public String getDomain(String relation) throws SQLException {
    DBConnection con = null;
    String domain = "NONE";
    try {
      con = DataManager.getConnection("domain");
      DBStatementInterface stmt = con.getStatement();
      String query = "SELECT " + OBJECT + " FROM " + FACTS_TABLE + " WHERE " + PREDICATE + "='" + DOMAIN + "' AND " + SUBJECT + "='" + relation + "'";
      ResultSet rs = stmt.executeQuery(query);
      if (rs.next()) {
        domain = rs.getString(1);
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return domain;
  }

  public String getRange(String relation) throws SQLException {
    DBConnection con = null;
    String range = "NONE";
    try {
      con = DataManager.getConnection("range");
      DBStatementInterface stmt = con.getStatement();
      String query = "SELECT " + OBJECT + " FROM " + FACTS_TABLE + " WHERE " + PREDICATE + "='" + RANGE + "' AND " + SUBJECT + "='" + relation + "'";
      ResultSet rs = stmt.executeQuery(query);
      if (rs.next()) {
        range = rs.getString(1);
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return range;
  }

  public boolean isEconomicRelation(String relation) {
    return economicRelations.contains(relation);
  }

  public void storeFactEvaluation(String factId, String evaluationTarget, String username, String value) throws SQLException {
    DBConnection con = null;
    try {
      if (!shouldStoreFact(factId, username)) {
        return;
      }
      // get all info for evaluation entry
      con = DataManager.getConnection("storing");
      DBStatementInterface stmt = con.getStatement();

      String query = "SELECT f1." + ID + " AS " + ID + ", f1." + SUBJECT + " AS " + SUBJECT + ", f1." + PREDICATE + " AS " + PREDICATE + ", f1." + OBJECT + " AS " + OBJECT + ", f3." + OBJECT + " AS technique " + "FROM " + FACTS_TABLE + " f1, "
          + FACTS_TABLE + " f2, " + FACTS_TABLE + " f3 " + "WHERE f1." + ID + "='" + factId + "' " + "AND f2." + SUBJECT + "=f1." + ID + " " + "AND f3." + SUBJECT + "=f2." + ID + " " + "AND f3." + PREDICATE + "='" + USING + "'";

      ResultSet rs = stmt.executeQuery(query);

      EvaluationEntry e = null;

      if (rs.next()) {
        Fact f = new Fact(rs.getString(SUBJECT), rs.getString(PREDICATE), rs.getString(OBJECT), rs.getString(ID));
        e = new EvaluationEntry(new Date(), f, rs.getString("technique"), username, value, evaluationTarget);
      }

      rs.close();

      if (e == null) {
        System.err.println("Could not write evaluation for fact '" + factId + "'");
        return;
      }

      DBPreparedStatementInterface pStmt = con.prepareStatement("INSERT INTO evaluation (timepoint, factid, " + SUBJECT + ", " + PREDICATE + ", " + OBJECT + ", technique, eval, username, target) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

      pStmt.setTimestamp(1, new Timestamp(e.getTime().getTime()));
      pStmt.setString(2, e.getFact().getId());
      pStmt.setString(3, e.getFact().getArg1());
      pStmt.setString(4, e.getFact().getRelation());
      pStmt.setString(5, e.getFact().getArg2());
      pStmt.setString(6, e.getTechnique());
      pStmt.setString(7, e.getEvaluationResult());
      pStmt.setString(8, e.getUser());
      pStmt.setString(9, e.getTarget());

      pStmt.execute();
    } finally {
      DataManager.releaseConnection(con);
    }
  }

  private boolean shouldStoreFact(String factId, String username) throws SQLException {
    DBConnection con = null;
    boolean shouldStore = true;
    try {
      con = DataManager.getConnection("storing");
      DBStatementInterface stmt = con.getStatement();
      String query = "SELECT factid " + "FROM evaluation " + "WHERE factid='" + factId + "' " + "AND username='" + username + "'";
      ResultSet rs = stmt.executeQuery(query);
      if (rs.next()) {
        // each fact should be evaluated only by one user, and each
        // fact not more than once
        shouldStore = false;
      }
      rs.close();
      query = "SELECT count(factid) " + "FROM evaluation " + "WHERE factid='" + factId + "'";
      rs = stmt.executeQuery(query);
      if (rs.next()) {
        int count = rs.getInt(1);
        if (count >= 2) {
          shouldStore = false;
        }
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return shouldStore;
  }

  public Iterable<Map<String, String>> getUserEvaluationNumbers() throws SQLException {
    DBConnection con = null;
    List<Map<String, String>> userEvaluations = new LinkedList<Map<String, String>>();
    try {
      con = DataManager.getConnection("storing");
      DBStatementInterface stmt = con.getStatement();
      String query = "SELECT username, count(*) AS count FROM evaluation GROUP BY username ORDER BY count DESC";
      ResultSet rs = stmt.executeQuery(query);
      while (rs.next()) {
        String username = rs.getString("username");
        String count = rs.getString("count");
        if (username.equals("")) {
          username = "ANONYMOUS";
        }
        Map<String, String> user = new HashMap<String, String>();
        user.put("User", username);
        user.put("Count", count);
        userEvaluations.add(user);
      }
      rs.close();
    } finally {
      DataManager.releaseConnection(con);
    }
    return userEvaluations;
  }

  public void normalizeYagoEntities() throws SQLException {
    DBConnection con = null;
    try {
      con = DataManager.getConnection("normalizing");
      DBStatementInterface stmt = con.getStatement();
      String query = "SELECT factid," + SUBJECT + "," + PREDICATE + "," + OBJECT + " FROM evaluation";
      ResultSet rs = stmt.executeQuery(query);
      List<String[]> entries = new LinkedList<String[]>();
      while (rs.next()) {
        String factid = rs.getString("factid");
        String arg1 = rs.getString(SUBJECT);
        String arg2 = rs.getString(OBJECT);
        entries.add(new String[] { factid, arg1, arg2 });
      }
      rs.close();
      int changeCount = 0;
      for (String[] entry : entries) {
        String factid = entry[0];
        String arg1 = entry[1];
        String arg2 = entry[2];
        String normArg1 = normalizeIfNecessary(arg1);
        String normArg2 = normalizeIfNecessary(arg2);
        if (!normArg1.equals(arg1) || !normArg2.equals(arg2)) {
          query = "UPDATE evaluation SET " + SUBJECT + "=E'" + getPostgresEscapedString(normArg1) + "', " + OBJECT + "='" + getPostgresEscapedString(normArg2) + "' WHERE factid='" + factid + "'";
          //        logger.info(query);
          changeCount++;
          logger.info("Updating '" + factid + "': '" + arg1 + "' -> '" + normArg1 + "', '" + arg2 + "' -> '" + normArg2 + "'");
          stmt.execute(query);
        }
      }
      logger.info("Changed " + changeCount + " entries");
    } finally {
      DataManager.releaseConnection(con);
    }
  }

  private String normalizeIfNecessary(String s) {
    String norm = s;

    if (!s.startsWith("\"") && !s.contains("#") && !s.startsWith("+") && !s.matches("[\\d\\./-]+")) {
      norm = FactComponent.forYagoEntity(s);
    }

    return norm;
  }

  public static String getPostgresEscapedString(String input) {
    return input.replace("'", "''").replace("\\", "\\\\");
  }
}
