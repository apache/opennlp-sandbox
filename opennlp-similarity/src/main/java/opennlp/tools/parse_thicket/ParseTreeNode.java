package opennlp.tools.parse_thicket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParseTreeNode implements IGeneralizer<ParseTreeNode>{
	String word; // word in normal form, lemma
	// this is the POS tag of the token
	String pos; 
	// this is the NER label of the token
	String ne; 
	Integer id;
	//PhraseType 
	String phraseType;
	Map<String, Object> attributes;
	String normalizedWord;
	String syntacticDependence;
	String originalWord; //what actually occurs in a sentence

	String head;
	String label;
	String modifier;



	public String getOriginalWord() {
		return originalWord;
	}

	public void setOriginalWord(String originalWord) {
		this.originalWord = originalWord;
	}

	public String getHead() {
		return head;
	}

	public void setHead(String head) {
		this.head = head;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getModifier() {
		return modifier;
	}

	public void setModifier(String modifier) {
		this.modifier = modifier;
	}

	public String getNormalizedWord() {
		return normalizedWord;
	}

	public void setNormalizedWord(String normalizedWord) {
		this.normalizedWord = normalizedWord;
	}

	public String getSyntacticDependence() {
		return syntacticDependence;
	}

	public void setSyntacticDependence(String syntacticDependence) {
		this.syntacticDependence = syntacticDependence;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	public enum PhraseType {NP("NP"), VP("VP"), PRP("PRP");
	private PhraseType(final String text) {
		this.text = text;
	}
	private final String text;

	}

	public ParseTreeNode(String word, String pos, String ne, Integer id) {
		super();
		this.word = word;
		this.pos = pos;
		this.ne = ne;
		this.id = id;
	}

	public ParseTreeNode(String word, String pos) {
		super();
		this.word = word;
		this.pos = pos;

	}

	public String getPhraseType() {
		return phraseType;
	}
	public void setPhraseType(String pt) {
		this.phraseType=pt;
	}
	public String getWord() {
		return word;
	}
	public void setWord(String word) {
		this.word = word;
	}
	public String getPos() {
		return pos;
	}
	public void setPos(String pos) {
		this.pos = pos;
	}
	public String getNe() {
		return ne;
	}
	public void setNe(String ne) {
		this.ne = ne;
	}
	public Integer getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	} 

	public String toString(){
		StringBuffer buf = new StringBuffer();
		if (id!=null)
			buf.append("<"+id+">");
		if(phraseType!=null)
			buf.append(phraseType);
		if(word!=null)
			buf.append("'"+word+"'");
		if (pos!=null)
			buf.append(":"+pos);
		return buf.toString();
	}

	public static String toTreeRepresentationString(List<ParseTreeNode> chList){
		StringBuffer buf = new StringBuffer();
		for(ParseTreeNode ch: chList){
			if (ch.getPos().startsWith(".") || ch.getPos().startsWith(",") || ch.getPos().startsWith(";") || ch.getPos().startsWith("!"))
				continue;
			buf.append( "("+ch.getWord()+ " " + ch.getPos() + ")" );
		}
		return buf.toString().trim();
	}
	public static String toWordString(List<ParseTreeNode> chList){
		String buf = "";
		for(ParseTreeNode ch: chList){
			buf+=ch.getWord()+ " ";
		}
		return buf.trim();
	}

	@Override
	public List<ParseTreeNode> generalize(Object o1, Object o2) {
		List<ParseTreeNode> result = new ArrayList<ParseTreeNode>();

		ParseTreeNode w1 = (ParseTreeNode) o1;
		ParseTreeNode w2 = (ParseTreeNode) o2;
		String posGen =  generalizePOS(w1.pos, w2.pos);
		if (posGen ==null)
			return result;
		ParseTreeNode newNode = new ParseTreeNode(generalizeWord(w1.word, w2.word),
				posGen, "O", -1);
		result.add(newNode);
		return result;
	}

	public String generalizeWord(String lemma1, String lemma2){
		if (lemma1.equals(lemma2))
			return lemma1;
		if (lemma1.equals("*"))
			return "*";
		if (lemma2.equals("*"))
			return "*";
		//TODO
		return "*";

	}

	public String generalizePOS(String pos1, String pos2) {
		if ((pos1.startsWith("NN") && pos2.equals("NP") || pos2.startsWith("NN")
				&& pos1.equals("NP"))) {
			return "NN";
		}
		if ((pos1.startsWith("NN") && pos2.equals("VBG") || pos2.startsWith("VBG")
				&& pos1.equals("NN"))) {
			return "NN";
		}

		if ((pos1.startsWith("NN") && pos2.equals("ADJP") || pos2.startsWith("NN")
				&& pos1.equals("ADJP"))) {
			return "NN";
		}
		if ((pos1.equals("IN") && pos2.equals("TO") || pos1.equals("TO")
				&& pos2.equals("IN"))) {
			return "IN";
		}
		// VBx vs VBx = VB (does not matter which form for verb)
		if (pos1.startsWith("VB") && pos2.startsWith("VB")) {
			return "VB";
		}

		// ABx vs ABy always gives AB
		if (pos1.equalsIgnoreCase(pos2)) {
			return pos1;
		}
		if (pos1.length() > 2) {
			pos1 = pos1.substring(0, 2);
		}

		if (pos2.length() > 2) {
			pos2 = pos2.substring(0, 2);
		}
		if (pos1.equalsIgnoreCase(pos2)) {
			return pos1 + "*";
		}
		return null;
	}


};

