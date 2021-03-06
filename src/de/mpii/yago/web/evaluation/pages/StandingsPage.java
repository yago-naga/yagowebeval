package de.mpii.yago.web.evaluation.pages;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.click.Context;
import org.apache.click.control.Column;
import org.apache.click.control.Decorator;
import org.apache.click.control.Table;
import org.apache.click.dataprovider.DataProvider;
import org.apache.click.util.Bindable;

import de.mpii.yago.web.evaluation.model.EvaluationEntry;
import de.mpii.yago.web.evaluation.util.Wilson;
import de.mpii.yago.web.evaluation.util.YagoDatabase;

public class StandingsPage extends BasePage {

  @Bindable
  protected Table relationsTable = new Table();

  @Bindable
  protected Table techniquesTable = new Table();

  @Bindable
  protected Table usersTable = new Table();

  private YagoDatabase ydb;

  List<EvaluationEntry> entries;

  public StandingsPage() {
    super();

    try {
      ydb = new YagoDatabase();
      entries = ydb.getEvaluationEntriesFromDB();
    } catch (SQLException e) {
      getLogger().error("Could not initialize DB", e);
    } catch (IOException e) {
      getLogger().error("Could not initialize DB", e);
    }

    addModel("title", "YAGO3 Evaluation - Current Standings");

    relationsTable.setClass(Table.CLASS_ITS);
    relationsTable.setSortable(true);

    relationsTable.addColumn(new Column("Evaluation Target"));
    relationsTable.addColumn(new Column("Evaluations"));
    relationsTable.addColumn(new Column("Correct"));
    relationsTable.addColumn(new Column("Ratio (%)"));
    relationsTable.addColumn(new Column("Wilson Center (%)"));
    relationsTable.addColumn(new Column("Wilson Width (%)"));
    Column progress = new Column("Progress");
    progress.setDecorator(new ProgressDecorator());
    progress.setSortable(false);
    relationsTable.addColumn(progress);

    relationsTable.setDataProvider(new StandingsProvider(relationsTable, EvaluationEntry.EVAL_TARGET_RELATION));
    relationsTable.setSorted(true);

    techniquesTable.setClass(Table.CLASS_ITS);
    techniquesTable.setSortable(true);

    techniquesTable.addColumn(new Column("Evaluation Target"));
    techniquesTable.addColumn(new Column("Evaluations"));
    techniquesTable.addColumn(new Column("Correct"));
    techniquesTable.addColumn(new Column("Ratio (%)"));
    techniquesTable.addColumn(new Column("Wilson Center (%)"));
    techniquesTable.addColumn(new Column("Wilson Width (%)"));
    Column progress_tech = new Column("Progress");
    progress_tech.setDecorator(new ProgressDecorator());
    progress_tech.setSortable(false);
    techniquesTable.addColumn(progress_tech);

    techniquesTable.setDataProvider(new StandingsProvider(relationsTable, EvaluationEntry.EVAL_TARGET_TECHNIQUE));

    usersTable.setClass(Table.CLASS_ITS);
    usersTable.addColumn(new Column("User"));
    usersTable.addColumn(new Column("Count"));

    usersTable.setDataProvider(new DataProvider<Map<String, String>>() {

      private static final long serialVersionUID = -2146381601361700283L;

      @Override
      public Iterable<Map<String, String>> getData() {
        Iterable<Map<String, String>> userEvaluations = null;

        try {
          userEvaluations = ydb.getUserEvaluationNumbers();
        } catch (SQLException e) {
          getLogger().error("Could not get user standings", e);
        }

        return userEvaluations;
      }
    });
  }

  @Override
  public void onRender() {
    super.onRender();

    double[] agreementAndKappa = { 0.0, 0.0 };
    double[] totalDoubleFactsAndProgress = { 0.0, 0.0 };
    double[] averageWilson = getAverageWilson();
    int numberEval = getNumberEval();
    double correctFraction = getCorrectFraction();

    try {
      agreementAndKappa = ydb.getAgreementAndKappa();
      totalDoubleFactsAndProgress = ydb.getTwiceEvaluatedFactsTotalAndProgress();
    } catch (SQLException e) {
      getLogger().error("Could not get Kappa", e);
    }

    addModel("correctFraction", nf.format(correctFraction));
    addModel("agreement", nf.format(agreementAndKappa[0]));
    addModel("kappa", nf.format(agreementAndKappa[1]));
    addModel("totalDouble", nf.format(totalDoubleFactsAndProgress[0]));
    addModel("progress", totalDoubleFactsAndProgress[1]);
    addModel("avgWilsonCenter", nf.format(averageWilson[0]));
    addModel("avgWilsonWidth", nf.format(averageWilson[1]));
    addModel("numberEval", numberEval);
  }

