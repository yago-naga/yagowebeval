package de.mpii.yago.web.evaluation.model;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javatools.parsers.Char;
import de.mpii.yago.web.evaluation.util.FactComponent;
import de.mpii.yago.web.evaluation.util.YagoDatabase;

public class Fact {
  String arg1;
  String predicate;
  String arg2;
  String id;
  String technique;
  String evaluationTarget;
  
  private static Set<String> keep = 
      new HashSet<String>(Arrays.asList(
          new String[] {"<m^2>", "</km^2>", "<s>", "<m>", "<dollar>", "<euro>", "<%>", "<g>", "<degrees>"}));
  
  public String getEvaluationTarget() {
    return evaluationTarget;
  }

  
  public void setEvaluationTarget(String evaluationTarget) {
    this.evaluationTarget = evaluationTarget;
  }

  public Fact(String arg1, String predicate, String arg2, String id) {
    super();
    this.arg1 = arg1;
    this.predicate = predicate;
    this.arg2 = arg2;
    this.id = id;
  }

  public String getHumanReadable() throws SQLException {
    String sentence = YagoDatabase.getArg2(predicate, "<hasGloss>");
    if (sentence == null) {
      sentence = toHumanReadableEntity(arg1) + " " + FactComponent.stripBrackets(predicate) + " " + toHumanReadableEntity(arg2);
    } else {
      sentence = FactComponent.stripQuotes(sentence).replace("$1", toHumanReadableEntity(arg1)).replace("$2", toHumanReadableEntity(arg2));
    }
    
    return sentence;
  }
  
  public static String stripAngleBrackets(String s) {
    if (s.length() <= 2) {
      return s;
    }
    
    return s.substring(1, s.length()-1);
  }
  
  public static String toHumanReadableEntity(String s) {
    String norm = s;
    
    if (s.startsWith("<wordnet_")) {
      s = s.replace("wordnet_", "");
      s = s.replaceAll("_\\d+", "");
    } else if (s.startsWith("<wikicategory")) {
      s = s.replace("wikicategory_", "");
    }
    
    if (s.contains("@")) {
      s = FactComponent.getString(s);
    }
    
    if (s.contains("^^")) {
      String[] split = s.split("\\^\\^");
      if (keep.contains(split[1])) {
        norm = cleanRDF(split[0])+" "+cleanRDF(split[1]);
      } else {
        norm = cleanRDF(split[0]);
      }
    } else {
      norm = cleanRDF(s);
    }
    
    return ("<span style=\"font-weight:bold\">" + Char.toHTML(norm) + "</span>");
  }
  
  public static String cleanRDF(String s) {
    return FactComponent.stripBrackets(FactComponent.stripQuotes(Char.decodeBackslash(s.replace('_', ' '))));
  }
  
  public String getArg1() {
    return arg1;
  }
  
  public void setArg1(String arg1) {
    this.arg1 = arg1;
  }
  
  public String getRelation() {
    return predicate;
  }
  
  public void setRelation(String relation) {
    this.predicate = relation;
  }
  
  public String getArg2() {
    return arg2;
  }
  
  public String getHumanReadableRelation() {
    return cleanRDF(getRelation());
  }
  
  public String getHumanReadableArg2() {
    return toHumanReadableEntity(getArg2());
  }
  
  public void setArg2(String arg2) {
    this.arg2 = arg2;
  }
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }

  public String getTechnique() {
    return technique;
  }

  public void setTechnique(String technique) {
    this.technique = technique;
  }
}