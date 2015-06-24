package opennlp.tools.disambiguator.ims;

import java.util.ArrayList;

import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.util.Span;

public class IMS implements WSDisambiguator{
	
	FeaturesExtractor fExtractor = new FeaturesExtractor();
	
	/**
	 * PARAMETERS
	 */
	
	int numberOfSurroundingWords;
	int ngram;
	
	
	
	/**
	 * Constructors
	 */
	
	public IMS() {
		super();
		numberOfSurroundingWords = 3;
		ngram = 2;
	}
	
	public IMS(int numberOfSurroundingWords, int ngram) {
		super();
		this.numberOfSurroundingWords = numberOfSurroundingWords;
		this.ngram = ngram;
	}
	
	
	
	/**
	 * INTERNAL METHODS
	 */
	
	private void extractFeature(ArrayList<WTDIMS> words) {
		
		for (WTDIMS word : words) {
			
			word.setPosOfSurroundingWords(fExtractor.extractPosOfSurroundingWords(word.getSentence(), word.getWordIndex(), numberOfSurroundingWords));
			
			word.setSurroundingWords(fExtractor.extractSurroundingWords(word.getSentence(), word.getWordIndex()));
			
			word.setLocalCollocations(fExtractor.extractLocalCollocations(word.getSentence(), word.getWordIndex(), ngram));
			
		}

	}
	
	private ArrayList<WTDIMS> extractTrainingData(String xmlFile) {
		
		ArrayList<WTDIMS> trainingData = new ArrayList<WTDIMS>();
		
		/**
		 * TODO Processing of the xml File here (To check the format of the data)
		 */
		
		return trainingData;
	}
	
	
	public void train(String trainingSetFile) { // TODO To revise after finihsing the implementation of the collector
		
		ArrayList<WTDIMS> instances = extractTrainingData(trainingSetFile);
		
		extractFeature(instances);
		
		
		
	}
	
	
	public void load (String binFile) {
		// TODO After finishing training the training data
				
	}
	

	@Override
	public String[] disambiguate(String[] inputText, int inputWordIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] disambiguate(String[] inputText, Span[] inputWordSpans) {
		// TODO Auto-generated method stub
		return null;
	}
	

}