  private double getCorrectFraction() {
    int totalEvaluations = getNumberEval();
    int correct = 0;

    for (EvaluationEntry e : entries) {
      if (e.isCorrect()) {
        correct++;
      }
    }

    return (double) correct * 100 / totalEvaluations;
  }

  private double[] getAverageWilson() {
    double totalCenter = 0.0;
    double totalWidth = 0.0;

    int totalNumber = 0;

    List<Map<String, String>> poolStanding = ydb.groupEntriesByEvaluationTarget(entries, "relation");

    for (Map<String, String> relationStanding : poolStanding) {
      int number = Integer.parseInt(relationStanding.get("Evaluations"));

      double center = Double.parseDouble(relationStanding.get("Wilson Center (%)"));
      double width = Double.parseDouble(relationStanding.get("Wilson Width (%)"));

      totalCenter += center * number;
      totalWidth += width * number;

      totalNumber += number;
    }

    double averageCenter = totalCenter / totalNumber;
    double averageWidth = totalWidth / totalNumber;

    return new double[] { averageCenter, averageWidth };
  }

  private int getNumberEval() {
    return entries.size();
  }

  class StandingsProvider implements DataProvider<Map<String, String>> {

    Table table;

    List<Map<String, String>> poolStanding;

    public StandingsProvider(Table table, String evalulationTarget) {
      this.table = table;
      poolStanding = ydb.groupEntriesByEvaluationTarget(entries, evalulationTarget);
    }

    @Override
    public Iterable<Map<String, String>> getData() {
      Collections.sort(poolStanding, new Comparator<Map<String, String>>() {

        List<String> numericColumns = Arrays.asList("Evaluations", "Correct", "Ratio (%)", "Wilson Center (%)", "Wilson Width (%)");

        String sortKey = table.getSortedColumn();

        boolean sortAscending = table.isSortedAscending();

        {
          // default sorting
          if (sortKey == null) {
            sortKey = "Wilson Center (%)";
            sortAscending = false;
          }
        }

        @Override
        public int compare(Map<String, String> one, Map<String, String> two) {
          // check arguments
          if (one == null || two == null) return 0;
          if (!(one instanceof HashMap<?, ?>)) return 0;
          if (!(two instanceof HashMap<?, ?>)) return 0;

          String val1 = ((HashMap<String, String>) one).get(sortKey);
          String val2 = ((HashMap<String, String>) two).get(sortKey);

          if (val1 == null || val2 == null) return 0;

          // do a numeric or string based comparison
          int compareValue = 0;
          if (numericColumns.contains(sortKey)) {
            Double ratio1 = Double.parseDouble(val1);
            Double ratio2 = Double.parseDouble(val2);
            compareValue = ratio1.compareTo(ratio2);
          } else {
            compareValue = val1.compareTo(val2);
          }

          return (sortAscending ? compareValue : -compareValue);
        }
      });
      return poolStanding;
    }
  }

  class ProgressDecorator implements Decorator {

    @Override
    public String render(Object object, Context context) {
      Map<String, String> row = (Map<String, String>) object;

      int total = Integer.parseInt(row.get("Evaluations"));
      int correct = Integer.parseInt(row.get("Correct"));

      double progress = Wilson.progress(total, correct);
      if (progress == 1.0) {
        return "<div style='width:160px'>" + "<div style='float:left;background-color:blue;width:" + (progress * 150) + "px' >&nbsp;</div>"
            + "</div>";
      }

      return "<div style='width:160px'>" + "<div style='float:left;background-color:green;width:" + (progress * 150) + "px' >&nbsp;</div>"
          + "<div style='float:left;background-color:red;width:" + ((1 - progress) * 150) + "px' >&nbsp;</div>" + "</div>";

    }

  }
}
