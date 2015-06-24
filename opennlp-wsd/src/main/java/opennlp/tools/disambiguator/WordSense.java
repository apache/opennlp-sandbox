package opennlp.tools.disambiguator;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import opennlp.tools.disambiguator.lesk.WTDLesk;

public class WordSense implements Comparable{ 
	
	protected WTDLesk WTDLesk;
	protected Node node;
	protected int id;
	protected double score;
	
	
	public WordSense(WTDLesk WTDLesk, Node node) {
		super();
		this.WTDLesk = WTDLesk;
		this.node = node;
	}

	public WordSense() {
		super();
	}

	
	public WTDLesk getWTDLesk() {
		return WTDLesk;
	}

	public void setWTDLesk(WTDLesk WTDLesk) {
		this.WTDLesk = WTDLesk;
	}

	
	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	
	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}


	public int compareTo(Object o) {
		return (this.score-((WordSense)o).score)<0?1:-1;
	}
	
	
	public String getSense() {
		return node.getSense();
	}
	


}


