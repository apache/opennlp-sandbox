package opennlp.tools.parse_thicket.opinion_processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;

public class PersonExtractor extends NamedEntityExtractor {
	private boolean isNERforPhraseExtraction(ParseTreeNode word){
		if ((word.getNe().equals("PERSON") ) &&
				(word.getPos().startsWith("NN") || word.getPos().startsWith("PR") || word.getPos().startsWith("IN")|| 
						word.getPos().startsWith("JJ") || word.getPos().startsWith("DT")  ))
			return true;

		return false;

	}
	
	public EntityExtractionResult extractEntities(String para){
		List<List<ParseTreeNode>> extractedNERs = new ArrayList<List<ParseTreeNode>>();
		List<String> extractedNERsWords = new ArrayList<String>();
		List<List<ParseTreeNode>> extractedSentimentPhrases = 
				new ArrayList<List<ParseTreeNode>>();
		EntityExtractionResult result = new EntityExtractionResult();

		ParseThicket pt = null;

		System.out.println("Processing paragraph of length "+para.length() + " | "+ para);
		pt = matcher.buildParseThicketFromTextWithRST(para);
		List<List<ParseTreeNode>> nodeList = pt.getSentenceNodes();


		for(List<ParseTreeNode> sentence: nodeList){
			System.out.println("   Processing sentence: "+ sentence);
			boolean bInsideNER = false; 
			String currentPhrase = "";
			List<ParseTreeNode> currentPhraseNode = new ArrayList<ParseTreeNode>(); 
			for(ParseTreeNode word: sentence){
				if (isNERforPhraseExtraction(word)){
					System.out.println("++Found word ="+word + " | NER="+ word.getNe());
					if (bInsideNER){
						currentPhrase += " "+word.getWord();
						currentPhraseNode.add(word);
					} else {
						bInsideNER=true;
						currentPhrase = word.getWord();
						currentPhraseNode.add(word);
					}
				} else {
					if (bInsideNER){
						if (currentPhrase.indexOf(' ')>-1) // at least two tokens
							extractedNERsWords.add(currentPhrase);
							extractedNERs.add(currentPhraseNode);
						currentPhrase = "";
						bInsideNER=false;
					} else {
						// do nothing, continue scan
					}
				}
			}
			if (currentPhrase.length()>1 && currentPhrase.indexOf(' ')>-1){
				extractedNERs.add(currentPhraseNode);
				extractedNERsWords.add(currentPhrase);
			}

			Set<String> foundSentimentWords = new HashSet<String>();
			// now we extract phrases
			List<List<ParseTreeNode>> phrases = phraseBuilder.buildPT2ptPhrases(pt);
			for(List<ParseTreeNode> phrase: phrases){
				// find a noun phrase under sentiment
				try {
					for(int i = phrase.size()-1; i>-1; i--){
						ParseTreeNode word = phrase.get(i);
						if ((isSentimentWord(word.getWord()) ||
								sVocab.isSentimentWord(word.getWord()) && !foundSentimentWords.contains(word.getWord()) )){
							foundSentimentWords.add(word.getWord());
							System.out.println("Found opinionated phrase "+phrase.toString());
							extractedSentimentPhrases.add(phrase);			
							break;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		} 
		result.setExtractedNER(extractedNERs);
		result.setExtractedNERWords(extractedNERsWords);
		result.setExtractedSentimentPhrases(extractedSentimentPhrases);
		return result;
	}
}
