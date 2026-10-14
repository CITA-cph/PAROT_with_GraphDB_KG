package qa.extract;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import fgmt.EntityFragment;
import nlp.ds.Word;
import qa.Globals;
import rdf.EntityMapping;
import rdf.NodeSelectedWithScore;
import rdf.TypeMapping;
import rdf.MergedWord;
import utils.FileUtil;
import addition.*;

/**
 * Core class of Node Recognition
 * @author husen
 */
public class EntityRecognition {
	public String preLog = "";
	public String stopEntFilePath = Globals.localPath + "data/DBpedia2016/parapharse/stopEntDict.txt";
	
	double EntAcceptedScore = 26;
	double TypeAcceptedScore = 0.5;
	double AcceptedDiffScore = 1;
	
	public ArrayList<MergedWord> mWordList = null;
	public ArrayList<String> stopEntList = null;
	public ArrayList<String> badTagListForEntAndType = null;
	ArrayList<ArrayList<Integer>> selectedList = null;
	
	TypeRecognition tr = null;
	AddtionalFix af = null;
	
	public EntityRecognition() 
	{
		// LOG
		preLog = "";
		loadStopEntityDict();
		
		// Bad posTag for entity
		badTagListForEntAndType = new ArrayList<String>();
		badTagListForEntAndType.add("RBS");
		badTagListForEntAndType.add("JJS");
		badTagListForEntAndType.add("W");
		badTagListForEntAndType.add(".");
		badTagListForEntAndType.add("VBD");
		badTagListForEntAndType.add("VBN");
		badTagListForEntAndType.add("VBZ");
		badTagListForEntAndType.add("VBP");
		badTagListForEntAndType.add("POS");
		
		// Additional fix for CATEGORY (in DBpedia)
		af = new AddtionalFix();
		tr = new TypeRecognition();
		
		System.out.println("EntityRecognizer Initial : ok!");
	}
	
