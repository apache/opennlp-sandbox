package opennlp.tools.disambiguator.ims;
import java.util.ArrayList;

import opennlp.tools.disambiguator.WordToDisambiguate;


public class WTDIMS extends WordToDisambiguate {
		
	protected String[] posOfSurroundingWords;
	protected String[] surroundingWords;
	protected ArrayList<String[]> localCollocations;
	
	
	
	/**
	 * Constructor
	 */
	public WTDIMS(String[] sentence, int word, int sense) {
		super(sentence, word, sense);
	}

	
	
	/**
	 * Getters and Setters
	 */
	
	
	public String[] getPosOfSurroundingWords() {
		return posOfSurroundingWords;
	}

	public void setPosOfSurroundingWords(String[] posOfSurroundingWords) {
		this.posOfSurroundingWords = posOfSurroundingWords;
	}
	

	public String[] getSurroundingWords() {
		return surroundingWords;
	}

	public void setSurroundingWords(String[] surroundingWords) {
		this.surroundingWords = surroundingWords;
	}

	
	public ArrayList<String[]> getLocalCollocations() {
		return localCollocations;
	}

	public void setLocalCollocations(ArrayList<String[]> localCollocations) {
		this.localCollocations = localCollocations;
	}
	
	
}
