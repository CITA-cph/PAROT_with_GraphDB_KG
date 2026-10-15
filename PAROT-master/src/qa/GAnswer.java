package qa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import log.QueryLogger;
import nlp.ds.Sentence;
import nlp.ds.Sentence.SentenceType;
import qa.parsing.QuestionParsing;
import qa.parsing.BuildQueryGraph;
import rdf.Sparql;
import utils.FileUtil;
import addition.AddtionalFix;
import qa.Globals;

public class GAnswer {

	public static final int MAX_SPQ_NUM = 3;

	public static void init() {
		System.out.println("gAnswer2 init ...");
		Globals.init();
		System.out.println("gAnswer2 init ... ok!");
	}

	public QueryLogger getSparqlList(String input)
	{
		QueryLogger qlog = null;
		try
		{
			if (input.length() <= 5)
				return null;

			System.out.println("[Input:] "+input);

			long t0 = System.currentTimeMillis(), t, NRtime;
			Query query = new Query(input);
			qlog = new QueryLogger(query);
			ArrayList<Sparql> rankedSparqls = new ArrayList<Sparql>();
			NRtime = (int)(System.currentTimeMillis()-t0);
			System.out.println("step0 [Node Recognition] : "+ NRtime +"ms");

			for(int i=query.sList.size()-1; i>=0; i--)
			{
				Sentence possibleSentence = query.sList.get(i);
				qlog.reloadSentence(possibleSentence);

				System.out.println("transQ: "+qlog.s.plainText);
				qlog.NRlog = query.preLog;
				qlog.SQGlog = "Id: "+query.queryId+"\nQuery: "+query.NLQuestion+"\n";
				qlog.SQGlog += qlog.NRlog;
				qlog.timeTable.put("step0", (int)NRtime);

				t = System.currentTimeMillis();
				QuestionParsing step1 = new QuestionParsing();
				step1.process(qlog);
				qlog.timeTable.put("step1", (int)(System.currentTimeMillis()-t));

				t = System.currentTimeMillis();
				BuildQueryGraph step2 = new BuildQueryGraph();
				step2.process(qlog);
				qlog.timeTable.put("step2", (int)(System.currentTimeMillis()-t));

				t = System.currentTimeMillis();
				AddtionalFix step3 = new AddtionalFix();
				step3.process(qlog);

				rankedSparqls.addAll(qlog.rankedSparqls);
				qlog.timeTable.put("step3", (int)(System.currentTimeMillis()-t));
			}

			for(Sparql spq: rankedSparqls)
				spq.deduplicate();

			Collections.sort(rankedSparqls);
			qlog.rankedSparqls = rankedSparqls;
			System.out.println("number of rankedSparqls = " + qlog.rankedSparqls.size());

			for (int i=0; i<qlog.rankedSparqls.size(); i++)
			{
				Sparql spq = qlog.rankedSparqls.get(i);
				String questionFocus = QuestionParsing.detectQuestionFocus(spq);
				if(questionFocus == null)
					questionFocus = "?"+qlog.target.originalForm;
				spq.questionFocus = questionFocus;
			}

			return qlog;
		}
		catch (Exception e) {
			e.printStackTrace();
			return qlog;
		}
	}

	public String getStdSparqlWoPrefix(QueryLogger qlog, Sparql curSpq)
	{
		if(qlog == null || curSpq == null)
			return null;

		String res = "";
		if (qlog.s.sentenceType==SentenceType.GeneralQuestion)
			res += "ask where";
		else
		{
			if(!curSpq.countTarget)
				res += ("select DISTINCT " + curSpq.questionFocus + " where");
			else
				res += ("select COUNT(DISTINCT " + curSpq.questionFocus + ") where");
		}
		res += "\n";
		res += curSpq.toStringForGStore();
		if(curSpq.moreThanStr != null)
			res += curSpq.moreThanStr+"\n";
		if(curSpq.mostStr != null)
			res += curSpq.mostStr+"\n";

		return res;
	}

	public Sparql getUntypedSparql (Sparql spq)
	{
		if(spq == null)
			return null;
		spq.removeAllTypeInfo();
		if (spq.tripleList.size() == 0) return null;
		return spq;
	}

