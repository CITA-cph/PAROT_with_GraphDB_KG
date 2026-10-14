package paradict;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;




import nlp.tool.CoreNLP;
import qa.Globals;

public class ParaphraseDictionary {
	public static String localDataPath;
	public static String dbpedia_relation_paraphrases_baseform_withScore;
	public static String dbpedia_relation_paraphrases_baseform_withScore_rerank;
	public static String dbpedia_relation_paraphrases_handwrite;
	public static String dbpedia_predicate_id;
	public static String dbpedia_dbo_predicate;

	public HashMap<String, Integer> predicate_2_id = null;
	public HashMap<Integer, String> id_2_predicate = null;
	public HashSet<Integer> dbo_predicate_id = null;
	public HashMap<String, ArrayList<PredicateIDAndSupport>> nlPattern_2_predicateList = null;
	public HashMap<String, ArrayList<String>> invertedIndex = null;

	// ADDED: maps predicate local name → full URI for KG predicates
	public HashMap<String, String> predicateName2URI = null;

	public HashSet<String> relns_subject;
	public HashSet<String> relns_object;
	public HashSet<String> prepositions;
	public HashSet<String> bannedTypes;

	public int typePredicateID = 5157;	//Dbpedia 2016 <type>=5157 | It will be updated according the dataset
	public int totalPredCount = 0;
	public int paraphrasedPredCount = 0;
	public int lineCount = 0;

