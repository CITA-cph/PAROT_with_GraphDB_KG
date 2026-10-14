package qa.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

import lcn.EntityFragmentFields;
import log.QueryLogger;

import fgmt.EntityFragment;
import rdf.EntityMapping;

public class DBpediaLookup {

	public static HashMap<String, String> entMentionDict = null;

	public DBpediaLookup() {
		entMentionDict = new HashMap<String, String>();
	}

	public ArrayList<EntityMapping> getEntityMappings(String searchString, QueryLogger qlog) {
		ArrayList<String> slist = new ArrayList<String>();
		if (entMentionDict.containsKey(searchString))
			slist.add(entMentionDict.get(searchString));
		else
			slist = lookForEntityNames(searchString, qlog);

		if (slist.size() == 0 && searchString.contains(". "))
			slist.addAll(lookForEntityNames(searchString.replaceAll(". ", "."), qlog));

		ArrayList<EntityMapping> emlist = new ArrayList<EntityMapping>();

		String[] sa = searchString.split("_");
		int UpperCnt = 0;
		for (String str : sa) {
			if ((str.charAt(0) >= 'A' && str.charAt(0) <= 'Z') || (str.charAt(0) >= '0' && str.charAt(0) <= '9'))
				UpperCnt++;
		}

		System.out.print("DBpediaLookup find: " + slist + ", ");

		int count = 40;
		for (String s : slist) {
			int eid = -1;
			s = s.replace(" ", "_");
			if (EntityFragmentFields.entityName2Id.containsKey(s)) {
				eid = EntityFragmentFields.entityName2Id.get(s);
				emlist.add(new EntityMapping(eid, s, count));
				count -= 2;
			} else {
				System.out.print("Drop " + s + " because it not in Entity Dictionary. ");
			}
		}
		System.out.println("DBpediaLookup select: " + emlist);

		return emlist;
	}

	/**
	 * CHANGED: Query GraphDB via SPARQL for entities whose rdfs:label CONTAINS
	 * the search string. Returns the URI local name (URL-decoded, spaces→underscores)
	 * so it matches the normalized keys stored by EntityFragmentFields.loadFromGraphDB().
	 * Also returns the raw label as a fallback candidate.
	 */
	public ArrayList<String> lookForEntityNames(String searchString, QueryLogger qlog) {
		ArrayList<String> ret = new ArrayList<String>();
		try {
			// Trust-all SSL for self-signed cert
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

			// Replace underscores with spaces for the label CONTAINS search
			String safe = searchString.replace("\"", "").replace("\\", "").replace("_", " ");
			String sparql = "SELECT DISTINCT ?entity ?label WHERE { "
					+ "?entity <http://www.w3.org/2000/01/rdf-schema#label> ?label . "
					+ "FILTER(CONTAINS(LCASE(STR(?label)), LCASE(\"" + safe + "\"))) "
					+ "FILTER NOT EXISTS { ?entity a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> } "
					+ "FILTER NOT EXISTS { ?entity a <http://www.w3.org/2002/07/owl#ObjectProperty> } "
					+ "FILTER NOT EXISTS { ?entity a <http://www.w3.org/2002/07/owl#DatatypeProperty> } "
					+ "FILTER NOT EXISTS { ?entity a <http://www.w3.org/2002/07/owl#AnnotationProperty> } "
					+ "} LIMIT 10";

			String encoded = java.net.URLEncoder.encode(sparql, "UTF-8");
			String url = "https://" + qa.Globals.QueryEngineIP + ":" + qa.Globals.QueryEnginePort
					+ "/repositories/" + qa.Globals.GraphDBRepo
					+ "?query=" + encoded;

			java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
			conn.setRequestProperty("Accept", "text/tab-separated-values");
			String creds = qa.Globals.GraphDBUser + ":" + qa.Globals.GraphDBPassword;
			conn.setRequestProperty("Authorization",
					"Basic " + java.util.Base64.getEncoder().encodeToString(creds.getBytes("UTF-8")));
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(10000);

			if (conn.getResponseCode() != 200) {
				System.out.println("GraphDBLookup: returned " + conn.getResponseCode());
				return ret;
			}

			java.io.BufferedReader br = new java.io.BufferedReader(
					new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
			br.readLine(); // skip TSV header (?entity\t?label)
			String line;
			while ((line = br.readLine()) != null) {
				String[] cols = line.split("\t", -1);
				if (cols.length < 1) continue;

				// --- Primary key: local name from URI, normalized to match EntityFragmentFields ---
				String uri = cols[0].replaceAll("^<|>$", "");
				String localName = uri.contains("#") ? uri.substring(uri.lastIndexOf('#') + 1)
						: uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1)
						: uri;
				// CHANGED: URL-decode (handles %20 etc.) then spaces→underscores,
				// matching the normalization applied in EntityFragmentFields.loadFromGraphDB()
				try { localName = java.net.URLDecoder.decode(localName, "UTF-8"); } catch (Exception ignored) {}
				localName = localName.replace(" ", "_");
				if (!localName.isEmpty() && !ret.contains(localName))
					ret.add(localName);

				// --- Fallback: raw label text (spaces will be converted in getEntityMappings) ---
				if (cols.length >= 2) {
					String label = cols[1].replaceAll("^\"|\"|@\\w+$", "").trim();
					if (!label.isEmpty() && !ret.contains(label))
						ret.add(label);
				}
			}
			br.close();
		} catch (Exception e) {
			System.out.println("GraphDBLookup error: " + e.getMessage());
		}
		return ret;
	}

	public static void main(String argv[]) {
		DBpediaLookup lookup = new DBpediaLookup();
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		try {
			while (true) {
				System.out.print("Search string: ");
				String s = br.readLine();
				System.out.println(lookup.lookForEntityNames(s, null));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}