	// -------------------------------------------------------------------------
	// Shared SSL + connection helpers
	// -------------------------------------------------------------------------
	private static void trustAllSSL() throws Exception {
		javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
			new javax.net.ssl.X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
				public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
				public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
			}
		};
		javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
		sc.init(null, trustAll, new java.security.SecureRandom());
		javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
	}

	private java.net.HttpURLConnection openConn(String sparql) throws Exception {
		String encoded = java.net.URLEncoder.encode(sparql, "UTF-8");
		String url = "https://" + Globals.QueryEngineIP + ":" + Globals.QueryEnginePort
				+ "/repositories/" + Globals.GraphDBRepo + "?query=" + encoded;
		java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
		conn.setRequestProperty("Accept", "text/tab-separated-values");
		String creds = Globals.GraphDBUser + ":" + Globals.GraphDBPassword;
		conn.setRequestProperty("Authorization",
				"Basic " + java.util.Base64.getEncoder().encodeToString(creds.getBytes("UTF-8")));
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(15000);
		return conn;
	}

	private Matches parseMatches(java.util.List<String> lines) {
		Matches ret = new Matches();
		if (lines.size() <= 1) { ret.answersNum = 0; return ret; }
		String[] vars = lines.get(0).split("\t");
		for (int v = 0; v < vars.length; v++)
			vars[v] = vars[v].startsWith("?") ? vars[v].substring(1) : vars[v];
		int ansNum = lines.size() - 1;
		ret.answersNum = ansNum;
		ret.answers = new String[ansNum][vars.length];
		for (int i = 1; i < lines.size(); i++) {
			String[] vals = lines.get(i).split("\t", -1);
			for (int j = 0; j < vars.length; j++) {
				String v = j < vals.length ? vals[j] : "";
				if (v.startsWith("<") && v.endsWith(">")) v = v.substring(1, v.length()-1);
				else if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length()-1);
				ret.answers[i-1][j] = vars[j] + ":" + v;
			}
		}
		return ret;
	}

	private java.util.List<String> executeQuery(String sparql) throws Exception {
		java.net.HttpURLConnection conn = openConn(sparql);
		int code = conn.getResponseCode();
		if (code != 200) {
			java.io.InputStream err = conn.getErrorStream();
			String body = "";
			if (err != null) {
				java.util.Scanner sc2 = new java.util.Scanner(err, "UTF-8");
				body = sc2.useDelimiter("\\A").hasNext() ? sc2.next() : "";
				sc2.close();
			}
			System.out.println("GraphDB returned " + code + ": " + body);
			return new java.util.ArrayList<>();
		}
		java.io.BufferedReader br = new java.io.BufferedReader(
				new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
		java.util.List<String> lines = new java.util.ArrayList<>();
		String line;
		while ((line = br.readLine()) != null) lines.add(line);
		br.close();
		conn.disconnect();
		return lines;
	}

	// -------------------------------------------------------------------------
	// Retry level 2: flat query with label-based entity matching
	// -------------------------------------------------------------------------
	/**
	 * When the primary query returns 0 results, replace concrete entity URIs
	 * with ?_inst constrained by rdfs:label CONTAINS. This handles the case
	 * where the resolved entity is a class (Hemp_fiber) but the data lives on
	 * specimens (Hemp_fiber_0, Hemp_fiber_1 …).
	 */
	private Matches retryWithLabelFallback(String primarySparql) {
		Matches ret = new Matches();
		try {
			java.util.Set<String> entityURIs = new java.util.LinkedHashSet<>();
			// entity in object position: ?var <pred> <entity>
			java.util.regex.Matcher m1 = java.util.regex.Pattern
					.compile("\\?\\w+\\s+<[^>]+>\\s+(<http[^>]+>)")
					.matcher(primarySparql);
			while (m1.find()) entityURIs.add(m1.group(1));
			// entity in subject position: <entity> <pred> ?var
			java.util.regex.Matcher m2 = java.util.regex.Pattern
					.compile("(<http[^>]+>)\\s+<[^>]+>\\s+\\?")
					.matcher(primarySparql);
			while (m2.find()) entityURIs.add(m2.group(1));

			if (entityURIs.isEmpty()) return ret;

			for (String entityURI : entityURIs) {
				String bare = entityURI.replaceAll("^<|>$", "");
				String localName = bare.contains("#")
						? bare.substring(bare.lastIndexOf('#') + 1)
						: bare.contains("/") ? bare.substring(bare.lastIndexOf('/') + 1) : bare;
				String labelTerm = localName.replace("_", " ").toLowerCase().trim();

				String retrySparql = primarySparql.replace(entityURI, "?_inst");
				String labelFilter =
						"?_inst <http://www.w3.org/2000/01/rdf-schema#label> ?_lbl .\n"
						+ "  FILTER(CONTAINS(LCASE(STR(?_lbl)), \""
						+ labelTerm.replace("\"", "\\\"") + "\"))\n  ";
				retrySparql = retrySparql.replaceFirst("(?i)where\\s*\\{", "where {\n  " + labelFilter);

				System.out.println("Label-fallback SPARQL:\n" + retrySparql);
				java.util.List<String> lines = executeQuery(retrySparql);
				if (lines.size() > 1) return parseMatches(lines);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	// -------------------------------------------------------------------------
	// Retry level 3: 2-hop traversal via mat:hasProperty
	// -------------------------------------------------------------------------

	/**
	 * Stop-words to strip when deriving predicate search terms from camelCase
	 * local names. These are common English connectives and overly generic
	 * domain prefixes that appear in property names but carry no discriminating
	 * meaning for a CONTAINS search (e.g. "mech_tensileElongationAtBreak" →
	 * strip "at", "mech" → keep "tensile", "elongation", "break").
	 */
	private static final java.util.Set<String> PRED_STOP_WORDS =
			new java.util.HashSet<>(java.util.Arrays.asList(
					// English connectives / prepositions / articles
					"a", "an", "the", "at", "by", "of", "in", "on", "to",
					"for", "and", "or", "is", "as", "up", "per",
					// Generic property-name prefixes common in material KGs
					"has", "mech", "prop", "val", "value", "property",
					"data", "info", "ref", "type", "id"
			));

	/**
	 * Splits a predicate local name (camelCase + underscore) into meaningful
	 * search tokens by:
	 *   1. Splitting on underscores.
	 *   2. Splitting each segment on camelCase boundaries.
	 *   3. Lower-casing everything.
	 *   4. Removing stop-words and tokens shorter than 3 characters.
	 *
	 * Examples:
	 *   mech_tensileElongationAtBreak → ["tensile", "elongation", "break"]
	 *   youngsModulus                 → ["youngs", "modulus"]
	 *   elongationAtBreak             → ["elongation", "break"]
	 *   hasVariability                → ["variability"]
	 */
	private java.util.List<String> splitPredicateTokens(String localName) {
		// Split on underscore first, then camelCase within each segment
		String spaced = localName
				.replaceAll("([a-z])([A-Z])", "$1 $2")   // camelCase split
				.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2") // handle ACRONYMs
				.replaceAll("[_\\s]+", " ")
				.toLowerCase()
				.trim();
		java.util.List<String> tokens = new java.util.ArrayList<>();
		for (String w : spaced.split("\\s+")) {
			if (w.length() >= 3 && !PRED_STOP_WORDS.contains(w))
				tokens.add(w);
		}
		return tokens;
	}

	/**
	 * ADDED: When both the primary flat query and the label fallback return 0
	 * results, the KG probably stores the value on a measurement node reachable
	 * via mat:hasProperty (a 2-hop pattern).
	 *
	 * This method:
	 * 1. Extracts the predicate URI from the failed SPARQL.
	 * 2. Derives meaningful search tokens by splitting camelCase + underscore
	 *    and removing stop-words:
	 *      mech_tensileElongationAtBreak → ["tensile","elongation","break"]
	 *    These are stacked as &&-combined CONTAINS filters on ?_pred so the
	 *    match is precise without knowing the exact URI in advance.
	 * 3. Derives the entity label term from the entity URI local name.
	 * 4. Builds and executes (example for elongation):
	 *      ?_inst rdfs:label ?_lbl . FILTER(label CONTAINS "hemp fiber")
	 *      ?_inst <HasPropertyURI> ?_prop .
	 *      ?_prop ?_pred ?value .
	 *      FILTER(CONTAINS(LCASE(STR(?_pred)),"tensile")
	 *          && CONTAINS(LCASE(STR(?_pred)),"elongation")
	 *          && CONTAINS(LCASE(STR(?_pred)),"break"))
	 *      FILTER(isLiteral(?value))
	 */
	private Matches retryWithTwoHop(String primarySparql) {
		Matches ret = new Matches();
		try {
			// --- Extract predicate URIs (predicate position) ---
			java.util.Set<String> predicateURIs = new java.util.LinkedHashSet<>();
			// ?var <PRED> <entity>
			java.util.regex.Matcher pm1 = java.util.regex.Pattern
					.compile("\\?\\w+\\s+(<http[^>]+>)\\s+<http")
					.matcher(primarySparql);
			while (pm1.find()) predicateURIs.add(pm1.group(1));
			// <entity> <PRED> ?var
			java.util.regex.Matcher pm2 = java.util.regex.Pattern
					.compile("<http[^>]+>\\s+(<http[^>]+>)\\s+\\?")
					.matcher(primarySparql);
			while (pm2.find()) predicateURIs.add(pm2.group(1));

			// --- Extract entity URIs (subject/object position) ---
			java.util.Set<String> entityURIs = new java.util.LinkedHashSet<>();
			java.util.regex.Matcher em1 = java.util.regex.Pattern
					.compile("\\?\\w+\\s+<[^>]+>\\s+(<http[^>]+>)")
					.matcher(primarySparql);
			while (em1.find()) entityURIs.add(em1.group(1));
			java.util.regex.Matcher em2 = java.util.regex.Pattern
					.compile("(<http[^>]+>)\\s+<[^>]+>\\s+\\?")
					.matcher(primarySparql);
			while (em2.find()) entityURIs.add(em2.group(1));

			if (predicateURIs.isEmpty() || entityURIs.isEmpty()) return ret;

			// --- Extract SELECT variable name ---
			java.util.regex.Matcher sm = java.util.regex.Pattern
					.compile("(?i)select\\s+DISTINCT\\s+(\\?\\w+)")
					.matcher(primarySparql);
			String selectVar = sm.find() ? sm.group(1) : "?_value";

			for (String predURI : predicateURIs) {
				// Derive meaningful predicate search tokens from camelCase local name
				String predBare = predURI.replaceAll("^<|>$", "");
				String predLocal = predBare.contains("#")
						? predBare.substring(predBare.lastIndexOf('#') + 1)
						: predBare.contains("/") ? predBare.substring(predBare.lastIndexOf('/') + 1) : predBare;

				java.util.List<String> predTokens = splitPredicateTokens(predLocal);
				if (predTokens.isEmpty()) {
					System.out.println("Two-hop: no meaningful tokens from predicate local name: " + predLocal + " — skipping");
					continue;
				}

				System.out.println("Two-hop predicate tokens: " + predTokens + " (from " + predLocal + ")");

				// Build stacked CONTAINS filter: CONTAINS(...,"tensile") && CONTAINS(...,"elongation") && ...
				StringBuilder predFilter = new StringBuilder();
				for (int i = 0; i < predTokens.size(); i++) {
					if (i > 0) predFilter.append("\n      && ");
					predFilter.append("CONTAINS(LCASE(STR(?_pred)), \"")
					           .append(predTokens.get(i).replace("\"", "\\\""))
					           .append("\")");
				}

				for (String entityURI : entityURIs) {
					String bare = entityURI.replaceAll("^<|>$", "");
					String localName = bare.contains("#")
							? bare.substring(bare.lastIndexOf('#') + 1)
							: bare.contains("/") ? bare.substring(bare.lastIndexOf('/') + 1) : bare;
					String entityLabelTerm = localName.replace("_", " ").toLowerCase().trim();

					String twoHopSparql =
							"select DISTINCT " + selectVar + " where {\n"
							+ "  ?_inst <http://www.w3.org/2000/01/rdf-schema#label> ?_lbl .\n"
							+ "  FILTER(CONTAINS(LCASE(STR(?_lbl)), \""
							+ entityLabelTerm.replace("\"", "\\\"") + "\"))\n"
							+ "  ?_inst <" + Globals.HasPropertyURI + "> ?_prop .\n"
							+ "  ?_prop ?_pred " + selectVar + " .\n"
							+ "  FILTER(" + predFilter + ")\n"
							+ "  FILTER(isLiteral(" + selectVar + "))\n"
							+ "} LIMIT " + Globals.MaxAnswerNum;

					System.out.println("Two-hop fallback SPARQL:\n" + twoHopSparql);
					java.util.List<String> lines = executeQuery(twoHopSparql);
					if (lines.size() > 1) return parseMatches(lines);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	// -------------------------------------------------------------------------
	// Main query execution with 3-level retry chain
	// -------------------------------------------------------------------------
	/**
	 * Executes the SPARQL against GraphDB with a 3-level retry chain:
	 *
	 * Level 1 — Primary flat query (toStringForGStore2 output)
	 *   Works when PAROT correctly resolves both the entity URI and predicate URI.
	 *
	 * Level 2 — Label-based flat retry
	 *   Replaces the concrete entity URI with a label CONTAINS filter.
	 *   Handles the class-vs-specimen mismatch (Hemp_fiber vs Hemp_fiber_0).
	 *
	 * Level 3 — 2-hop traversal via mat:hasProperty
	 *   Handles KGs where values live on measurement nodes, not directly on
	 *   the subject. Splits the predicate local name on camelCase + underscore
	 *   boundaries, strips stop-words, and stacks all meaningful tokens as
	 *   &&-combined CONTAINS filters so it matches the real KG predicate
	 *   precisely without knowing its exact URI in advance.
	 */
	public Matches getAnswerFromGStore2(Sparql spq) {
		Matches ret = new Matches();
		try {
			String sparql = resolveLocalNames(spq.toStringForGStore2());
			System.out.println("GraphDB SPARQL:\n" + sparql);

			trustAllSSL();

			// Level 1: primary flat query
			java.util.List<String> lines = executeQuery(sparql);
			ret = parseMatches(lines);

			// Level 2: label-based flat retry
			if (ret.answersNum == 0 || ret.answers == null) {
				System.out.println("Primary query empty — retrying with label-based fallback...");
				ret = retryWithLabelFallback(sparql);
			}

			// Level 3: 2-hop via mat:hasProperty
			if (ret.answersNum == 0 || ret.answers == null) {
				System.out.println("Label fallback empty — retrying with 2-hop property traversal...");
				ret = retryWithTwoHop(sparql);
			}

		} catch (Exception e) {
			e.printStackTrace();
			ret.answersNum = 0;
		}
		return ret;
	}

	// -------------------------------------------------------------------------
	// URI resolution helpers
	// -------------------------------------------------------------------------
	/**
	 * Replaces all <localName> tokens in the SPARQL string with full URIs.
	 * Priority: KG predicate → KG entity → DBpedia entity → fallback (unchanged).
	 */
	private String resolveLocalNames(String gstoreBody) {
		StringBuffer result = new StringBuffer();
		java.util.regex.Pattern p = java.util.regex.Pattern.compile("<([^>\\s]+)>");
		java.util.regex.Matcher m = p.matcher(gstoreBody);
		while (m.find()) {
			String localName = m.group(1);
			if (localName.startsWith("http://") || localName.startsWith("https://")) {
				m.appendReplacement(result, java.util.regex.Matcher.quoteReplacement("<" + localName + ">"));
				continue;
			}
			m.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(resolveToFullURI(localName)));
		}
		m.appendTail(result);
		return result.toString();
	}

	private String resolveToFullURI(String localName) {
		if (Globals.pd != null && Globals.pd.predicateName2URI != null
				&& Globals.pd.predicateName2URI.containsKey(localName))
			return "<" + Globals.pd.predicateName2URI.get(localName) + ">";
		if (lcn.EntityFragmentFields.entityName2URI != null
				&& lcn.EntityFragmentFields.entityName2URI.containsKey(localName))
			return "<" + lcn.EntityFragmentFields.entityName2URI.get(localName) + ">";
		if (lcn.EntityFragmentFields.entityName2Id != null
				&& lcn.EntityFragmentFields.entityName2Id.containsKey(localName)) {
			int id = lcn.EntityFragmentFields.entityName2Id.get(localName);
			if (id < 200000)
				return "<http://dbpedia.org/resource/" + localName + ">";
		}
		System.out.println("resolveLocalNames: cannot resolve <" + localName + "> to a URI");
		return "<" + localName + ">";
	}

	public static void main (String[] args)
	{
		Globals.init();
		GAnswer ga = new GAnswer();
		int i =1;

		List<String> inputList = FileUtil.readFile("./questions.txt");
		for(String input: inputList)
		{
			ArrayList<String> outputs = new ArrayList<String>();
			ArrayList<String> spqs = new ArrayList<String>();
			spqs.add("id:"+String.valueOf(i));
			i++;

			long parsing_st_time = System.currentTimeMillis();

			QueryLogger qlog = ga.getSparqlList(input);
			if(qlog == null || qlog.rankedSparqls == null)
				continue;

			long parsing_ed_time = System.currentTimeMillis();
			System.out.println("Question Understanding time: "+ (int)(parsing_ed_time - parsing_st_time)+ "ms");
			System.out.println("TripleCheck time: "+ qlog.timeTable.get("TripleCheck") + "ms");
			System.out.println("SparqlCheck time: "+ qlog.timeTable.get("SparqlCheck") + "ms");
			System.out.println("Ranked Sparqls: " + qlog.rankedSparqls.size());

			outputs.add(qlog.SQGlog);
			outputs.add(qlog.SQGlog + "Building HQG time: "+ (qlog.timeTable.get("step0")+qlog.timeTable.get("step1")+qlog.timeTable.get("step2")-qlog.timeTable.get("BQG_topkjoin")) + "ms");
			outputs.add("TopKjoin time: "+ qlog.timeTable.get("BQG_topkjoin") + "ms");
			outputs.add("Question Understanding time: "+ (int)(parsing_ed_time - parsing_st_time)+ "ms");

			long excuting_st_time = System.currentTimeMillis();
			Matches m = null;
			System.out.println("[RESULT]");
			ArrayList<String> lastSpqList = new ArrayList<String>();
			int idx;
			for(idx=1; idx<=Math.min(qlog.rankedSparqls.size(), 5); idx++)
			{
				Sparql curSpq = qlog.rankedSparqls.get(idx-1);
				String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, curSpq);
				lastSpqList.add(stdSPQwoPrefix);

				System.out.println("[" + idx + "]" + "score=" + curSpq.score);
				System.out.println(stdSPQwoPrefix);

				if(idx <= MAX_SPQ_NUM)
					outputs.add("[" + idx + "]" + "score=" + curSpq.score + "\n" + stdSPQwoPrefix);

				if(m == null || m.answers == null)
				{
					if(curSpq.tripleList.size()>0 && curSpq.questionFocus!=null)
						m = ga.getAnswerFromGStore2(curSpq);
					if(m != null && m.answers != null)
                    {
                        qlog.sparql = curSpq;
                        qlog.match = m;
                        qlog.reviseAnswers();
                        System.out.println("Query Executing time: "+ (int)(System.currentTimeMillis() - excuting_st_time)+ "ms");
                    }
				}
			}

			if(!qlog.rankedSparqls.isEmpty())
			{
				Sparql untypedSparql = ga.getUntypedSparql(qlog.rankedSparqls.get(0));
				if(untypedSparql != null)
				{
					String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, untypedSparql);
					if(!lastSpqList.contains(stdSPQwoPrefix))
						outputs.add("[" + Math.min(MAX_SPQ_NUM+1, idx) + "]" + "score=" + 1000 + "\n" + stdSPQwoPrefix + "\n");
				}
			}

			FileUtil.writeFile(outputs, "./output.txt", true);
		}
	}
}