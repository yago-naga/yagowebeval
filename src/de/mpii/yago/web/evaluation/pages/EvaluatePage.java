package de.mpii.yago.web.evaluation.pages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.click.control.RadioGroup;
import org.apache.click.control.Submit;
import org.apache.click.util.Bindable;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mpii.yago.web.evaluation.model.Fact;
import de.mpii.yago.web.evaluation.pages.WikiHelper.WikipediaEntity;
import de.mpii.yago.web.evaluation.util.EvaluationManager;
import de.mpii.yago.web.evaluation.util.FactComponent;
import de.mpii.yago.web.evaluation.util.YagoDatabase;
import javatools.parsers.Char;

public class EvaluatePage extends BasePage {

	@Bindable String username = "";

	Map<String, RadioGroup> radioGroups = new HashMap<String, RadioGroup>();

	private YagoDatabase ydb;
	private Logger logger = LoggerFactory.getLogger(EvaluatePage.class);
	
	WikiHelper helper = new WikiHelper();
	
	public EvaluatePage() {
		super();
		try {
			ydb = new YagoDatabase();
		} catch (SQLException e) {
			getLogger().error("Could not initialize DB", e);
		}

		addModel("title", "YAGO3 Evaluation - Judge The Fact");

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
					String arg1desc = descriptionForEntity(f.getArg1(), ydb.getDomain(f.getRelation()), f.getRelation(), f.getTechnique());

					if (arg1desc != null) {
						entityDescriptions.put(Fact.toHumanReadableEntity(f.getArg1()), arg1desc);
					}

					String arg2desc = descriptionForEntity(f.getArg2(), ydb.getRange(f.getRelation()), f.getRelation(), f.getTechnique());

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
	private String descriptionForEntity(String entity, String yagoClass, String relation, String extractionTechnique) throws SQLException, IOException {
		// Ignore literals/facts
		if (ydb.isLiteral(yagoClass) || yagoClass.equals("yagoFact")) return null;
		
		Pattern idEntity = Pattern.compile("<id_[^_]*_[^_]*_[^_]*>");
		if(idEntity.matcher(entity).matches()) {
			return null;
		}
		
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
		// TODO: fix this in YAGO ?
		unnormEntity.replaceAll("(u[0-9A-Fa-f]{4})", "\\$1");
		unnormEntity = StringEscapeUtils.unescapeJava(unnormEntity);
		
		logger.debug("entity: " + entity + " unnorm: " + unnormEntity);

		String url = searchWikipediaEntity(unnormEntity, relation, extractionTechnique);
		StringBuilder descriptionBuilder = new StringBuilder();
		descriptionBuilder.append("<IFRAME width='99%' height='1500px;' src='");
		descriptionBuilder.append(url);
		descriptionBuilder.append("'>Error: Your browser is from the stone age. Try again with a new browser. </IFRAME>");

		return descriptionBuilder.toString();
	}
	
	/**
	 * Search Wikipedia entity, corresponding to the yago entity (if possible the right revision)
	 * @return url
	 */
	private String searchWikipediaEntity(String entity, String relation, String extractionTechnique) {
		WikipediaEntity wikientity = helper.new WikipediaEntity();
		wikientity.languageCode = "en";
		wikientity.title = entity;
		
		// match yago entities of the form <language code>/<entity> (for example de/Bundeskanzler)
		int idx = entity.indexOf("/");
		if(idx == 2) {
			String languageCode = entity.substring(0, idx);
			String title = entity.substring(idx+1);
			
			if(helper.checkIfArticleExists(languageCode, title)) {
				wikientity.languageCode = languageCode;
				wikientity.title = title;
			}
		}
		
		// deal with economy relations
		String datestr = helper.languageDatestrMap.get(wikientity.languageCode);
		WikipediaEntity economywikientity = checkEconomyRelation(wikientity, relation, datestr);
		if(economywikientity != null) {
			wikientity = economywikientity;
		}
		
		// try with language used by infobox extractor
		if(extractionTechnique != null &&
				extractionTechnique.startsWith("&quot;InfoboxExtractor")) {
			Pattern infoboxLangPattern = Pattern.compile("infobox/(..)/");
			Matcher m = infoboxLangPattern.matcher(extractionTechnique);
			if(m.find()) {
				String infoboxLanguage = m.group(1);
				System.out.println("infobox extractor language " + m.group(1));
				if(!infoboxLanguage.equals(wikientity.languageCode)) {
					// search translation
					String foreignTitle = helper.getWikipediaLanguageTitle(wikientity.languageCode, infoboxLanguage, wikientity.title);
					System.out.println("foreign title " + foreignTitle);
					if(foreignTitle != null) {
						wikientity.languageCode = infoboxLanguage;
						wikientity.title = foreignTitle;
					}
				}
			}
		}

		// try to fetch version at the time of the extraction
		if(datestr != null && wikientity.revisionId == null) {
			wikientity.revisionId = helper.getWikipediaRevisionId(wikientity.languageCode, wikientity.title, datestr);
		}
		return wikientity.getUrl();
	}
	
	/**
	 * Tries to retrieve title of economic Wikipedia page if exists
	 * @param entity
	 * @param relation
	 * @param datestr
	 * @return
	 */
	private WikipediaEntity checkEconomyRelation(WikipediaEntity entity, String relation, String datestr) {
		if(!ydb.isEconomicRelation(relation)) return null;
		
		// assuming that every economy page exists in the English wikipedia
		if(!entity.languageCode.equals("en")) {
			entity.languageCode = "en";
			entity.title = helper.getWikipediaLanguageTitle(entity.languageCode, "en", entity.title);
		}
		String possibleTitles[] = {
			"Economy_of_" + entity.title,
			"Economy_of_the_" + entity.title,
			entity.title
		};
		WikipediaEntity economyEntity = helper.new WikipediaEntity();
		if(datestr != null) {
			for(String possibleTitle : possibleTitles) {
				String revid = helper.getWikipediaRevisionId("en", possibleTitle, datestr);
				if(revid != null) {
					economyEntity.revisionId = revid;
					break;
				}
			}
		}
		else {
			for(String possibleTitle : possibleTitles) {
				if(helper.checkIfArticleExists("en", possibleTitle)) {
					economyEntity.title = possibleTitle;
				}
			}
		}
		boolean valid = economyEntity.revisionId != null || economyEntity.title != null;
		return valid ? economyEntity : null;
	}
	
	public static void main(String[] args) {
		/*WikiHelper helper = new WikiHelper();
		System.out.println(helper.getWikipediaRevisionId("en", "Economy_of_Falkland_Islands", "20150101000000"));
		
		System.out.println(helper.getWikipediaLanguageTitle("en", "fr", "Tony Leung Chiu-Wai"));
		System.out.println(helper.getWikipediaLanguageTitle("en", "de", "Economy_of_the_United_States"));
		System.out.println(helper.checkIfArticleExists("en", "Spain"));
		System.out.println(helper.checkIfArticleExists("en", "Spainx"));*/
		
		String test = "\\u002E";
		System.out.println(test);
		System.out.println(StringEscapeUtils.unescapeJava(test));
	}
}

class WikiHelper {
	private Logger logger = LoggerFactory.getLogger(WikiHelper.class);
	
