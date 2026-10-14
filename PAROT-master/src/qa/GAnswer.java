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
	// Shared SSL setup (called once per connection)
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

	/**
	 * ADDED: When the primary query returns 0 results, retry with a label-based
	 * fallback. Entity URIs in object/subject position are replaced with a
	 * variable ?_inst that is constrained by rdfs:label CONTAINS matching.
	 *
	 * Example: <http://.../Hemp_fiber> becomes
	 *   ?_inst <rdfs:label> ?_lbl . FILTER(CONTAINS(LCASE(?_lbl), "hemp fiber"))
	 *
	 * This returns specimen results (Hemp_fiber_0, Hemp_fiber_1 …) even though
	 * the resolved URI was the class Hemp_fiber.
	 */
	private Matches retryWithLabelFallback(String primarySparql) {
		Matches ret = new Matches();
		try {
			// Find entity URIs in object position: ?var <pred> <entity> .
			java.util.Set<String> entityURIs = new java.util.LinkedHashSet<>();
			java.util.regex.Matcher m1 = java.util.regex.Pattern
					.compile("\\?\\w+\\s+<[^>]+>\\s+(<http[^>]+>)")
					.matcher(primarySparql);
			while (m1.find()) entityURIs.add(m1.group(1));
			// Find entity URIs in subject position: <entity> <pred> ?var .
			java.util.regex.Matcher m2 = java.util.regex.Pattern
					.compile("(<http[^>]+>)\\s+<[^>]+>\\s+\\?")
					.matcher(primarySparql);
			while (m2.find()) entityURIs.add(m2.group(1));

			if (entityURIs.isEmpty()) return ret;

			for (String entityURI : entityURIs) {
				String bare = entityURI.replaceAll("^<|>$", "");
				// Derive label search term: local name, remove trailing digits, spaces
				String localName = bare.contains("#")
						? bare.substring(bare.lastIndexOf('#') + 1)
						: bare.contains("/") ? bare.substring(bare.lastIndexOf('/') + 1) : bare;
				String labelTerm = localName.replace("_", " ").toLowerCase().trim();

				// Replace entity URI with ?_inst variable everywhere in the SPARQL
				String retrySparql = primarySparql.replace(entityURI, "?_inst");

				// Inject label constraint after the opening "where {"
				String labelFilter =
						"?_inst <http://www.w3.org/2000/01/rdf-schema#label> ?_lbl .\n"
						+ "  FILTER(CONTAINS(LCASE(STR(?_lbl)), \""
						+ labelTerm.replace("\"", "\\\"") + "\"))\n  ";
				retrySparql = retrySparql.replaceFirst("(?i)where\\s*\\{", "where {\n  " + labelFilter);

				System.out.println("Label-fallback SPARQL:\n" + retrySparql);
				java.util.List<String> lines = executeQuery(retrySparql);
				if (lines.size() > 1) {
					return parseMatches(lines);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	/**
	 * CHANGED: HTTPS + SSL trust-all + Basic Auth against GraphDB.
	 * toStringForGStore2() already returns a complete SPARQL 1.1 query
	 * (with UNION for both triple directions).
	 * resolveLocalNames() expands <localName> tokens to full URIs.
	 * ADDED: label-based fallback retry when primary query returns 0 results.
	 */
	public Matches getAnswerFromGStore2(Sparql spq) {
		Matches ret = new Matches();
		try {
			String sparql = resolveLocalNames(spq.toStringForGStore2());
			System.out.println("GraphDB SPARQL:\n" + sparql);

			trustAllSSL();

			java.util.List<String> lines = executeQuery(sparql);
			ret = parseMatches(lines);

			// ADDED: if 0 results, retry using rdfs:label CONTAINS matching
			// so that class-level entities expand to their specimens
			if (ret.answersNum == 0 || ret.answers == null) {
				System.out.println("Primary query empty — retrying with label-based fallback...");
				ret = retryWithLabelFallback(sparql);
			}

		} catch (Exception e) {
			e.printStackTrace();
			ret.answersNum = 0;
		}
		return ret;
	}

	/**
	 * ADDED: Replace all <localName> tokens with full URIs.
	 * Priority: KG predicate > KG entity > DBpedia entity > fallback.
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