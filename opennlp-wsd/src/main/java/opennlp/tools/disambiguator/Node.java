package opennlp.tools.disambiguator;

import java.util.ArrayList;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.data.list.PointerTargetNode;
import net.sf.extjwnl.data.list.PointerTargetNodeList;



/**
 * Convenience class to access some features.
 */

public class Node {

  public Synset parent;
  public Synset synset;
  
  protected ArrayList<WordPOS> senseRelevantWords;

  public ArrayList<Synset> hypernyms = new ArrayList<Synset>();
  public ArrayList<Synset> hyponyms = new ArrayList<Synset>();
  public ArrayList<Synset> meronyms = new ArrayList<Synset>();
  public ArrayList<Synset> holonyms = new ArrayList<Synset>();
  
  public ArrayList<WordPOS> synonyms = new ArrayList<WordPOS>();
  
  
  public Node(Synset parent, Synset synSet, ArrayList<WordPOS> senseRelevantWords) {
	    this.parent = parent;
	    this.synset = synSet;
	    this.senseRelevantWords = senseRelevantWords;
	  }
	  
  public Node(Synset synSet, ArrayList<WordPOS> senseRelevantWords) {
		    this.synset = synSet;
		    this.senseRelevantWords = senseRelevantWords;
	    }
  
  
	public ArrayList<WordPOS> getSenseRelevantWords() {
		return senseRelevantWords;
	}

	public void setSenseRelevantWords(ArrayList<WordPOS> senseRelevantWords) {
		this.senseRelevantWords = senseRelevantWords;
	}
	  
  public String getSense() {
    return this.synset.getGloss().toString();
  }

  
  public void setHypernyms() {
  //  PointerUtils pointerUtils = PointerUtils.get();
    PointerTargetNodeList phypernyms = new PointerTargetNodeList();
    try {
      phypernyms = PointerUtils.getDirectHypernyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  hypernyms");
      e.printStackTrace();
    }

    for (int i = 0; i < phypernyms.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) phypernyms.get(i);
      this.hypernyms.add(ptn.getSynset());
    }

  }

  public void setMeronyms() {
    //PointerUtils pointerUtils = PointerUtils.getInstance();
    PointerTargetNodeList pmeronyms = new PointerTargetNodeList();
    try {
    	pmeronyms = PointerUtils.getMeronyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  meronyms");
      e.printStackTrace();
    }

    for (int i = 0; i < pmeronyms.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) pmeronyms.get(i);
      this.meronyms.add(ptn.getSynset());
    }
  }
  
  public void setHolonyms() {
	   // PointerUtils pointerUtils = PointerUtils.getInstance();
	    PointerTargetNodeList pholonyms = new PointerTargetNodeList();
	    try {
	    	pholonyms = PointerUtils.getHolonyms(this.synset);
	    } catch (JWNLException e) {
	      e.printStackTrace();
	    } catch (NullPointerException e) {
	      System.err.println("Error finding the  holonyms");
	      e.printStackTrace();
	    }

	    for (int i = 0; i < pholonyms.size(); i++) {
	      PointerTargetNode ptn = (PointerTargetNode) pholonyms.get(i);
	      this.holonyms.add(ptn.getSynset());
	    }

	  }
  
  public void setHyponyms() {
	  //  PointerUtils pointerUtils = PointerUtils.getInstance();
	    PointerTargetNodeList phyponyms = new PointerTargetNodeList();
	    try {
	      phyponyms = PointerUtils.getDirectHyponyms(this.synset);
	    } catch (JWNLException e) {
	      e.printStackTrace();
	    } catch (NullPointerException e) {
	      System.err.println("Error finding the  hyponyms");
	      e.printStackTrace();
	    }

	    for (int i = 0; i < phyponyms.size(); i++) {
	      PointerTargetNode ptn = (PointerTargetNode) phyponyms.get(i);
	      this.hyponyms.add(ptn.getSynset());
	    }
	  }
  
  public void setSynonyms()
  {
    for (Word word : synset.getWords())
      synonyms.add(new WordPOS(word.toString(),word.getPOS()));
  }
  
  public ArrayList<Synset> getHypernyms() {
	  return hypernyms;
  }
  
  public ArrayList<Synset> getHyponyms() {
	  return hyponyms;
  }
  
  public ArrayList<Synset> getMeronyms() {
	  return meronyms;
  }
  public ArrayList<Synset> getHolonyms() {
	  return holonyms;
  }

  public ArrayList<WordPOS> getSynonyms()
  {
    return synonyms;
  }

}