	/**
	 * constructor
	 * @param parser
	 * @param ner
	 */
	public ParaphraseDictionary () {
		String fixedPath = Globals.localPath;

		System.out.println(System.getProperty("user.dir"));
		localDataPath = fixedPath + "data/DBpedia2016/parapharse/";
		dbpedia_relation_paraphrases_baseform_withScore_rerank = localDataPath + "dbpedia-relation-paraphrases-withScore-baseform-merge-sorted-rerank-slct.txt";
		dbpedia_relation_paraphrases_handwrite = localDataPath + "dbpedia-relation-paraphrase-handwrite.txt";

		dbpedia_predicate_id = localDataPath + "16predicate_id.txt";
		dbpedia_dbo_predicate = localDataPath + "16dbo_predicates.txt";

		bannedTypes = new HashSet<String>();
		bannedTypes.add("Mayor");

		relns_subject = new HashSet<String>();
		relns_subject.add("subj");
		relns_subject.add("csubjpass");
		relns_subject.add("csubj");
		relns_subject.add("xsubj");
		relns_subject.add("nsubjpass");
		relns_subject.add("nsubj");
		relns_subject.add("poss");	// Obama's wife
		relns_subject.add("dobj");

		relns_object = new HashSet<String>();
		relns_object.add("dobj");
		relns_object.add("iobj");
		relns_object.add("obj");
		relns_object.add("pobj");

		prepositions = new HashSet<String>();
		prepositions.add("in");//in at on with to from before after of for
		prepositions.add("at");
		prepositions.add("on");
		prepositions.add("with");
		prepositions.add("to");
		prepositions.add("from");
		prepositions.add("before");
		prepositions.add("after");
		prepositions.add("of");
		prepositions.add("for");
		prepositions.add("as");

		try {
			loadPredicateId();
			loadDboPredicateFromGraphDB(); // CHANGED: was loadDboPredicate()
			loadParaDict();
			buildInvertedIndex();
			typePredicateID = predicate_2_id.get("type");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Load the mapping between predicates and their IDs.
	 * @throws IOException
	 */
	public void loadPredicateId () throws IOException {
		predicate_2_id = new HashMap<String, Integer>();
		id_2_predicate = new HashMap<Integer, String>();

		String input_filename = dbpedia_predicate_id;
		File file = new File(input_filename);
		InputStreamReader in = null;
		BufferedReader br = null;
		try{
			in = new InputStreamReader(new FileInputStream(file), "utf-8");
			br = new BufferedReader(in);
			String line = null;
			while ((line = br.readLine())!= null) {
			String[] lines = line.split("\t");
			predicate_2_id.put(lines[0], Integer.parseInt(lines[1]));
			id_2_predicate.put(Integer.parseInt(lines[1]), lines[0]);
		}
		}catch(IOException e){
			System.out.println("NLPatterns.loadPredicateId() : IOException!");
			e.printStackTrace();
		}finally{
			if(br != null){
				try{
					br.close();
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}
		System.out.println("NLPatterns.loadPredicateId() : ok!");
	}

	/**
	 * ADDED: Load all predicates from GraphDB via HTTPS (replaces loadDboPredicate / 16dbo_predicates.txt).
	 * Queries GraphDB for every predicate that has an rdfs:label, treating all KG predicates
	 * as "core" predicates (equivalent to the old DBO set).
	 * ADDED: also populates predicateName2URI (local name → full URI).
	 */
	public void loadDboPredicateFromGraphDB() {
		dbo_predicate_id = new HashSet<Integer>();
		predicateName2URI = new HashMap<String, String>();
		try {
			// Trust-all SSL for self-signed certificate
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

			// Query only OWL/RDF properties (actual predicates, not entities)
			String sparql = "SELECT DISTINCT ?p WHERE { "
					+ "?p <http://www.w3.org/2000/01/rdf-schema#label> ?label . "
					+ "{ ?p a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> } "
					+ "UNION { ?p a <http://www.w3.org/2002/07/owl#ObjectProperty> } "
					+ "UNION { ?p a <http://www.w3.org/2002/07/owl#DatatypeProperty> } "
					+ "UNION { ?p a <http://www.w3.org/2002/07/owl#AnnotationProperty> } "
					+ "}";
			String encoded = java.net.URLEncoder.encode(sparql, "UTF-8");
			String url = "https://" + Globals.QueryEngineIP + ":" + Globals.QueryEnginePort
					   + "/repositories/" + Globals.GraphDBRepo
					   + "?query=" + encoded;
			java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
					new java.net.URL(url).openConnection();
			conn.setRequestProperty("Accept", "text/tab-separated-values");
			String creds = Globals.GraphDBUser + ":" + Globals.GraphDBPassword;
			String basicAuth = "Basic " + java.util.Base64.getEncoder().encodeToString(creds.getBytes("UTF-8"));
			conn.setRequestProperty("Authorization", basicAuth);
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(10000);
			int rc = conn.getResponseCode();
			if (rc != 200) {
				java.io.InputStream errStream = conn.getErrorStream();
				String body = "";
				if (errStream != null) {
					java.util.Scanner sc2 = new java.util.Scanner(errStream, "UTF-8");
					body = sc2.useDelimiter("\\A").hasNext() ? sc2.next() : "";
					sc2.close();
				}
				System.out.println("loadDboPredicateFromGraphDB(): GraphDB returned " + rc + " ? " + body);
				return;
			}
			java.io.BufferedReader br2 = new java.io.BufferedReader(
					new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
			br2.readLine(); // skip TSV header
			String line2;
			while ((line2 = br2.readLine()) != null) {
				String uri = line2.replaceAll("^<|>$", "").trim();
				if (uri.isEmpty()) continue;
				String localName = uri.contains("#") ? uri.substring(uri.lastIndexOf('#') + 1)
								 : uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1)
								 : uri;
				if (localName.isEmpty()) continue;
				// Store URI mapping regardless of whether it's in predicate_2_id
				predicateName2URI.put(localName, uri);
				if (predicate_2_id.containsKey(localName)) {
					dbo_predicate_id.add(predicate_2_id.get(localName));
				}
			}
			br2.close();
			System.out.println("loadDboPredicateFromGraphDB(): loaded " + dbo_predicate_id.size()
					+ " predicate IDs, " + predicateName2URI.size() + " predicate URIs.");
		} catch (Exception e) {
			System.out.println("loadDboPredicateFromGraphDB(): failed — " + e.getMessage());
		}
	}

	/**
	 * Get predicate by its id
	 * @param predicateID
	 * @return
	 */
	public String getPredicateById (int predicateID) {
		return id_2_predicate.get(predicateID);
	}

	public void loadParaDict () throws Exception {
		nlPattern_2_predicateList = new HashMap<String, ArrayList<PredicateIDAndSupport>>();
		HashSet<String> missInDBP2014 = new HashSet<String>();

		InputStreamReader in = null;
		BufferedReader br = null;
		try{
			String inputFileName = dbpedia_relation_paraphrases_baseform_withScore_rerank;
			File file = new File(inputFileName);
			in = new InputStreamReader(new FileInputStream(file), "utf-8");
			br = new BufferedReader(in);
			String line = null;
			int lineCount = 0;
			//line = br.readLine();//read the first line which indicates the format
			while ((line = br.readLine()) != null)
			{
				if (line.startsWith("#")) continue;
				lineCount ++;
				String[] content = line.split("\t");

				if(!predicate_2_id.containsKey(content[0]))
				{
					missInDBP2014.add(content[0]);
					continue;
				}

				int predicateID = predicate_2_id.get(content[0]);
				String nlPattern = content[1].toLowerCase();
				int support = Integer.parseInt(content[2]);
				//double score = Double.parseDouble(content[3]);
				String []slctString = content[3].split(" ");
				double[] slct = new double[slctString.length];
				for (int i=0; i < slct.length; i++) {
					slct[i] = Double.parseDouble(slctString[i]);
				}

				if (!nlPattern_2_predicateList.containsKey(nlPattern)) {
					nlPattern_2_predicateList.put(nlPattern, new ArrayList<PredicateIDAndSupport>());
				}
				nlPattern_2_predicateList.get(nlPattern).add(new PredicateIDAndSupport(predicateID, support, slct));
			}

			System.out.println("Number of NL-Patterns-to-predicate mappings = " + lineCount);
			System.out.println("NLPatterns.size = " + nlPattern_2_predicateList.size());
			System.out.println("Predicate.size = " + predicate_2_id.size());
			System.out.println("Warning: Predicates not in DBpedia 2014 count: "+missInDBP2014.size());

			// Notice predicate itself and handwritten patterns have no wordSelectivity.
			addPredicateAsNLPattern(); // This is very important.
			addHandwriteAsNLPattern();
			addGraphDBLabelsAsNLPattern(); // ADDED: auto-load labels from GraphDB

			Iterator<String> it = nlPattern_2_predicateList.keySet().iterator();
			while (it.hasNext()) {
				Collections.sort(nlPattern_2_predicateList.get(it.next()));
			}

		}catch(IOException e){
			System.out.println("NLPatterns.Paradict() : IOException!");
		}finally{
			if(br!=null){
				try{
					br.close();
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}
		System.out.println("NLPatterns.Paradict() : ok!");
	}

	/**
	 * A set of very important NL patterns are the predicates themselves!
	 */
	public void addPredicateAsNLPattern () {
		final int support = 200;
		int predicate_id;
		for (String p : predicate_2_id.keySet())
		{
			// TODO: Omitting some bad relations (should be discarded in future)
			if(p.equals("state") || p.equals("states"))
				continue;

			predicate_id = predicate_2_id.get(p);
			StringBuilder pattern = new StringBuilder("");

			// Work/runtime	11,SpaceStation/volume	68 and some predicates have prefix (DBpedia 2015), discard the prefix when generating pattern
			if(p.contains("/"))
			{
				if(p.charAt(0)>='A' && p.charAt(0)<='Z')
					p = p.substring(p.indexOf("/")+1);
				//gameW/l	1974
				else
					p = p.replace("/", "");
			}

			int last = 0, i = 0;
			for(i = 0; i < p.length(); i ++) {
				// if it were not a small letter, then break it.
				if(!(p.charAt(i)>='a' && p.charAt(i)<='z')) {
					pattern.append(p.substring(last, i).toLowerCase());
					pattern.append(" ");
					last = i;
				}
			}
			pattern.append(p.substring(last, i).toLowerCase());
			for (i = 3; i < pattern.length(); i ++) {
				// the blank between two digits should be deleted.
				if (pattern.charAt(i)>='0' && pattern.charAt(i)<='9'
					&& pattern.charAt(i-1)==' '
					&& pattern.charAt(i-2)>='0' && pattern.charAt(i-2)<='9') {
					pattern.deleteCharAt(i-1);
				}
				// the blank between I and D should be deleted.
				else if (pattern.charAt(i)=='d'
					&& pattern.charAt(i-1)==' '
					&& pattern.charAt(i-2)=='i'
					&& pattern.charAt(i-3)==' ') {
					pattern.deleteCharAt(i-1);
				}
				// the blank between D and B should be deleted.
				else if (pattern.charAt(i)=='b'
					&& pattern.charAt(i-1)==' '
					&& pattern.charAt(i-2)=='d'
					&& pattern.charAt(i-3)==' ') {
					pattern.deleteCharAt(i-1);
				}
			}

			// Special case cannot use base form, eg, foundingYear	//TODO: maybe Porter's Algorithm
			String patternString = Globals.coreNLP.getBaseFormOfPattern(pattern.toString());

			if (!nlPattern_2_predicateList.containsKey(patternString)) {
				nlPattern_2_predicateList.put(patternString, new ArrayList<PredicateIDAndSupport>());
			}
			nlPattern_2_predicateList.get(patternString).add(
					new PredicateIDAndSupport(predicate_id,
							support,
							PredicateIDAndSupport.genSlct(patternString.split(" ").length)));
		}

		System.out.println("NLPatterns.addPredicateAsNLPattern(): ok!");
	}

	public void addHandwriteAsNLPattern() throws IOException {
		String inputFileName = dbpedia_relation_paraphrases_handwrite;
		InputStreamReader in = null;
		BufferedReader br = null;

		try{
			File file = new File(inputFileName);
			in = new InputStreamReader(new FileInputStream(file), "utf-8");
			br = new BufferedReader(in);

			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("#") || line.isEmpty()) continue;
				String[] content = line.split("\t");

				if(!predicate_2_id.containsKey(content[0]))
					continue;

				int predicateID = predicate_2_id.get(content[0]);
				String nlPattern = content[1].toLowerCase();
				int support = Integer.parseInt(content[2]);

				if (!nlPattern_2_predicateList.containsKey(nlPattern)) {
					nlPattern_2_predicateList.put(nlPattern, new ArrayList<PredicateIDAndSupport>());
				}
				nlPattern_2_predicateList.get(nlPattern).add(
						new PredicateIDAndSupport(predicateID,
								support,
								PredicateIDAndSupport.genSlct(nlPattern.split(" ").length)));
			}
		}catch(IOException e){
			System.out.println("NLPatterns.addHandwriteAsNLPattern(): IOException!");
		}finally{
			if(br!=null){
				try{
					br.close();
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}

		System.out.println("NLPatterns.addHandwriteAsNLPattern(): ok!");
	}

	/**
	 * ADDED: Query GraphDB for all predicate rdfs:labels and add them as NL patterns via HTTPS.
	 * Also adds a camelCase-split version of each predicate local name as an extra pattern.
	 */
	public void addGraphDBLabelsAsNLPattern() {
		try {
			// Trust-all SSL for self-signed certificate
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

			// Query only OWL/RDF properties for NL patterns (same filter as loadDboPredicateFromGraphDB)
			String sparql = "SELECT ?p ?label WHERE { "
					+ "?p <http://www.w3.org/2000/01/rdf-schema#label> ?label . "
					+ "{ ?p a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> } "
					+ "UNION { ?p a <http://www.w3.org/2002/07/owl#ObjectProperty> } "
					+ "UNION { ?p a <http://www.w3.org/2002/07/owl#DatatypeProperty> } "
					+ "UNION { ?p a <http://www.w3.org/2002/07/owl#AnnotationProperty> } "
					+ "}";
			String encoded = java.net.URLEncoder.encode(sparql, "UTF-8");
			String url = "https://" + Globals.QueryEngineIP + ":" + Globals.QueryEnginePort
					   + "/repositories/" + Globals.GraphDBRepo
					   + "?query=" + encoded;
			java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
					new java.net.URL(url).openConnection();
			conn.setRequestProperty("Accept", "text/tab-separated-values");
			String creds2 = Globals.GraphDBUser + ":" + Globals.GraphDBPassword;
			String basicAuth2 = "Basic " + java.util.Base64.getEncoder().encodeToString(creds2.getBytes("UTF-8"));
			conn.setRequestProperty("Authorization", basicAuth2);
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(10000);
			int rc = conn.getResponseCode();
			if (rc != 200) {
				java.io.InputStream errStream = conn.getErrorStream();
				String body = "";
				if (errStream != null) {
					java.util.Scanner sc2 = new java.util.Scanner(errStream, "UTF-8");
					body = sc2.useDelimiter("\\A").hasNext() ? sc2.next() : "";
					sc2.close();
				}
				System.out.println("addGraphDBLabelsAsNLPattern(): GraphDB returned " + rc + " ? " + body);
				return;
			}
			java.io.BufferedReader br2 = new java.io.BufferedReader(
					new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
			br2.readLine(); // skip TSV header
			String line2;
			int added = 0;
			while ((line2 = br2.readLine()) != null) {
				String[] cols = line2.split("\t", -1);
				if (cols.length < 2) continue;
				// extract local name from URI
				String uri = cols[0].replaceAll("^<|>$", "");
				String localName = uri.contains("#") ? uri.substring(uri.lastIndexOf('#') + 1)
								 : uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1)
								 : uri;
				if (!predicate_2_id.containsKey(localName)) continue;
				int predicateID = predicate_2_id.get(localName);
				// strip quotes and language tag from label
				String rawLabel = cols[1].replaceAll("^\"|\"|@\\w+$", "").trim();
				// add the label itself (lowercased)
				addNLPatternEntry(rawLabel.toLowerCase(), predicateID, 150);
				// also add camelCase-split version of localName as extra pattern
				String split = localName.replaceAll("([a-z])([A-Z])", "$1 $2")
									   .replaceAll("_", " ").toLowerCase().trim();
				if (!split.equals(rawLabel.toLowerCase())) {
					addNLPatternEntry(split, predicateID, 120);
				}
				added++;
			}
			br2.close();
			System.out.println("addGraphDBLabelsAsNLPattern(): added " + added + " patterns from KG labels.");
		} catch (Exception e) {
			System.out.println("addGraphDBLabelsAsNLPattern(): failed — " + e.getMessage());
		}
	}

	/**
	 * ADDED: Helper to insert a single NL pattern → predicate ID mapping.
	 */
	private void addNLPatternEntry(String pattern, int predicateID, int support) {
		String baseForm = Globals.coreNLP.getBaseFormOfPattern(pattern);
		if (!nlPattern_2_predicateList.containsKey(baseForm)) {
			nlPattern_2_predicateList.put(baseForm, new ArrayList<PredicateIDAndSupport>());
		}
		nlPattern_2_predicateList.get(baseForm).add(
			new PredicateIDAndSupport(predicateID, support,
				PredicateIDAndSupport.genSlct(baseForm.split(" ").length)));
	}

	/**
	 * Show the NLPatterns
	 */
	public void showNLPatterns () {
		System.out.println("predicate-->id");
		for (String s : predicate_2_id.keySet()) {
			System.out.println(s + "-->" + predicate_2_id.get(s));
		}
		Globals.systemPause();

		int count = 1;
		System.out.println("nlPattern-->predicate<support>");
		for (String p : nlPattern_2_predicateList.keySet()) {
			System.out.print("" + (count++) + ".\t" + p + "\t[" + nlPattern_2_predicateList.get(p).size() + "]\t");
			for (PredicateIDAndSupport i : nlPattern_2_predicateList.get(p)) {
				System.out.print(id_2_predicate.get(i.predicateID) + "<" + i.support + ">" + ", ");
			}
			System.out.println();
		}
	}

	/**
	 * Build the inverted index, where each word will be mapped to the patterns that it occurs
	 */
	public void buildInvertedIndex () {
		invertedIndex = new HashMap<String, ArrayList<String>>();
		// traversing all patterns
		for (String p : nlPattern_2_predicateList.keySet()) {
			String[] tokens = p.split(" ");
			for (String token : tokens) {
				if (token.length() < 1) continue;
				if (!invertedIndex.containsKey(token)) {
					invertedIndex.put(token, new ArrayList<String>());
				}
				invertedIndex.get(token).add(p);
			}
		}

		System.out.println("NLPatterns.buildInvertedIndex(): ok!");
	}

	public static void main (String[] args) {
		Globals.coreNLP = new CoreNLP();
		Globals.pd = new ParaphraseDictionary();
		//Globals.pd.showNLPatterns();
	}
}