	public class WikipediaEntity {
		String languageCode = "en";
		String title = null;
		String revisionId = null;
		
		public String getUrl() {
			if(revisionId != null) {
				return "http://" + languageCode + ".wikipedia.org/wiki/index.php?oldid=" + revisionId;
			}
			return "http://" + languageCode + ".wikipedia.org/wiki/" + Char.encodePercentage(title);
		}
	}
	
	// maps language to date version code
	public Map<String, String> languageDatestrMap = new HashMap<String, String>();
	
	CloseableHttpClient httpclient;
	
	public WikiHelper() {
		wikiRevisionIdPattern = Pattern.compile("\"revid\":([^,]*),");
		languageDatestrMap.put("en", "20140627000000");
		languageDatestrMap.put("de", "20130423000000");
		languageDatestrMap.put("fr", "20140315000000");
		languageDatestrMap.put("nl", "20140525000000");
		languageDatestrMap.put("it", "20140317000000");
		languageDatestrMap.put("es", "20140320000000");
		languageDatestrMap.put("ro", "20140314000000");
		languageDatestrMap.put("pl", "20140312000000");
		languageDatestrMap.put("ar", "20140323000000");
		languageDatestrMap.put("fa", "20140319000000");
		
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(200);
		cm.setDefaultMaxPerRoute(20);
		
		httpclient = HttpClients.custom()
				.setConnectionManager(cm)
				.build();
		
	}

