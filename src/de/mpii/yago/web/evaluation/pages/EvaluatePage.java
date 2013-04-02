package de.mpii.yago.web.evaluation.pages;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javatools.parsers.Char;

import org.apache.click.control.RadioGroup;
import org.apache.click.control.Submit;
import org.apache.click.util.Bindable;

import de.mpii.yago.web.evaluation.model.Fact;
import de.mpii.yago.web.evaluation.util.EvaluationManager;
import de.mpii.yago.web.evaluation.util.FactComponent;
import de.mpii.yago.web.evaluation.util.YagoDatabase;

public class EvaluatePage extends BasePage {
     
  @Bindable String username = "";
  
  Map<String, RadioGroup> radioGroups = new HashMap<String, RadioGroup>();
  
  private YagoDatabase ydb;
  
  public EvaluatePage() {
    super();
    try {
      ydb = new YagoDatabase();
    } catch (SQLException e) {
      getLogger().error("Could not initialize DB", e);
    }
    
    addModel("title", "YAGO2 Evaluation - Judge The Fact");
    
    addControl(new Submit("submit", this, "onFactEvaluation"));
  }
  
  @Override
  public void onRender() {    
    try {      
      List<Fact> factsToEvaluate = new LinkedList<Fact>();
      
      getLogger().info("Getting new fact to evaluate for " + username);
    
      boolean done = EvaluationManager.getRandomFactForEvaluation(username, factsToEvaluate);

      if (!done) {
        Fact fact = factsToEvaluate.get(0);        
        
        // if there are more than one facts in the list, they are related facts
        
        List<Fact> relatedFacts = new LinkedList<Fact>();
        
        for (int i=1;i<factsToEvaluate.size();i++) {
          relatedFacts.add(factsToEvaluate.get(i));
        }
        
        addModel("fact", fact);
        addModel("relatedFacts", relatedFacts);
        addModel("done", false);
        
        Map<String, String> entityDescriptions = new LinkedHashMap<String, String>();

        List<Fact> allFacts = new LinkedList<Fact>();
        allFacts.add(fact);
        allFacts.addAll(relatedFacts);

        for (Fact f : allFacts) {
          String arg1desc = descriptionForEntity(f.getArg1(), ydb.getDomain(f.getRelation()), f.getRelation());
          
          if (arg1desc != null) {
            entityDescriptions.put(Fact.toHumanReadableEntity(f.getArg1()), arg1desc);
          }

          String arg2desc = descriptionForEntity(f.getArg2(), ydb.getRange(f.getRelation()), f.getRelation());
          
          if (arg2desc != null) {
            entityDescriptions.put(Fact.toHumanReadableEntity(f.getArg2()), arg2desc);
          }
        }

        addModel("entityDescriptions", entityDescriptions);
      } else {
        addModel("done", true);
        return;
      }
    } catch (SQLException e) {
      getLogger().error("Could not get facts to evaluate", e);
    } catch (IOException e) {
      getLogger().error("Could not get facts to evaluate", e);
    }
  }
  
  public boolean onFactEvaluation() {    
    Map<String, String[]> params = getContext().getRequest().getParameterMap();
    
    String evaluationTarget = params.get("evaluation_target")[0];
    
    for (String param : params.keySet()) {
      if (!param.startsWith("fact_")) continue;
      
      String factId = param.replace("fact_", "");
      String value = params.get(param)[0];
      try {
        storeEvaluationForFact(factId, value, evaluationTarget);
      } catch (SQLException e) {
        getLogger().error("Could not store evaluation for Fact " + param, e);
      }
    }
    
    return true;
  }
  
  private void storeEvaluationForFact(String factId, String value, String evaluationTarget) throws SQLException {
    ydb.storeFactEvaluation(factId, evaluationTarget, username, value);
  }

  /**
   * Returns a description of an entity. The description is either a wordnet
   * gloss or a snippet from the Wikipedia page
   * @throws IOException 
   * @throws SQLException 
   * @throws Exception 
   */
  private String descriptionForEntity(String entity, String yagoClass, String relation) throws SQLException, IOException {
    // Ignore literals/facts
    if (ydb.isLiteral(yagoClass) || yagoClass.equals("yagoFact")) return null;
    
    // Stuff with glosses
    String gloss = YagoDatabase.getArg2(entity, "<hasGloss>");

    if (gloss != null) {
      return ("'" + Fact.toHumanReadableEntity(entity) + "' is meant in the sense of " + FactComponent.asJavaString(gloss));
    }
    
    // Wordnet entries without gloss (?)
    if (entity.contains("wordnet_")) return "No description available, sorry!";
    // Wikipedia categories
    if (entity.contains("wikicategory_")) return "No description available, sorry!";
    // Wikipedia countries and economic relation: Load Wikipedia page on the
    // economy
    
    // it's a real entity
    String unnormEntity = FactComponent.stripBrackets(entity.replace('_', ' '));
    
    if (ydb.isEconomicRelation(relation)) return ("<IFRAME width='80%' height='1000px' src='http://en.wikipedia.org/wiki/Economy_of_" + Char.encodePercentage(unnormEntity) + "' >NO IFRAME SUPPORT</IFRAME>");
    // Wikipedia entities: Load Wikipedia page
    return ("<IFRAME width='80%' height='1000px' src=\"http://en.wikipedia.org/wiki/" + Char.encodePercentage(unnormEntity) + "\" >Error: Your browser is from the stone age. Try again with a new browser. </IFRAME>");
  }
}
