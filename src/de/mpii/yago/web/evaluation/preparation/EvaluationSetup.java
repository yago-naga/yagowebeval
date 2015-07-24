package de.mpii.yago.web.evaluation.preparation;

import javatools.administrative.Announce;
import mpi.database.DBConnection;
import mpi.database.DataManager;
import mpi.database.interfaces.DBStatementInterface;
import de.mpii.yago.web.evaluation.util.YagoDatabase;


public class EvaluationSetup {

  /**
   * @param args
   * @throws Exception 
   */
  public static void main(String[] args) throws Exception {
    /*String[] sqlQueries = {
        "DROP TABLE IF EXISTS facts_tech_rel_sorted",
        "DROP TABLE IF EXISTS facts_tech_rel_meta",
        "DROP TABLE IF EXISTS facts_rel_tech_sorted",
        "DROP TABLE IF EXISTS facts_rel_tech_meta",
        "CREATE TABLE facts_tech_rel_sorted ( id SERIAL, yagoid varchar(255), technique varchar(255), relation varchar(255) )",
        "INSERT INTO facts_tech_rel_sorted(yagoid, technique, relation) (SELECT f1.id, f3.arg2, f1.relation FROM facts f1, facts f2, facts f3 WHERE f1.id=f2.arg1 AND f2.relation='wasFoundIn' AND f2.id=f3.arg1 AND f3.relation='using' ORDER BY f3.arg2, f1.relation)",
        "CREATE TABLE facts_tech_rel_meta ( technique varchar(255), relation varchar(255), first integer, last integer, count integer )",
        "INSERT INTO facts_tech_rel_meta(technique, relation, first, last, count) SELECT technique, relation, min(id), max(id), max(id)-min(id)+1 FROM facts_tech_rel_sorted GROUP BY technique, relation ORDER BY technique, relation",
        "CREATE TABLE facts_rel_tech_sorted ( id SERIAL, yagoid varchar(255), relation varchar(255), technique varchar(255) )",
        "INSERT INTO facts_rel_tech_sorted(yagoid, relation, technique) (SELECT f1.id, f1.relation, f3.arg2 FROM facts f1, facts f2, facts f3 WHERE f1.id=f2.arg1 AND f2.relation='wasFoundIn' AND f2.id=f3.arg1 AND f3.relation='using' ORDER BY f1.relation, f3.arg2)",
        "CREATE TABLE facts_rel_tech_meta ( relation varchar(255), technique varchar(255), first integer, last integer, count integer )",
        "INSERT INTO facts_rel_tech_meta(relation, technique, first, last, count) SELECT relation, technique, min(id), max(id), max(id)-min(id)+1 FROM facts_rel_tech_sorted GROUP BY relation, technique ORDER BY relation, technique",
        "CREATE INDEX ftrs_id_index ON facts_tech_rel_sorted(id)",
        "CREATE INDEX ftrm_tech_index ON facts_tech_rel_meta(technique)",
        "CREATE INDEX frts_id_index ON facts_rel_tech_sorted(id)",
        "CREATE INDEX frtm_rel_index ON facts_rel_tech_meta(relation)",
        "CREATE INDEX frtm_rel_tech_index ON facts_rel_tech_meta(relation,technique)"
    };
    
    YagoDatabase.connectToDBWithProperties("db_settings.properties");
        
    Announce.doing("Creating performance-enhancing tables for YAGO facts by executing the following statements");
    
    DBConnection con = DataManager.getConnection("setup");
    DBStatementInterface stmt = con.getStatement();
    
    for (String query : sqlQueries) {
      Announce.doing(query);
      stmt.execute(query);
      Announce.done();
    }
    
    DataManager.releaseConnection(con);
    
    Announce.done();*/
  }
}
