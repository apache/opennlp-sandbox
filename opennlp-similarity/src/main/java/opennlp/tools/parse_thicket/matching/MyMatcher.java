package opennlp.tools.parse_thicket.matching;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.deeplearning4j.berkeley.Pair;

import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.TextProcessor;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;

import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Created by sanviswa on 10/29/16.
 */
public class MyMatcher {

    Matcher m = new Matcher();


    public static void main(String[] args) throws Exception
    {

        MyMatcher myMatcher = new MyMatcher();
        myMatcher.runTest(myMatcher.readFile());
    }

    public List<String> readFile() throws Exception
    {
     //   BufferedReader br = new BufferedReader(new FileReader(this.getClass().getResource("/fidelity.txt").getPath()));
        List<String> al = new ArrayList<String>();
     /*   String line = null;
        while ((line = br.readLine()) != null) {

            al.add(line);
        }
        br.close(); */
    	String content = FileUtils.readFileToString(new File("/Users/bgalitsky/Documents/relevance-based-on-parse-trees/fidelity.txt"));
        String[] als = content.split("\n");
    	al = Arrays.asList(als);
    	return al;
    }

    public void runTest(List<String> lst) throws Exception
    {
        System.out.println("Enter text: ");
        Scanner scanner = new Scanner(System.in);
        String queryStr = scanner.nextLine();
        if("quit".equals(queryStr))
        {
            return;
        }
        else
        {
            checkLinguisticScores(queryStr,lst);
            runTest(lst);
        }

    }

    public void checkLinguisticScores(String q, List<String> aList) throws Exception
    {   // convert query into list of tokens
    	List<String> queryTokens = TextProcessor.fastTokenize(q.toLowerCase(), false);
    	
    	List<String> shortListedClasses = new ArrayList<String>();
    	for (String ans: aList) {
    		// convert answer class into the list of tokens
    		List<String> classTokens = TextProcessor.fastTokenize(ans.toLowerCase(), false);
    		// do intersection of tokens
    		classTokens.retainAll(queryTokens);
    		int tokenScore = 0;
    		// count significant tokens / no stopwords
    		for(String word: classTokens){
    			if (word.length()>2 && StringUtils.isAlpha(word))
    				tokenScore++;
    		}
    		if (tokenScore>1)
    			 shortListedClasses.add(ans);
    	}
    	// do it again with lower thresh, if too few results
    	if (shortListedClasses.size()<5)
    		for (String ans: aList) {
        		List<String> classTokens = TextProcessor.fastTokenize(ans.toLowerCase(), false);
        		classTokens.retainAll(queryTokens);
        		int tokenScore = 0;
        		for(String word: classTokens){
        			if (word.length()>2 && StringUtils.isAlpha(word))
        				tokenScore++;
        		}
        		if (tokenScore>=1)
        			 shortListedClasses.add(ans);
        	}
    	// if no overlap give up of do the full list 
    	if (shortListedClasses.isEmpty())
    		shortListedClasses = aList;
    		
        ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();

        ArrayList<Pair<String,Double>> pairList = new ArrayList<Pair<String,Double>>();

        for (String ans: shortListedClasses) {

            List<List<ParseTreeChunk>> res = m.assessRelevanceCache(q, ans);
            double score1 = parseTreeChunkListScorer.getParseTreeChunkListScoreAggregPhraseType(res);
            Pair<String,Double> p = new Pair<String, Double>(ans, score1);
            pairList.add(p);
        }

        Collections.sort(pairList, Comparator.comparing(p -> p.getSecond()));

      System.out.println("***** '" + q + "' ******* falls into the following categories: ");
        for (Pair<String, Double> score: pairList) {
            System.out.println("        " + score.getFirst() + ": " + score.getSecond());
        }


    }
}