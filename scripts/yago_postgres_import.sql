-- this file is adopted from converters2s/scripts/postgres.sql
-- it imports *.tsv files which were necessary to evaluate YAGO3
-- it might need to be changed for different versions of YAGO

/*     YAGO2s to Postgres database
*
* This script loads YAGO2s into a Postgres database.
* 
* The database has to be in UTF-8. To create such a database, say
*   createdb yago2 --encoding utf8 --lc-ctype en_US.utf8  --lc-collate en_US.utf8 --template template0
* 
* After that, run this script as follows:
*  - download YAGO in TSV format
*  - in a shell or dos box, cd into the directory of the YAGO TSV files
*  - run 
*        psql -a -d <database> -h <hostname> -U <user> -f <thisScript>
*/

-- Creating table
DROP TABLE IF EXISTS yagoFacts;
CREATE TABLE yagoFacts (id varchar, subject varchar, predicate varchar, object varchar, value float);

-- Loading files
-- (don't worry if some files are not found)
SET client_encoding to 'utf-8';
\copy yagoFacts FROM 'yagoDateFacts.tsv' NULL as ''
\copy yagoFacts FROM 'yagoFacts.tsv' NULL as ''
\copy yagoFacts FROM 'yagoLabels.tsv' NULL as ''
\copy yagoFacts FROM 'yagoLiteralFacts.tsv' NULL as ''
\copy yagoFacts FROM 'yagoMetaFacts.tsv' NULL as ''
\copy yagoFacts FROM 'yagoPreferredMeanings.tsv' NULL as ''
\copy yagoFacts FROM 'yagoSchema.tsv' NULL as ''
\copy yagoFacts FROM 'yagoSources.tsv' NULL as ''
\copy yagoFacts FROM 'yagoStatistics.tsv' NULL as ''
\copy yagoFacts FROM 'yagoTaxonomy.tsv' NULL as ''
\copy yagoFacts FROM 'yagoTypes.tsv' NULL as ''
\copy yagoFacts FROM 'yagoTypesSources.tsv' NULL as ''

-- Remove facts on which we cannot build the index
DELETE FROM yagofacts WHERE length(object)>1000;

-- Creating indexes
--CREATE INDEX yagoIndexSubject ON yagoFacts(subject);
--CREATE INDEX yagoIndexObject ON yagoFacts(object);
--CREATE INDEX yagoIndexValue ON yagoFacts(value);
CREATE INDEX yagoIndexPredicate ON yagoFacts(predicate);
CREATE INDEX yagoIndexId ON yagoFacts(id);
CREATE INDEX yagoIndexSubjectPredicate ON yagoFacts(subject,predicate);
CREATE INDEX yagoIndexObjectPredicate ON yagoFacts(object,predicate);
--CREATE INDEX yagoIndexValuePredicate ON yagoFacts(value,predicate);

-- Creating index for case-insensitive search
-- (you may abort the script if you don't need this index) 
--CREATE INDEX yagoIndexLowerObject ON yagofacts (lower(object));
-- done*/
