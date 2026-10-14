package lcn;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.HashMap;

import qa.Globals;

public class EntityFragmentFields {

	public static HashMap<String, Integer> entityName2Id = null;
	public static HashMap<Integer, String> entityId2Name = null;
	public static HashMap<Integer, String> entityFragmentString = null;

	// ADDED: maps local name → full URI for KG entities
	public static HashMap<String, String> entityName2URI = null;

	public static void load() throws Exception {
		entityName2Id = new HashMap<String, Integer>();
		entityId2Name = new HashMap<Integer, String>();
		entityFragmentString = new HashMap<Integer, String>();
		entityName2URI = new HashMap<String, String>();

		// --- Load DBpedia entity IDs ---
		long t1 = System.currentTimeMillis();
		System.out.println("Loading entity id ...");
		String idFile = Globals.localPath + "data/DBpedia2016/fragments/id_mappings/16entity_id.txt";
		BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(idFile), "UTF-8"));
		String line;
		while ((line = br.readLine()) != null) {
			String[] fields = line.split("\t");
			if (fields.length < 2) continue;
			// strip surrounding quotes from name
			String name = fields[0].substring(1, fields[0].length() - 1);
			int id = Integer.parseInt(fields[1].trim());
			entityName2Id.put(name, id);
			entityId2Name.put(id, name);
		}
		br.close();
		long t2 = System.currentTimeMillis();
		System.out.println("Load " + entityId2Name.size() + " entity ids in " + (t2 - t1) + "ms.");

		// --- Load DBpedia entity fragments ---
		System.out.println("Loading entity fragments ...");
		String fragFile = Globals.localPath + "data/DBpedia2016/fragments/entity_RDF_fragment/16entity_fragment.txt";
		br = new BufferedReader(
				new InputStreamReader(new FileInputStream(fragFile), "UTF-8"));
		while ((line = br.readLine()) != null) {
			String[] fields = line.split("\t");
			if (fields.length != 2) continue;
			int id = Integer.parseInt(fields[0].trim());
			entityFragmentString.put(id, fields[1]);
		}
		br.close();
		long t3 = System.currentTimeMillis();
		System.out.println("Load " + entityFragmentString.size() + " entity fragments in " + (t3 - t2) + "ms.");

		// --- Load KG entities from GraphDB ---
		loadFromGraphDB();
	}

	/**
	 * Query GraphDB for all entities + their rdfs:labels and inject them into
	 * the entity maps with IDs starting at 200000 (above DBpedia range).
	 *
	 * CHANGED: SELECT now fetches ?label as well as ?entity.
	 * After storing localName → URI, we also store
	 *   label.toLowerCase().replace(" ","_") → URI
	 * as a secondary key so that resolveToFullURI("hemp_fiber") can find
	 * Hemp_fiber_0's URI even when the emList's top entity name is the
	 * RDF label "hemp fiber" rather than the local URI name "Hemp_fiber_0".
	 *
	 * URL-decode is applied to localName to handle percent-encoded URIs
	 * like Terre%20de%20Lin%20Hemp → Terre_de_Lin_Hemp.
	 */
	public static void loadFromGraphDB() {
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

			// CHANGED: also SELECT ?label so we can build a label-derived secondary key
			String sparql = "SELECT DISTINCT ?entity ?label WHERE { "
					+ "?entity <http://www.w3.org/2000/01/rdf-schema#label> ?label . "
					+ "FILTER NOT EXISTS { ?entity a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> } "
					+ "FILTER NOT EXISTS { ?entity a <http://www.w3.org/2002/07/owl#ObjectProperty> } "
					+ "FILTER NOT EXISTS { ?entity a <http://www.w3.org/2002/07/owl#DatatypeProperty> } "
					+ "FILTER NOT EXISTS { ?entity a <http://www.w3.org/2002/07/owl#AnnotationProperty> } "
					+ "}";
			String encoded = java.net.URLEncoder.encode(sparql, "UTF-8");
			String url = "https://" + Globals.QueryEngineIP + ":" + Globals.QueryEnginePort
					+ "/repositories/" + Globals.GraphDBRepo
					+ "?query=" + encoded;

			java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
			conn.setRequestProperty("Accept", "text/tab-separated-values");
			String creds = Globals.GraphDBUser + ":" + Globals.GraphDBPassword;
			conn.setRequestProperty("Authorization",
					"Basic " + java.util.Base64.getEncoder().encodeToString(creds.getBytes("UTF-8")));
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(15000);

			if (conn.getResponseCode() != 200) {
				System.out.println("EntityFragmentFields.loadFromGraphDB(): GraphDB returned " + conn.getResponseCode());
				return;
			}

			BufferedReader br = new BufferedReader(
					new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
			br.readLine(); // skip TSV header (?entity \t ?label)

			int nextId = 200000;
			int added = 0;
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) continue;

				// TSV: <URI> \t "label"[@lang]
				String[] parts = line.split("\t", 2);
				if (parts.length < 1) continue;

				String fullURI = parts[0].replaceAll("^<|>$", "").trim();
				if (fullURI.isEmpty()) continue;

				// Extract and normalise local name
				String localName = fullURI.contains("#")
						? fullURI.substring(fullURI.lastIndexOf('#') + 1)
						: fullURI.contains("/")
						? fullURI.substring(fullURI.lastIndexOf('/') + 1)
						: fullURI;

				// ADDED: URL-decode percent-encoded local names (Terre%20de%20Lin%20Hemp → Terre de Lin Hemp)
				try { localName = URLDecoder.decode(localName, "UTF-8"); } catch (Exception ignore) {}
				localName = localName.replace(" ", "_");

				if (localName.isEmpty()) continue;

				// Parse label value
				String label = (parts.length >= 2) ? parts[1].trim() : "";
				if (label.startsWith("\"")) {
					int closeQ = label.lastIndexOf('"');
					label = (closeQ > 0) ? label.substring(1, closeQ) : label.substring(1);
				}

				if (!entityName2Id.containsKey(localName)) {
					entityName2Id.put(localName, nextId);
					entityId2Name.put(nextId, localName);
					entityFragmentString.put(nextId, "<" + fullURI + ">");
					entityName2URI.put(localName, fullURI);
					nextId++;
					added++;
				} else {
					entityName2URI.put(localName, fullURI);
				}

				// ADDED: secondary key from label so resolveToFullURI("hemp_fiber")
				// finds Hemp_fiber_0's URI when "hemp fiber" is the emList top name.
				// Only store if this key is not already taken by a primary local name.
				if (!label.isEmpty()) {
					String labelKey = label.toLowerCase().replace(" ", "_");
					if (!entityName2URI.containsKey(labelKey)) {
						int existingId = entityName2Id.containsKey(localName)
								? entityName2Id.get(localName) : nextId - 1;
						entityName2URI.put(labelKey, fullURI);
						entityName2Id.put(labelKey, existingId);
					}
				}
			}
			br.close();
			System.out.println("EntityFragmentFields.loadFromGraphDB(): added " + added + " KG entities.");

		} catch (Exception e) {
			System.out.println("EntityFragmentFields.loadFromGraphDB(): failed — " + e.getMessage());
		}
	}
}