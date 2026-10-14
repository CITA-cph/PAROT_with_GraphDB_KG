import java.io.*;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.Document;
public class TestIndex {
    public static void main(String[] args) throws Exception {
        String term = args.length > 0 ? args[0] : "hemp";
        IndexReader reader = IndexReader.open(FSDirectory.open(new java.io.File("lucene/entity_fragment_index")));
        IndexSearcher searcher = new IndexSearcher(reader);
        QueryParser qp = new QueryParser("EntityName", new StandardAnalyzer());
        Query q = qp.parse(term);
        TopDocs hits = searcher.search(q, 10);
        System.out.println("Query: " + term + " -> " + hits.totalHits + " hits");
        for (ScoreDoc sd : hits.scoreDocs) {
            Document doc = searcher.doc(sd.doc);
            System.out.println("  [" + doc.get("EntityId") + "] " + doc.get("EntityName"));
        }
        searcher.close(); reader.close();
    }
}
