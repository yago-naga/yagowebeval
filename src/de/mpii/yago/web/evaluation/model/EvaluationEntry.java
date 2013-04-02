package de.mpii.yago.web.evaluation.model;

import java.util.Date;

import de.mpii.yago.web.evaluation.util.EvaluationManager;


public class EvaluationEntry {
  
  public static final String EVAL_TARGET_RELATION = "relation";
  public static final String EVAL_TARGET_TECHNIQUE = "technique";
  
  private Date time;
  private Fact fact;
  private String technique;
  private String user;
  private String evaluationResult;
  private String target;

  public EvaluationEntry(Date time, Fact fact, String technique, String user, String evaluationResult, String target) {
    this.time = time;
    this.fact = fact;
    this.technique = technique;
    this.user = user;
    this.evaluationResult = evaluationResult;
    this.target = target;
  }

  public Date getTime() {
    return time;
  }
  
  public void setTime(Date time) {
    this.time = time;
  }

  
  public Fact getFact() {
    return fact;
  }

  
  public void setFact(Fact fact) {
    this.fact = fact;
  }

  
  public String getTechnique() {
    return technique;
  }

  
  public void setTechnique(String technique) {
    this.technique = technique;
  }

  
  public String getTarget() {
    return target;
  }

  
  public void setTarget(String target) {
    this.target = target;
  }

  
  public String getUser() {
    return user;
  }

  
  public void setUser(String user) {
    this.user = user;
  }
  
  
  public String getEvaluationResult() {
    return evaluationResult;
  }

  
  public void setEvaluationResult(String evaluationResult) {
    this.evaluationResult = evaluationResult;
  }

  public String getValueForTarget(String target) {
    if (target.equals(EVAL_TARGET_RELATION)) {
      return getFact().getRelation();
    } else if (target.equals(EVAL_TARGET_TECHNIQUE)) {
      return getTechnique();
    } else {
      return null;
    }
  }

  public boolean isCorrect() {
    return (evaluationResult.equals(EvaluationManager.RIGHT));
  }
  
  public String toString() {
    return user + " " + fact.id + "/" + fact.predicate + ": " + evaluationResult;
  }
}
