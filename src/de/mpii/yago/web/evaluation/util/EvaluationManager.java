package de.mpii.yago.web.evaluation.util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.mpii.yago.web.evaluation.model.EvaluationEntry;
import de.mpii.yago.web.evaluation.model.Fact;

public class EvaluationManager {

  /**
   * Sets the fraction of facts to be evaluated twice (for calculating Kappa)
   */
  public static final double DOUBLE_EVALUATION_FRACTION = 0.1;

  public static final boolean DO_KAPPA_EVALUATION = false;

  public static final String RIGHT = "@right";

  public static final String WRONG = "@wrong";

  public static final String IGNORE = "@ignore";

  private enum EvaluationTarget {
    RELATION, TECHNIQUE, KAPPA, DONE
  };

  private static EvaluationTarget currentTarget;

  // holds relation or technique  
  private static String currentEvaluationTarget;

  private static int remainingFactsForCurrentTarget = 0;

  private static final int maxRemainigFactsforCurrentTarget = 10;

  /**
   * Fills factsToEvaluate with a new fact for evaluation
   * 
   * @param excludedUser    Current user, if a new fact is fetched for evaluating annotator-agreement, this is used
   * @param factsToEvaluate This will contain a fact to evaluate if return is false
   * @return  true if the evaluation is done (no fact will be added to factsToEvaluate), false otherwise
   * @throws SQLException
   * @throws IOException
   */
  public static synchronized boolean getRandomFactForEvaluation(String excludedUser, List<Fact> factsToEvaluate) throws SQLException, IOException {
    YagoDatabase ydb = new YagoDatabase();

    // check if there is a current target - if so, try to get the next fact for the same target
    // this simplifies evaluation
    if (remainingFactsForCurrentTarget > 0) {
      Fact f = getRandomFact(excludedUser, ydb);

      if (f != null) {
        factsToEvaluate.add(f);

        // adds related facts if there are some
        factsToEvaluate.addAll(ydb.getRelatedFactsForFact(f));
        remainingFactsForCurrentTarget--;
        return false;
      }
    }

    // check both techniques and relations (as target):

    // RELATIONS
    // - get all remaining subtargets for target, sorted by progress ASC
    List<EvaluationEntry> evalEntries = ydb.getEvaluationEntriesFromDB();

    Set<String> allRelations = ydb.getAllRelations(false);
    Map<String, Double> relationProgress = getProgressForEvaluationTarget(ydb, allRelations, "relation", evalEntries);

    // - remove all targets with progress == 1.0
    // - sort remaining by wilson-progress
    List<String> relationsToDo = getRemainingSubTargets(relationProgress);

    // - for each subtarget, try to get facts - return if there is one
    for (String rel : relationsToDo) {

      currentTarget = EvaluationTarget.RELATION;
      currentEvaluationTarget = rel;
      Fact fact = ydb.randomFactsForRelation(currentEvaluationTarget, 1).poll();

      if (fact != null) {
        factsToEvaluate.add(fact);

        // adds related facts if there are some
        factsToEvaluate.addAll(ydb.getRelatedFactsForFact(fact));

        remainingFactsForCurrentTarget = maxRemainigFactsforCurrentTarget;
        return false;
      }
    }

    // TECHNIQUES
    // - get all remaining subtargets for target, sorted by progress ASC

    Set<String> allTechniques = ydb.getAllTechniques();
    Map<String, Double> techniquesProgress = getProgressForEvaluationTarget(ydb, allTechniques, "technique", evalEntries);

    // - remove all targets with progress == 1.0
    // - sort remaining by wilson-progress
    List<String> techniquesToDo = getRemainingSubTargets(techniquesProgress);

    // - for each subtarget, try to get facts - return if there is one
    for (String tech : techniquesToDo) {

      currentTarget = EvaluationTarget.TECHNIQUE;
      currentEvaluationTarget = tech;
      Fact fact = ydb.randomFactsForTechnique(currentEvaluationTarget, 1).poll();

      if (fact != null) {
        factsToEvaluate.add(fact);

        // adds related facts if there are some
        factsToEvaluate.addAll(ydb.getRelatedFactsForFact(fact));

        remainingFactsForCurrentTarget = maxRemainigFactsforCurrentTarget;
        return false;
      }
    }

    // get next fact for kappa, if necessary
    double kappaProgress = 2.0;
    if (DO_KAPPA_EVALUATION) {
      kappaProgress = ydb.getTwiceEvaluatedFactsTotalAndProgress()[1];
    }

    if (kappaProgress < 1.0) {
      Fact fact = ydb.getFactForSecondEvaluation(excludedUser);
      factsToEvaluate.add(fact);
      return false;
    }

    // if we reach here, the whole evaluation is done!
    return true;
  }

  private static Fact getRandomFact(String excludedUser, YagoDatabase ydb) throws SQLException {
    Fact fact = null;

    switch (currentTarget) {
      case RELATION:
        fact = ydb.randomFactsForRelation(currentEvaluationTarget, 1).poll();

        if (fact != null) {
          fact.setEvaluationTarget("relation");
        }
        break;
      case TECHNIQUE:
        fact = ydb.randomFactsForTechnique(currentEvaluationTarget, 1).poll();

        if (fact != null) {
          fact.setEvaluationTarget("technique");
        }
        break;

      default:
        break;
    }

    return fact;
  }

  private static List<String> getRemainingSubTargets(Map<String, Double> subTargetProgress) {
    List<Entry<String, Double>> entries = new ArrayList<Entry<String, Double>>(subTargetProgress.entrySet());

    Collections.sort(entries, new Comparator<Entry<String, Double>>() {

      @Override
      public int compare(Entry<String, Double> arg0, Entry<String, Double> arg1) {
        return arg0.getValue().compareTo(arg1.getValue());
      }
    });

    List<String> remainingSubTargets = new LinkedList<String>();

    for (Entry<String, Double> e : entries) {
      if (e.getValue() < 1.0) {
        remainingSubTargets.add(e.getKey());
      }
    }

    return remainingSubTargets;
  }

  private static Map<String, Double> getProgressForEvaluationTarget(YagoDatabase ydb, Set<String> allTargets, String evalTarget, List<EvaluationEntry> evalEntries) {
    Map<String, Double> progress = new HashMap<String, Double>();

    for (String target : allTargets) {
      progress.put(target, 0.0);
    }

    // overwrite progress where we know it
    List<Map<String, String>> entriesByTarget = ydb.groupEntriesByEvaluationTarget(evalEntries, evalTarget, "Progress", true);

    for (Map<String, String> evalTargetData : entriesByTarget) {
      String target = evalTargetData.get("Evaluation Target");

      if (allTargets.contains(target)) {
        int total = Integer.parseInt(evalTargetData.get("Evaluations"));
        int correct = Integer.parseInt(evalTargetData.get("Correct"));
        double targetPrgoress = Wilson.progress(total, correct);
//        DO NOT USE following line as it has a rounding error.
//        double targetPrgoress = Double.parseDouble(evalTargetData.get("Progress"));
        progress.put(target, targetPrgoress);
      }
    }

    return progress;
  }
}
