This is the web application used for evaluating YAGO2 facts.

SETUP
-----

1. Create a new Postgres db and insert the YAGO2s facts to evaluate into it (using the standard SQL converter)

2. Adjust the settings in the 'generate_evaluation_tables.sh' in the 'scripts' directory and run it to generate additional tables from the YAGO2s facts table (needed for performance)

3. Create a new table 'evaluation_settings' with the commands at the end of this file. Use this table to exclude relations or evaluate them separately

4. Adjust the 'db_settings.properties' in the 'resources' folder to point to the YAGO2s db.

5. Package the whole project as a war and deploy on a Tomcat server


### EVALUATION_SETTINGS TABLE ###
CREATE TABLE evaluation_settings (
    key text,
    value text
);

COPY evaluation_settings (key, value) FROM stdin;
exclude_relation	<extractionSource>
exclude_relation	<extractionTechnique>
exclude_relation	<hasValue>
exclude_relation	<hasGloss>
exclude_relation	<hasWikipediaUrl>
exclude_relation	rdfs:domain
exclude_relation	rdfs:range
exclude_relation	rdfs:subPropertyOf
exclude_technique	"CategoryExtractor from title"
exclude_technique	"WikipediaLabelExtractor from title" 
economic_relation	<exports>
economic_relation	<hasExportValue>
economic_relation	<imports>
economic_relation	<hasImportValue>
economic_relation	<hasInflation>
economic_relation	<hasLabor>
economic_relation	<hasPoverty>
economic_relation	<hasUnemployment>
economic_relation	<dealsWith>
economic_relation	<hasEconomicGrowth>
economic_relation	<hasExpenses>
economic_relation	<hasImport>
economic_relation	<hasExport>
exclude_relation	<hasGeonamesEntityId>
exclude_relation	<hasGeonamesClassId>
\.


CREATE INDEX key_idx ON evaluation_settings USING btree (key);