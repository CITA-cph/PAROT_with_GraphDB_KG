import java.io.*;
import java.util.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;

public class BuildTypeIndex {
    static String split(String sn){
        if(sn.contains("_")) return sn.replace("_"," ").toLowerCase();
        StringBuilder sb=new StringBuilder(); int last=0;
        for(int i=0;i<sn.length();i++){
            char c=sn.charAt(i);
            if(!(c>='a'&&c<='z')){sb.append(sn.substring(last,i).toLowerCase());sb.append(' ');last=i;}
        }
        sb.append(sn.substring(last).toLowerCase());
        while(sb.length()>0&&sb.charAt(0)==' ') sb.deleteCharAt(0);
        return sb.toString().trim();
    }
    public static void main(String[] args) throws Exception {
        String dataDir=args.length>0?args[0]:".";
        String idFile=dataDir+File.separator+"ids_type.tsv";
        String indexDirPath=dataDir+File.separator+"lucene"+File.separator+"type_fragment_index";
        List<String> names=new ArrayList<String>();
        BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(idFile),"UTF-8"));
        String line;
        while((line=br.readLine())!=null){
            String[] p=line.split("\t"); if(p.length<1) continue;
            String n=p[0].trim(); if(n.startsWith("<")&&n.endsWith(">")) n=n.substring(1,n.length()-1);
            if(!n.isEmpty()) names.add(n);
        }
        br.close();
        File indexDir=new File(indexDirPath); if(!indexDir.exists()) indexDir.mkdirs();
        IndexWriter w=new IndexWriter(indexDir,new StandardAnalyzer(),true);
        w.setMergeFactor(100000); w.setMaxBufferedDocs(1000); w.setMaxMergeDocs(Integer.MAX_VALUE);
        int count=0;
        for(String sn:names){
            String sp=split(sn);
            Document doc=new Document();
            doc.add(new Field("SplittedTypeShortName",sp,Field.Store.YES,Field.Index.TOKENIZED,Field.TermVector.WITH_POSITIONS_OFFSETS));
            doc.add(new Field("TypeShortName",sn,Field.Store.YES,Field.Index.NO));
            w.addDocument(doc); count++;
        }
        w.optimize(); w.close();
        System.out.println("Done. "+count+" types indexed to "+indexDirPath);
    }
}
