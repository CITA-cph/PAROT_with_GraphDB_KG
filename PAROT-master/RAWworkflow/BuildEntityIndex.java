import java.io.*;
import java.util.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;

public class BuildEntityIndex {
    public static void main(String[] args) throws Exception {
        String dataDir = args.length > 0 ? args[0] : ".";
        String idFile       = dataDir + File.separator + "ids_entity.tsv";
        String fragFile     = dataDir + File.separator + "step5_entity_fragments.tsv";
        String indexDirPath = dataDir + File.separator + "lucene" + File.separator + "entity_fragment_index";
        HashMap<Integer,String> id2name = new HashMap<Integer,String>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(idFile),"UTF-8"));
        String line;
        while((line=br.readLine())!=null){
            String[] p=line.split("\t"); if(p.length<2) continue;
            String n=p[0]; if(n.startsWith("<")&&n.endsWith(">")) n=n.substring(1,n.length()-1);
            id2name.put(Integer.parseInt(p[1].trim()),n);
        }
        br.close();
        System.out.println("Loaded "+id2name.size()+" entity IDs.");
        File indexDir=new File(indexDirPath); if(!indexDir.exists()) indexDir.mkdirs();
        IndexWriter w=new IndexWriter(indexDir,new StandardAnalyzer(),true);
        w.setMergeFactor(100000); w.setMaxBufferedDocs(1000); w.setMaxMergeDocs(Integer.MAX_VALUE);
        br=new BufferedReader(new InputStreamReader(new FileInputStream(fragFile),"UTF-8"));
        int count=0;
        while((line=br.readLine())!=null){
            String[] p=line.split("\t"); if(p.length!=2) continue;
            int id=Integer.parseInt(p[0].trim());
            if(!id2name.containsKey(id)) continue;
            String name=id2name.get(id).replace("____"," ").replace("__"," ").replace("_"," ");
            Document doc=new Document();
            doc.add(new Field("EntityName",name,Field.Store.YES,Field.Index.TOKENIZED,Field.TermVector.WITH_POSITIONS_OFFSETS));
            doc.add(new Field("EntityId",String.valueOf(id),Field.Store.YES,Field.Index.NO));
            doc.add(new Field("EntityFragment",p[1],Field.Store.YES,Field.Index.NO));
            w.addDocument(doc); count++;
            if(count%10000==0) System.out.println("  "+count+" indexed...");
        }
        br.close(); w.optimize(); w.close();
        System.out.println("Done. "+count+" entities indexed to "+indexDirPath);
    }
}
