package opennlp.tools.parse_thicket.communicative_actions;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.parse_thicket.Pair;
import opennlp.tools.parse_thicket.ParseTreeNode;


public class CommunicativeActionsArcBuilder implements IGeneralizer<Pair<String, Integer[]>>{

	private List<Pair<String, Integer[]>> commActionsAttr = new ArrayList<Pair<String, Integer[]>>();
	public CommunicativeActionsArcBuilder(){

		commActionsAttr.add(new Pair<String, Integer[]>("agree", new Integer[]{	1,	-1,	-1,	1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("accept", new Integer[]{	1,	-1,	-1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("explain", new Integer[]{	0,	-1,	1,	1,	-1}));

		commActionsAttr.add(new Pair<String, Integer[]>("suggest", new Integer[]{	1,	0,	1,	-1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("claim", new Integer[]{	1,	0,	1,	-1,	-1}));

		// bring-attention
		commActionsAttr.add(new Pair<String, Integer[]>("bring_attention", new Integer[]{	1,	1,	1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("remind", new Integer[]{	-1,	0,	1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("allow", new Integer[]{	1,	-1,	-1,	-1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("try", new Integer[]{	1,	0,	-1,	-1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("request", new Integer[]{	0,	1,	-1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("understand", new Integer[]{	0,	-1,	-1,	1,	-1}));

		commActionsAttr.add(new Pair<String, Integer[]>("inform", new Integer[]{	0,	0,	1,	1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("notify", new Integer[]{	0,	0,	1,	1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("report", new Integer[]{	0,	0,	1,	1,	-1}));


		commActionsAttr.add(new Pair<String, Integer[]>("confirm", new Integer[]{	0,	-1,	1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("ask", new Integer[]{	0,	1,	-1,	-1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("check", new Integer[]{	-1,	1,	-1,	-1,	1}));

		commActionsAttr.add(new Pair<String, Integer[]>("ignore", new Integer[]{	-1,	-1,	-1,	-1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("wait", new Integer[]{	-1,	-1,	-1,	-1,	1}));

		commActionsAttr.add(new Pair<String, Integer[]>("convince", new Integer[]{	0,	1,	1,	1, -1}));
		commActionsAttr.add(new Pair<String, Integer[]>("disagree", new Integer[]{	-1,	-1,	-1,	1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("appeal", new Integer[]{	-1,	1,	1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("deny", new Integer[]{	-1,	-1,	-1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("threaten", new Integer[]{	-1,	1, -1,	1,	1}));

		commActionsAttr.add(new Pair<String, Integer[]>("concern", new Integer[]{	1,	-1, -1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("afraid", new Integer[]{	1,	-1, -1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("worri", new Integer[]{	1,	-1, -1,	1,	1}));
		commActionsAttr.add(new Pair<String, Integer[]>("scare", new Integer[]{	1,	-1, -1,	1,	1}));

		commActionsAttr.add(new Pair<String, Integer[]>("want", new Integer[]{	1,	0,	-1,	-1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("know", new Integer[]{	0,	-1,	-1,	1,	-1}));
		commActionsAttr.add(new Pair<String, Integer[]>("believe", new Integer[]{	0,	-1,	-1,	1,	-1}));
	}

	public Pair<String, Integer[]> findCAInSentence(List<ParseTreeNode> sentence){
		for(ParseTreeNode node: sentence){
			for(Pair<String, Integer[]> ca: commActionsAttr){
				String lemma = (String)ca.getFirst();
				// canonical form lemma is a sub-string of an actual form in parseTreeNode
				if (node.getWord().toLowerCase().startsWith(lemma))
					return ca;
			}
		}
		return null;
	}

	public int findCAIndexInSentence(List<ParseTreeNode> sentence){
		for(int index = 1; index< sentence.size(); index++){
			ParseTreeNode node = sentence.get(index);
			for(Pair<String, Integer[]> ca: commActionsAttr){
				String lemma = (String)ca.getFirst();
				String[] lemmas = lemma.split("_");
				if (lemmas==null || lemmas.length<2){
					if (node.getWord().toLowerCase().startsWith(lemma))
						return index;
				} else { //multiword matching 
					for(int indexM= index+1; indexM<sentence.size(); indexM++);//
				}
				
			}
		}
		return -1;
	}


	public List<Pair<String, Integer[]>> generalize(Object o1, Object o2) {
		List<Pair<String, Integer[]>> results = new ArrayList<Pair<String, Integer[]>>();


		String ca1 = null, ca2=null;

		if (o1 instanceof String){
			ca1 = (String)o1;
			ca2 = (String)o2;
		} else {			
			ca1 = ((Pair<String, Integer[]>)o1).getFirst();
			ca2 = ((Pair<String, Integer[]>)o2).getFirst();
		}


		// find entry for ca1
		Pair<String, Integer[]> caP1=null, caP2=null;
		for(Pair<String, Integer[]> ca: commActionsAttr){
			String lemma = (String)ca.getFirst();
			if (lemma.equals(ca1)){
				caP1=ca;
				break;
			}					
		}

		// find entry for ca2
		for(Pair<String, Integer[]> ca: commActionsAttr){
			String lemma = (String)ca.getFirst();
			if (lemma.equals(ca2)){
				caP2=ca;
				break;
			}					
		}

		if (ca1.equals(ca2)){
			results.add(caP1);
		} else {
			// generalization of int arrays also implements IGeneralizer
			// we take Integer[] which is a first element of as resultant list
			Integer[] res = new CommunicativeActionsAttribute().
					generalize(caP1.getSecond(), caP2.getSecond()).get(0);
			results.add(new Pair<String, Integer[]>("", res ));
		}

		return results;
	}




	/*Pair<String, Integer[]>[] commActionsAttrAr = new Pair<String, Integer[]>[] {
			new Pair<String, Integer[]>("agree", new Integer[]{	1,	-1,	-1,	1,	-1}),
			new Pair<String, Integer[]>("accept", new Integer[]{	1,	-1,	-1,	1,	1}),
			new Pair<String, Integer[]>("explain", new Integer[]{	0,	-1,	1,	1,	-1}),
			new Pair<String, Integer[]>("suggest", new Integer[]{	1,	0,	1,	-1,	-1}),
			new Pair<String, Integer[]>("bring attention", new Integer[]{	1,	1,	1,	1,	1}),
			new Pair<String, Integer[]>("remind", new Integer[]{	-1,	0,	1,	1,	1}),
		    new Pair<String, Integer[]>("allow", new Integer[]{	1,	-1,	-1,	-1,	-1}),
			new Pair<String, Integer[]>("try", new Integer[]{	1,	0,	-1,	-1,	-1}),
			new Pair<String, Integer[]>("request", new Integer[]{	0,	1,	-1,	1,	1}),
			new Pair<String, Integer[]>("understand", new Integer[]{	0,	-1,	-1,	1,	-1}),
			new Pair<String, Integer[]>("inform", new Integer[]{	0,	0,	1,	1,	-1}),
			new Pair<String, Integer[]>("confirm", new Integer[]{	0,	-1,	1,	1,	1}),
			new Pair<String, Integer[]>("ask", new Integer[]{	0,	1,	-1,	-1,	-1}),
			new Pair<String, Integer[]>("check", new Integer[]{	-1,	1,	-1,	-1,	1}),
			new Pair<String, Integer[]>("ignore", new Integer[]{	-1,	-1,	-1,	-1,	1}),
			new Pair<String, Integer[]>("convince", new Integer[]{	0,	1,	1,	1, -1}),
			new Pair<String, Integer[]>("disagree", new Integer[]{	-1,	-1,	-1,	1,	-1}),
			new Pair<String, Integer[]>("appeal", new Integer[]{	-1,	1,	1,	1,	1}),
			new Pair<String, Integer[]>("deny", new Integer[]{	-1,	-1,	-1,	1,	1}),
			new Pair<String, Integer[]>("threaten", new Integer[]{	-1,	1, -1,	1,	1}),	
	} */

}
