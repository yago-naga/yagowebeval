

DROP TABLE IF EXISTS facts_tech_rel_sorted;
DROP TABLE IF EXISTS facts_tech_rel_meta;
DROP TABLE IF EXISTS facts_rel_tech_sorted;
DROP TABLE IF EXISTS facts_rel_tech_meta;
DROP TABLE IF EXISTS evaluation;


CREATE MATERIALIZED VIEW yago_extraction_source
AS 
	SELECT id, subject FROM yagofacts f
	WHERE predicate='<extractionSource>'
WITH DATA;
CREATE INDEX yago_extraction_source_id_index ON yago_extraction_source(id);
CREATE INDEX yago_extraction_source_subject_index ON yago_extraction_source(subject);


CREATE MATERIALIZED VIEW yago_extraction_technique
AS
	SELECT subject, object FROM yagofacts f
	WHERE predicate='<extractionTechnique>'
WITH DATA;
CREATE INDEX yago_extraction_technique_subject_index ON yago_extraction_technique(subject);

CREATE TABLE facts_tech_rel (id SERIAL, yagoid TEXT, technique TEXT, predicate TEXT );
INSERT INTO facts_tech_rel (yagoid, technique, predicate) (
	SELECT f1.id, f3.object, f1.predicate 
	FROM yagofacts f1, yago_extraction_source f2, yago_extraction_technique f3 
	WHERE f1.id=f2.subject 
	AND f2.id=f3.subject);

CREATE TABLE facts_tech_rel_sorted ( id SERIAL, yagoid TEXT, technique TEXT, predicate TEXT );
INSERT INTO facts_tech_rel_sorted(yagoid, technique, predicate) (
	SELECT f1.id, f3.object, f1.predicate 
	FROM yagofacts f1, yago_extraction_source f2, yago_extraction_technique f3 
	WHERE f1.id=f2.subject 
	AND f2.id=f3.subject 
	ORDER BY f3.object, f1.predicate);

CREATE TABLE facts_tech_rel_meta ( technique TEXT, predicate TEXT, first integer, last integer, count integer );
INSERT INTO facts_tech_rel_meta(technique, predicate, first, last, count) 
	SELECT technique, predicate, min(id), max(id), max(id)-min(id)+1 
	FROM facts_tech_rel_sorted
	GROUP BY technique, predicate 
	ORDER BY technique, predicate;

DROP TABLE facts_tech_rel;
CREATE TABLE facts_rel_tech_sorted ( id SERIAL, yagoid TEXT, predicate TEXT, technique TEXT );
INSERT INTO facts_rel_tech_sorted(yagoid, predicate, technique) (
	SELECT f1.id, f1.predicate, f3.object 
	FROM yagofacts f1, yago_extraction_source f2, yago_extraction_technique f3 
	WHERE f1.id=f2.subject 
	AND f2.id=f3.subject
	ORDER BY f1.predicate, f3.object);

CREATE TABLE facts_rel_tech_meta ( predicate TEXT, technique TEXT, first integer, last integer, count integer );
INSERT INTO facts_rel_tech_meta(predicate, technique, first, last, count) 
	SELECT predicate, technique, min(id), max(id), max(id)-min(id)+1 
	FROM facts_rel_tech_sorted
	GROUP BY predicate, technique 
	ORDER BY predicate, technique;

CREATE INDEX ftrs_id_index ON facts_tech_rel_sorted(id);
CREATE INDEX ftrm_tech_index ON facts_tech_rel_meta(technique);
CREATE INDEX frts_id_index ON facts_rel_tech_sorted(id);
CREATE INDEX frtm_rel_index ON facts_rel_tech_meta(predicate);
CREATE INDEX frtm_rel_tech_index ON facts_rel_tech_meta(predicate,technique);
CREATE TABLE evaluation (timepoint timestamp without time zone, factid character varying(255), subject text, predicate text, object text, technique text, eval text, username text, target text);
CREATE INDEX factid_index ON evaluation USING btree(factid);
CREATE INDEX rel_index ON evaluation USING btree(predicate);
CREATE INDEX tech_index ON evaluation USING btree(technique);
