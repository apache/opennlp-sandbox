package opennlp.tools.parse_thicket;

import java.util.ArrayList;
import java.util.List;

public class ParseTreeNode implements IGeneralizer<ParseTreeNode>{
	String word;
    // this is the POS tag of the token
    String pos; 
    // this is the NER label of the token
    String ne; 
    Integer id;
    //PhraseType 
    String phraseType;
    
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
		this.ne = ne;
		this.id = id;
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