	public void loadStopEntityDict()
	{
		stopEntList = new ArrayList<String>();
		try 
		{
			List<String> inputs = FileUtil.readFile(stopEntFilePath);
			for(String line: inputs)
			{
				if(line.startsWith("#"))
					continue;
				stopEntList.add(line);
			}	
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public ArrayList<String> process(String question)
	{
		ArrayList<String> fixedQuestionList = new ArrayList<String>();
		ArrayList<Integer> literalList = new ArrayList<Integer>();
		HashMap<Integer, Double> entityScores = new HashMap<Integer, Double>();
		HashMap<Integer, Integer> entityMappings = new HashMap<Integer, Integer>();
		HashMap<Integer, Double> typeScores = new HashMap<Integer, Double>();
		HashMap<Integer, String> typeMappings = new HashMap<Integer, String>();
		HashMap<Integer, Double> mappingScores = new HashMap<Integer, Double>();
		ArrayList<Integer> mustSelectedList = new ArrayList<Integer>();
		
		System.out.println("--------- entity/type recognition start ---------");
		
		Word[] words = Globals.coreNLP.getTaggedWords(question);
		mWordList = new ArrayList<MergedWord>();
		
		long t1 = System.currentTimeMillis();
		int checkEntCnt = 0, checkTypeCnt = 0, hitEntCnt = 0, hitTypeCnt = 0, allCnt = 0;
		boolean needRemoveCommas = false;
		
		// Check entity & type
		// Notice, ascending order by length
		StringBuilder tmpOW = new StringBuilder();
		StringBuilder tmpBW = new StringBuilder();
		for(int len=1; len<=words.length; len++)
		{
			for(int st=0,ed=st+len; ed<=words.length; st++,ed++)
			{
				String originalWord = "", baseWord = "", allUpperWord = "";
				for(int j=st; j<ed; j++)
				{
					tmpOW.append(words[j].originalForm);
					tmpBW.append(words[j].baseForm);
					String tmp = words[j].originalForm;
					if(tmp.length()>0 && tmp.charAt(0) >='a' && tmp.charAt(0)<='z')
					{
						String pre = tmp.substring(0,1).toUpperCase();
						tmp = pre + tmp.substring(1);
					}
					allUpperWord += tmp;
					
					if(j < ed-1)
					{
						tmpOW.append("_");
						tmpBW.append("_");
					}
				}
				originalWord = tmpOW.toString();
				baseWord=tmpBW.toString();
				tmpOW.setLength(0);
				tmpBW.setLength(0);
				
				allCnt++;
/*
 * Filters to speed up and drop some bad cases.  
*/				
				boolean entOmit = false, typeOmit = false;
				int prep_cnt=0;
				
				// Upper words can pass filter. eg： "Melbourne , Florida"
				int UpperWordCnt = 0;
				for(int i=st;i<ed;i++)
					if((words[i].originalForm.charAt(0)>='A' && words[i].originalForm.charAt(0)<='Z') 
							|| ((words[i].posTag.equals(",") || words[i].originalForm.equals("'")) && i>st && i<ed-1))
						UpperWordCnt++;
				
				// Filters 
				if(UpperWordCnt<len || st==0)
				{
					if(st==0)
					{
						if(!words[st].posTag.startsWith("DT") && !words[st].posTag.startsWith("N"))
						{
							entOmit = true;
							typeOmit = true;
						}
					}
					else if(st>0)
					{
						Word formerWord = words[st-1];
						if(formerWord.baseForm.equals("as"))
							entOmit = true;
						if(formerWord.baseForm.equals("many"))
							entOmit = true;
						
						if(len == 1 && (formerWord.posTag.startsWith("POS") || formerWord.posTag.startsWith("PRP")))
							entOmit = true;
						if(ed<words.length)
						{
							Word nextWord = words[ed];
							if(formerWord.posTag.equals("DT") && nextWord.posTag.equals("IN"))
								entOmit = true;
						}
						boolean flag1=false, flag2=false;
						for(int i=0;i<=st;i++)
							if(words[i].posTag.equals("DT"))
								flag1 = true;
						for(int i=ed-1;i<words.length;i++)
							if(words[i].posTag.equals("IN"))
								flag2 = true;
						if(flag1 && flag2)
							entOmit = true;
					}
					if(ed < words.length)
					{
						Word nextWord = words[ed];
						if(nextWord.originalForm.charAt(0)>='A' && nextWord.originalForm.charAt(0)<='Z')
							entOmit = true;
					}
					
					for(int i=st;i<ed;i++)
					{
						if(words[i].posTag.startsWith("I"))
							prep_cnt++;
						
						for(String badTag: badTagListForEntAndType)
						{
							if(words[i].posTag.startsWith(badTag))
							{
								entOmit = true;
								typeOmit = true;
								break;
							}
						}
						if(words[i].posTag.startsWith("P") && (i!=ed-1 || len==1)){
							entOmit = true;
							typeOmit = true;
						}
						// First word
						if(i==st)
						{
							if(words[i].posTag.startsWith("I") || words[i].posTag.startsWith("EX") || words[i].posTag.startsWith("TO"))
							{
								entOmit = true;
								typeOmit = true;
							}
							if(words[i].posTag.startsWith("D") && len==2){
								entOmit = true;
								typeOmit = true;
							}
							if(words[i].baseForm.startsWith("list") || words[i].baseForm.startsWith("many"))
							{
								entOmit = true;
								typeOmit = true;
							}
							if(words[i].baseForm.equals("and"))
							{
								entOmit = true;
								typeOmit = true;
							}
						}
						// Last word.
						if(i==ed-1)
						{
							if(words[i].posTag.startsWith("I") || words[i].posTag.startsWith("D") || words[i].posTag.startsWith("TO"))
							{
								entOmit = true;
								typeOmit = true;
							}
							if(words[i].baseForm.equals("and"))
							{
								entOmit = true;
								typeOmit = true;
							}
						}
						// Single word.
						if(len==1)
						{
							if(!words[i].posTag.startsWith("N"))
							{
								entOmit = true;
								typeOmit = true;
							}
						}
					}
					// Too many preposition. 
					if(prep_cnt >= 3)
					{
						entOmit = true;
						typeOmit = true;
					}
				}
/*
 * Filter done.
*/
							
				// Search category | highest priority
				String category = null;
				if(af.pattern2category.containsKey(baseWord))
				{
					typeOmit = true;
					entOmit = true;
					category = af.pattern2category.get(baseWord);
				}
				
				// Search type
				int hitMethod = 0; // 1=dbo(baseWord), 2=dbo(originalWord), 3=yago|extend()
				ArrayList<TypeMapping> tmList = new ArrayList<TypeMapping>();
				if(!typeOmit)
				{
					System.out.println("Type Check:  "+originalWord);
					tmList = tr.getTypeIDsAndNamesByStr(baseWord);
					if(tmList == null || tmList.size() == 0)
					{
						tmList = tr.getTypeIDsAndNamesByStr(originalWord);
						if(tmList != null && tmList.size()>0)
							hitMethod = 2;
					}
					else
						hitMethod = 1;
					
					if(tmList == null || tmList.size() == 0)
					{
						tmList = tr.getExtendTypeByStr(allUpperWord);
						if(tmList != null && tmList.size() > 0)
						{
							preLog += "++++ Extend Type detect: "+baseWord+": "+" prefferd relaiton:"+tmList.get(0).prefferdRelation+"\n";
							hitMethod = 3;
						}
					}
				}
				
				// Search entity
				ArrayList<EntityMapping> emList = new ArrayList<EntityMapping>();
				if(!entOmit && !stopEntList.contains(baseWord))
				{
					System.out.println("Ent Check: "+originalWord);
					checkEntCnt++;
					emList = getEntityIDsAndNamesByStr(originalWord, (UpperWordCnt>=len-1 || len==1),len);
					if(emList == null || emList.size() == 0)
					{
						emList = getEntityIDsAndNamesByStr(baseWord, (UpperWordCnt>=len-1 || len==1), len);
					}
					if(emList!=null && emList.size()>10)
					{
						ArrayList<EntityMapping> tmpList = new ArrayList<EntityMapping>();
						for(int i=0;i<10;i++)
						{
							tmpList.add(emList.get(i));
						}
						emList = tmpList;
					}
				}
				
				MergedWord mWord = new MergedWord(st,ed,originalWord);
				
				// Add category
				if(category != null)
				{
					mWord.mayCategory = true;
					mWord.category = category;
					int key = st*(words.length+1) + ed;
					mustSelectedList.add(key);
				}
				
				// Add literal
				if(len==1 && checkLiteralWord(words[st]))
				{
					mWord.mayLiteral = true;
					int key = st*(words.length+1) + ed;
					literalList.add(key);
				}
				
				// Add type mappings
				if(tmList!=null && tmList.size()>0)
				{
					if(tmList.get(0).score < TypeAcceptedScore)
						typeOmit = true;

					String likelyType = tmList.get(0).typeName.toLowerCase();
					String candidateBase = baseWord.replace("_", ""), candidateOriginal = originalWord.replace("_", "").toLowerCase();
					if(!candidateBase.equals(likelyType) && hitMethod == 1)
						typeOmit = true;
					if(!candidateOriginal.equals(likelyType) && hitMethod == 2)
						typeOmit = true;
					
					if(!typeOmit)
					{
						mWord.mayType = true;
						mWord.tmList = tmList;
						
						int key = st*(words.length+1) + ed;
						typeMappings.put(key, tmList.get(0).typeName);
						typeScores.put(key, tmList.get(0).score);
					}
				}
				
				// Add entity mappings
				if(emList!=null && emList.size()>0)
				{
					if(emList.get(0).score < EntAcceptedScore)
						entOmit = true;
					
					else if(len > 2)
					{
						for(int key: entityMappings.keySet())
						{
							int ts=key/(words.length+1);
							if(ts == st+1 && ts <= ed)
							{
								if(words[st].posTag.startsWith("DT") && !(words[st].originalForm.charAt(0)>='A'&&words[st].originalForm.charAt(0)<='Z'))
								{
									entOmit = true;
								}
							}
						}
					}
					
					if(!entOmit)
					{
						mWord.mayEnt = true;
						mWord.emList = emList;
					
						int key = st*(words.length+1) + ed;
						entityMappings.put(key, emList.get(0).entityID);
						
						double score = emList.get(0).score;
						String likelyEnt = emList.get(0).entityName.toLowerCase().replace(" ", "_");
						String lowerOriginalWord = originalWord.toLowerCase();
						if(likelyEnt.equals(lowerOriginalWord))
							score *= len;
						if(len>1)
						{
							boolean[] flag = new boolean[words.length+1];
							ArrayList<Integer> needlessEntList = new ArrayList<Integer>();
							double tmpScore=0;
							for(int preKey: entityMappings.keySet())
							{
								if(preKey == key)
									continue;
								int te=preKey%(words.length+1),ts=preKey/(words.length+1);
								for(int i=ts;i<te;i++)
									flag[i] = true;
								if(st<=ts && ed>= te)
								{
									needlessEntList.add(preKey);
									tmpScore += entityScores.get(preKey);
								}
							}
							int hitCnt = 0;
							for(int i=st;i<ed;i++)
								if(flag[i])
									hitCnt++;
							if(hitCnt == len || ((double)hitCnt/(double)len > 0.6 && (double)UpperWordCnt/(double)len > 0.6) || UpperWordCnt == len || len>=4)
							{
								boolean commaTotalRight = true;
								if(originalWord.contains(","))
								{
									String candidateCompactString = originalWord.replace(",","").replace("_", "").toLowerCase();
									String likelyCompactEnt = likelyEnt.replace(",","").replace("_", "");
									if(!candidateCompactString.equals(likelyCompactEnt))
										commaTotalRight = false;
									else
									{
										mWord.name = mWord.name.replace("_,_","_");
										needRemoveCommas = true;
									}
								}
								if(commaTotalRight)
								{
									mustSelectedList.add(key);
									if(tmpScore>score)
										score = tmpScore+1;
									for(int preKey: needlessEntList)
									{
										entityMappings.remove(preKey);
										mustSelectedList.remove(Integer.valueOf(preKey));
									}
								}
							}
						}
						entityScores.put(key,score);
					}
				}
				
				if(mWord.mayCategory || mWord.mayEnt || mWord.mayType || mWord.mayLiteral)
					mWordList.add(mWord);
			}
		}
		
		/* Print all candidates (use fixed score).*/
		System.out.println("------- Result ------");
		for(MergedWord mWord: mWordList)
		{
			int key = mWord.st * (words.length+1) + mWord.ed;
			if(mWord.mayCategory)
			{
				System.out.println("Detect category mapping: "+mWord.name+": "+ mWord.category +" score: 100.0");
	        	preLog += "++++ Category detect: "+mWord.name+": "+mWord.category+" score: 100.0\n";
			}
			if(mWord.mayEnt)
			{
				System.out.println("Detect entity mapping: "+mWord.name+": [");
				for(EntityMapping em: mWord.emList)
					System.out.print(em.entityName + ", ");
				System.out.println("]");
	        	preLog += "++++ Entity detect: "+mWord.name+": "+mWord.emList.get(0).entityName+" score:"+entityScores.get(key)+"\n";
				hitEntCnt++;
			}
			if(mWord.mayType)
			{
				System.out.println("Detect type mapping: "+mWord.name+": [");
				for(TypeMapping tm: mWord.tmList)
					System.out.print(tm.typeName + ", ");
				System.out.println("]");
	    		preLog += "++++ Type detect: "+mWord.name+": "+mWord.tmList.get(0).typeName +" score:"+typeScores.get(key)+"\n";
				hitTypeCnt++;
			}
			if(mWord.mayLiteral)
			{
				System.out.println("Detect literal: "+mWord.name);
				preLog += "++++ Literal detect: "+mWord.name+"\n";
			}
		}
		
		/*
		 * Sort by score and remove duplicate.
		 */
		ByValueComparator bvc = new ByValueComparator(entityScores,words.length+1);
		List<Integer> keys = new ArrayList<Integer>(entityMappings.keySet());
        Collections.sort(keys, bvc);
        for(Integer key : keys)
        {
        	if(!mappingScores.containsKey(entityMappings.get(key)))
        		mappingScores.put(entityMappings.get(key), entityScores.get(key));
        	else
        		entityMappings.remove(key);
        }
        
        selectedList = new ArrayList<ArrayList<Integer>>();
        ArrayList<Integer> selected = new ArrayList<Integer>();
        
        selected.addAll(mustSelectedList);
        for(Integer key: typeMappings.keySet())
        {
        	int ed = key%(words.length+1), st = key/(words.length+1);
        	if(st+1 < ed)
        	{
        		boolean beCovered = false;
				for(int preKey: entityMappings.keySet())
				{
					int te=preKey%(words.length+1),ts=preKey/(words.length+1);
					if(ts <= st && te >= ed && ed-st < te-ts)
					{
						beCovered = true;
					}
				}
				
				if(!beCovered)
					selected.add(key);
        	}
        }
        
        // Conflict resolution
        ArrayList<Integer> noConflictSelected = new ArrayList<Integer>();
    	
		boolean[] flag = new boolean[words.length];
		ByLenComparator blc = new ByLenComparator(words.length+1);
		Collections.sort(selected,blc);
		  
		for(Integer key : selected) 
		{
			int ed = key%(words.length+1), st = (key-ed)/(words.length+1);
		  	boolean omit = false;
		  	for(int i=st;i<ed;i++)
		  	{
		  		if(flag[i])
		  		{
		  			omit = true;
		  			break;
		  		}
		  	}
		  	if(omit)
		  		continue;
		  	for(int i=st;i<ed;i++)
		  		flag[i]=true;
		  	noConflictSelected.add(key);
		}

		/**
		 * ADDED: Compound entity emList propagation.
		 *
		 * When a multi-word entity (e.g. hemp_fiber, span [5,7)) wins conflict
		 * resolution, Stanford CoreNLP will re-parse the merged question and split
		 * "hemp_fiber" back into tokens hemp + _ + fiber.  BuildQueryGraph then
		 * picks up the head noun "fiber" (span [6,7)), whose mWordList entry still
		 * carries F-02 (the single-word entity mapping) — ignoring Hemp_fiber_0.
		 *
		 * Fix: for every multi-word entity span selected in noConflictSelected,
		 * replace the emList of all strictly-contained sub-word mWordList entries
		 * with the compound's emList.  This way, however BuildQueryGraph reaches
		 * "fiber"'s MergedWord, it gets Hemp_fiber_0 as the top candidate.
		 */
		for (Integer compKey : noConflictSelected) {
			int compEd = compKey % (words.length + 1);
			int compSt = compKey / (words.length + 1);
			if (compEd - compSt <= 1) continue; // only multi-word spans

			// Find this compound's MergedWord and its emList
			ArrayList<EntityMapping> compEmList = null;
			for (MergedWord mw : mWordList) {
				if (mw.st == compSt && mw.ed == compEd && mw.mayEnt) {
					compEmList = mw.emList;
					break;
				}
			}
			if (compEmList == null || compEmList.isEmpty()) continue;

			System.out.println("Compound propagation: ["
					+ compSt + "," + compEd + ") "
					+ compEmList.get(0).entityName
					+ " → sub-words");

			// Overwrite every strictly-contained sub-word's emList
			for (MergedWord mw : mWordList) {
				if (mw.mayEnt
						&& mw.st >= compSt && mw.ed <= compEd
						&& !(mw.st == compSt && mw.ed == compEd)) {
					mw.emList = new ArrayList<>(compEmList);
					System.out.println("  Updated sub-word [" + mw.st + "," + mw.ed + ") "
							+ mw.name + " emList → " + compEmList.get(0).entityName);
				}
			}
		}
        
        // Scoring and ranking --> top-k decision
        dfs(keys,0,noConflictSelected,words.length+1);
        ArrayList<NodeSelectedWithScore> nodeSelectedWithScoreList = new ArrayList<NodeSelectedWithScore>();
        for(ArrayList<Integer> select: selectedList)
        {
        	double score = 0;
        	for(Integer key: select)
        	{
        		if(entityScores.containsKey(key))
        			score += entityScores.get(key);
        		if(typeScores.containsKey(key))
        			score += typeScores.get(key);
        	}
        	NodeSelectedWithScore tmp = new NodeSelectedWithScore(select, score);
        	nodeSelectedWithScoreList.add(tmp);
        }
        Collections.sort(nodeSelectedWithScoreList);
        
        // Replace
        int cnt = 0;
        for(int k=0; k<nodeSelectedWithScoreList.size(); k++)
        {
        	if(k >= nodeSelectedWithScoreList.size())
        		break;
        	selected = nodeSelectedWithScoreList.get(k).selected;
   
        	Collections.sort(selected);
	        int j = 0;
	        String res = question;
	        if(selected.size()>0)
	        {
		        res = words[0].originalForm;
		        int tmp = selected.get(j++), st = tmp/(words.length+1), ed = tmp%(words.length+1);
		        for(int i=1;i<words.length;i++)
		        {
		        	if(i>st && i<ed)
		        	{
		        		res = res+"_"+words[i].originalForm;
		        	}
		        	else
		        	{
		        		res = res+" "+words[i].originalForm;
		        	}
		        	if(i >= ed && j<selected.size())
		        	{
		        		tmp = selected.get(j++);
		        		st = tmp/(words.length+1);
		        		ed = tmp%(words.length+1);
		        	}
		        }
	        }
	        else
	        {
	        	res = words[0].originalForm;
		        for(int i=1;i<words.length;i++)
		        {
		        	res = res+" "+words[i].originalForm;
		        }
	        }
	        
	        boolean ok = true;
	        for(String str: fixedQuestionList)
	        	if(str.equals(res))
	        		ok = false;
	        if(!ok)
	        	continue;
	        
	        if(needRemoveCommas)
	        	res = res.replace("_,_","_");
	        
	        System.out.println("Merged: "+res);
	        preLog += "plan "+cnt+": "+res+"\n";
	        fixedQuestionList.add(res);
	        cnt++;
	        if(cnt >= 3)	// top-3
	        	break;
        }
        long t2 = System.currentTimeMillis();
        preLog += "Node Recognition time: "+ (t2-t1) + "ms\n";
		System.out.println("Total check time: "+ (t2-t1) + "ms");
		System.out.println("--------- pre entity/type recognition end ---------");
		
		return fixedQuestionList;
	}
	
	public void dfs(List<Integer> keys,int dep,ArrayList<Integer> selected,int size)
	{
		if(dep == keys.size())
		{
			ArrayList<Integer> tmpList = (ArrayList<Integer>) selected.clone();
			selectedList.add(tmpList);
		}
		else
		{
			dfs(keys,dep+1,selected,size);
			boolean conflict = false;
			for(int preKey: selected)
			{
				int curKey = keys.get(dep);
				int preEd = preKey%size, preSt = (preKey-preEd)/size;
				int curEd = curKey%size, curSt = (curKey-curEd)/size;
				if(!(preSt<preEd && preEd<=curSt && curSt<curEd) && !(curSt<curEd && curEd<=preSt && preSt<preEd))
					conflict = true;
			}
			if(!conflict)
			{
				selected.add(keys.get(dep));
				dfs(keys,dep+1,selected,size);
				selected.remove(keys.get(dep));
			}
		}
		
	}
	
	public ArrayList<EntityMapping> getEntityIDsAndNamesByStr(String entity, boolean useDblk, int len) 
	{	
		String n = entity;
		ArrayList<EntityMapping> ret= new ArrayList<EntityMapping>();
		
		//1. Lucene index
		ret.addAll(EntityFragment.getEntityMappingList(n));
		
		//2. DBpedia Lookup (some cases)
		if (useDblk) 
		{ 
			ret.addAll(Globals.dblk.getEntityMappings(n, null));
		}
		
		Collections.sort(ret);
		
		if (ret.size() > 0) return ret;
		else return null;
	}
	
	public int preferDBpediaLookupOrLucene(String entityName)
	{
		int cntUpperCase = 0;
		int cntSpace = 0;
		int cntPoint = 0;
		int length = entityName.length();
		for (int i=0; i<length; i++)
		{
			char c = entityName.charAt(i);
			if (c==' ')
				cntSpace++;
			else if (c=='.')
				cntPoint++;
			else if (c>='A' && c<='Z')
				cntUpperCase++;
		}
		
		if ((cntUpperCase>0 || cntPoint>0) && cntSpace<3)
			return 1;
		if (cntUpperCase == length)
			return 1;
		return 0;		
	}
	
	static class ByValueComparator implements Comparator<Integer> {
        HashMap<Integer, Double> base_map;
        int base_size;
        double eps = 1e-8;
        
        int dblcmp(double a,double b)
        {
        	if(a+eps < b)
        		return -1;
        	return b+eps<a ? 1:0;
        }
 
        public ByValueComparator(HashMap<Integer, Double> base_map, Integer size) {
            this.base_map = base_map;
            this.base_size = size;
        }
 
        public int compare(Integer arg0, Integer arg1) {
            if (!base_map.containsKey(arg0) || !base_map.containsKey(arg1)) {
                return 0;
            }
 
            if (dblcmp(base_map.get(arg0),base_map.get(arg1))<0) {
                return 1;
            } 
            else if (dblcmp(base_map.get(arg0),base_map.get(arg1))==0) 
            {
            	int len0 = (arg0%base_size)-arg0/base_size , len1 = (arg1%base_size)-arg1/base_size;
                if (len0 < len1) {
                    return 1;
                } else if (len0 == len1) {
                    return 0;
                } else {
                    return -1;
                }
            } 
            else {
                return -1;
            }
        }
    }
	
	static class ByLenComparator implements Comparator<Integer> {
        int base_size;
 
        public ByLenComparator(int size) {
            this.base_size = size;
        }
 
        public int compare(Integer arg0, Integer arg1) {
        	int len0 = (arg0%base_size)-arg0/base_size , len1 = (arg1%base_size)-arg1/base_size;
            if (len0 < len1) {
                return 1;
            } else if (len0 == len1) {
                return 0;
            } else {
                return -1;
            }
        }
    }
	 
	public boolean isDigit(char ch)
	{
		if(ch>='0' && ch<='9')
			return true;
		return false;
	}
	
	public boolean checkLiteralWord(Word word)
	{
		boolean ok = false;
		if(word.posTag.equals("CD"))
			ok = true;
		return ok;
	}
	
	public static void main (String[] args) 
	{
		Globals.init();
		EntityRecognition er = new EntityRecognition();
		try 
		{
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			while (true) 
			{	
				System.out.println("Please input the question: ");
				String question = br.readLine();
				
				er.process(question);
			}
	
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}