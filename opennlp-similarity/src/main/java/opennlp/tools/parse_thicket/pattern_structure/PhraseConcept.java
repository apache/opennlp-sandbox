package opennlp.tools.parse_thicket.pattern_structure;

import java.util.*;
import java.io.*;

import opennlp.tools.textsimilarity.ParseTreeChunk;

public class PhraseConcept {
	int position;
	//Set<Integer> intent;
	List<List<ParseTreeChunk>> intent;
	Set<Integer> parents;
	public PhraseConcept() {
		position = -1;
		intent = new ArrayList<List<ParseTreeChunk>>();
		parents = new HashSet<Integer>();
	}
	public void setPosition( int newPosition ){
	       position = newPosition;
	}
	public void setIntent( List<List<ParseTreeChunk>> newIntent ){
	       intent.clear();
	       intent.addAll(newIntent);
	}
	public void setParents( Set<Integer> newParents ){
	       //parents = newParents;
		parents.clear();
		parents.addAll(newParents);
	}
	public void printConcept() {
		System.out.println("Concept position:" + position);
		System.out.println("Concept intent:" + intent);
		System.out.println("Concept parents:" + parents);
	}
	 public static void main(String []args) {
		 PhraseConcept c = new PhraseConcept();
		 c.printConcept();
		 c.setPosition(10);
		 c.printConcept();
		 //List<List<ParseTreeChunk>> test = new List<List<ParseTreeChunk>>();
		 //c.setIntent(test);
		 c.printConcept();

	 }
}