	public String doHttpGet(String url) {
		HttpGet httpget = new HttpGet(url);
		ResponseHandler<String> handler = new ResponseHandler<String>() {
			@Override
			public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
				StatusLine statusLine = response.getStatusLine();
				HttpEntity entity = response.getEntity();
				if(statusLine.getStatusCode() >= 300) {
					throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
				}
				if(entity == null) {
					throw new ClientProtocolException("Response contains no content");
				}
				BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
				
				return reader.readLine();
			}
		};
		
		String responseStr = null;
		try {
			responseStr = httpclient.execute(httpget, handler);
		} catch (HttpResponseException e) {
			logger.warn("url " + url);
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return responseStr;
	}

	Pattern wikiRevisionIdPattern;
	/**
	 * Return revision id for article
	 */
	public String getWikipediaRevisionId(String languageCode, String entity, String datestr) {
		StringBuilder urlbuilder = new StringBuilder();
		urlbuilder.append("http://");
		urlbuilder.append(languageCode);
		urlbuilder.append(".wikipedia.org/w/api.php?");
		urlbuilder.append("action=query&prop=revisions&rvlimit=1&redirects&format=json&continue=");
		urlbuilder.append("&titles=");
		urlbuilder.append(Char.encodePercentage(entity));
		urlbuilder.append("&rvstart=");
		urlbuilder.append(datestr);
		
		String responseStr = doHttpGet(urlbuilder.toString());
		if(responseStr == null) {
			return null;
		}

		Matcher m = wikiRevisionIdPattern.matcher(responseStr);
		if(m.find()) {
			return m.group(1);
		}

		return null;
	}
	
	public String getWikipediaLanguageTitle(String srcLanguageCode, String dstLanguageCode, String entity) {
		StringBuilder urlbuilder = new StringBuilder();
		urlbuilder.append("http://");
		urlbuilder.append(srcLanguageCode);
		urlbuilder.append(".wikipedia.org/w/api.php?");
		urlbuilder.append("action=query&prop=langlinks&redirects&format=json&continue=");
		urlbuilder.append("&titles=");
		urlbuilder.append(Char.encodePercentage(entity));
		urlbuilder.append("&lllang=");
		urlbuilder.append(dstLanguageCode);
		
		System.out.println(urlbuilder.toString());
		String responseStr = doHttpGet(urlbuilder.toString());
		if(responseStr == null) {
			return null;
		}
		
		Pattern languagePattern = Pattern.compile("\"lang\":\"" + dstLanguageCode + "\",\"\\*\":\"([^\"]*)\"");
		Matcher m = languagePattern.matcher(responseStr);
		if(m.find()) {
			return StringEscapeUtils.unescapeJava(m.group(1));
		}
		
		return null;
	}
	
//	Pattern wikiArticleMissingPattern = Pattern.compile("pages:\":\\{\"-1\"");
	Pattern wikiArticleMissingPattern = Pattern.compile("pages\"\\:\\{\"-1\"");
	public boolean checkIfArticleExists(String languageCode, String entity) {
		StringBuilder urlbuilder = new StringBuilder();
		urlbuilder.append("http://");
		urlbuilder.append(languageCode);
		urlbuilder.append(".wikipedia.org/w/api.php?");
		urlbuilder.append("action=query&format=json&continue=");
		urlbuilder.append("&titles=");
		urlbuilder.append(Char.encodePercentage(entity));
		
		String responseStr = doHttpGet(urlbuilder.toString());
		if(responseStr == null) {
			return false;
		}
		
		Matcher m = wikiArticleMissingPattern.matcher(responseStr);
		return !m.find(0);
	}
}
