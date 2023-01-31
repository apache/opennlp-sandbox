package opennlp.tools.apps.review_builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.textsimilarity.ParseTreeChunk;

public class SentenceBeingOriginalized {
	private Map<String, String> sentKey_value= new HashMap<String, String>();
	private String sentence;
	private List<List<ParseTreeChunk>> groupedChunks;
	
	
	
	public Map<String, String> getSentKey_value() {
		return sentKey_value;
	}



	public void setSentKey_value(Map<String, String> sentKey_value) {
		this.sentKey_value = sentKey_value;
	}



	public String getSentence() {
		return sentence;
	}



	public void setSentence(String sentence) {
		this.sentence = sentence;
	}



	public List<List<ParseTreeChunk>> getGroupedChunks() {
		return groupedChunks;
	}



	public void setGroupedChunks(List<List<ParseTreeChunk>> groupedChunks) {
		this.groupedChunks = groupedChunks;
	}



	public SentenceBeingOriginalized(Map<String, String> sentKey_value,
			String sentence, List<List<ParseTreeChunk>> groupedChunks) {
		super();
		this.sentKey_value = sentKey_value;
		this.sentence = sentence;
		this.groupedChunks = groupedChunks;
	}
}
