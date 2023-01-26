package opennlp.tools.parse_thicket.communicative_actions;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.parse_thicket.Pair;
import opennlp.tools.parse_thicket.ParseTreeNode;

public class CommunicativeActionsArcBuilder implements IGeneralizer<Pair<String, Integer[]>> {

	private final List<Pair<String, Integer[]>> commActionsAttr = new ArrayList<>();
	
	public CommunicativeActionsArcBuilder(){

		commActionsAttr.add(new Pair<>("agree", new Integer[]{	1,	-1,	-1,	1,	-1}));
		commActionsAttr.add(new Pair<>("accept", new Integer[]{	1,	-1,	-1,	1,	1}));
		commActionsAttr.add(new Pair<>("explain", new Integer[]{	0,	-1,	1,	1,	-1}));

		commActionsAttr.add(new Pair<>("suggest", new Integer[]{	1,	0,	1,	-1,	-1}));
		commActionsAttr.add(new Pair<>("claim", new Integer[]{	1,	0,	1,	-1,	-1}));

		// bring-attention
		commActionsAttr.add(new Pair<>("bring_attention", new Integer[]{	1,	1,	1,	1,	1}));
		commActionsAttr.add(new Pair<>("remind", new Integer[]{	-1,	0,	1,	1,	1}));
		commActionsAttr.add(new Pair<>("allow", new Integer[]{	1,	-1,	-1,	-1,	-1}));
		commActionsAttr.add(new Pair<>("try", new Integer[]{	1,	0,	-1,	-1,	-1}));
		commActionsAttr.add(new Pair<>("request", new Integer[]{	0,	1,	-1,	1,	1}));
		commActionsAttr.add(new Pair<>("understand", new Integer[]{	0,	-1,	-1,	1,	-1}));

		commActionsAttr.add(new Pair<>("inform", new Integer[]{	0,	0,	1,	1,	-1}));
		commActionsAttr.add(new Pair<>("notify", new Integer[]{	0,	0,	1,	1,	-1}));
		commActionsAttr.add(new Pair<>("report", new Integer[]{	0,	0,	1,	1,	-1}));


		commActionsAttr.add(new Pair<>("confirm", new Integer[]{	0,	-1,	1,	1,	1}));
		commActionsAttr.add(new Pair<>("ask", new Integer[]{	0,	1,	-1,	-1,	-1}));
		commActionsAttr.add(new Pair<>("check", new Integer[]{	-1,	1,	-1,	-1,	1}));

		commActionsAttr.add(new Pair<>("ignore", new Integer[]{	-1,	-1,	-1,	-1,	1}));
		commActionsAttr.add(new Pair<>("wait", new Integer[]{	-1,	-1,	-1,	-1,	1}));

		commActionsAttr.add(new Pair<>("convince", new Integer[]{	0,	1,	1,	1, -1}));
		commActionsAttr.add(new Pair<>("disagree", new Integer[]{	-1,	-1,	-1,	1,	-1}));
		commActionsAttr.add(new Pair<>("appeal", new Integer[]{	-1,	1,	1,	1,	1}));
		commActionsAttr.add(new Pair<>("deny", new Integer[]{	-1,	-1,	-1,	1,	1}));
		commActionsAttr.add(new Pair<>("threaten", new Integer[]{	-1,	1, -1,	1,	1}));

		commActionsAttr.add(new Pair<>("concern", new Integer[]{	1,	-1, -1,	1,	1}));
		commActionsAttr.add(new Pair<>("afraid", new Integer[]{	1,	-1, -1,	1,	1}));
		commActionsAttr.add(new Pair<>("worri", new Integer[]{	1,	-1, -1,	1,	1}));
		commActionsAttr.add(new Pair<>("scare", new Integer[]{	1,	-1, -1,	1,	1}));

		commActionsAttr.add(new Pair<>("want", new Integer[]{	1,	0,	-1,	-1,	-1}));
		commActionsAttr.add(new Pair<>("know", new Integer[]{	0,	-1,	-1,	1,	-1}));
		commActionsAttr.add(new Pair<>("believe", new Integer[]{	0,	-1,	-1,	1,	-1}));
	}

	public Pair<String, Integer[]> findCAInSentence(List<ParseTreeNode> sentence){
		for(ParseTreeNode node: sentence){
			for(Pair<String, Integer[]> ca: commActionsAttr){
				String lemma = ca.getFirst();
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
				String lemma = ca.getFirst();
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


	@Override
	public List<Pair<String, Integer[]>> generalize(Object o1, Object o2) {
		List<Pair<String, Integer[]>> results = new ArrayList<>();


		String ca1, ca2;

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
			String lemma = ca.getFirst();
			if (lemma.equals(ca1)){
				caP1=ca;
				break;
			}					
		}

		// find entry for ca2
		for(Pair<String, Integer[]> ca: commActionsAttr){
			String lemma = ca.getFirst();
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
			results.add(new Pair<>("", res ));
		}

		return results;
	}


	/*Pair<String, Integer[]>[] commActionsAttrAr = new Pair<>[] {
			new Pair<>("agree", new Integer[]{	1,	-1,	-1,	1,	-1}),
			new Pair<>("accept", new Integer[]{	1,	-1,	-1,	1,	1}),
			new Pair<>("explain", new Integer[]{	0,	-1,	1,	1,	-1}),
			new Pair<>("suggest", new Integer[]{	1,	0,	1,	-1,	-1}),
			new Pair<>("bring attention", new Integer[]{	1,	1,	1,	1,	1}),
			new Pair<>("remind", new Integer[]{	-1,	0,	1,	1,	1}),
		    new Pair<>("allow", new Integer[]{	1,	-1,	-1,	-1,	-1}),
			new Pair<>("try", new Integer[]{	1,	0,	-1,	-1,	-1}),
			new Pair<>("request", new Integer[]{	0,	1,	-1,	1,	1}),
			new Pair<>("understand", new Integer[]{	0,	-1,	-1,	1,	-1}),
			new Pair<>("inform", new Integer[]{	0,	0,	1,	1,	-1}),
			new Pair<>("confirm", new Integer[]{	0,	-1,	1,	1,	1}),
			new Pair<>("ask", new Integer[]{	0,	1,	-1,	-1,	-1}),
			new Pair<>("check", new Integer[]{	-1,	1,	-1,	-1,	1}),
			new Pair<>("ignore", new Integer[]{	-1,	-1,	-1,	-1,	1}),
			new Pair<>("convince", new Integer[]{	0,	1,	1,	1, -1}),
			new Pair<>("disagree", new Integer[]{	-1,	-1,	-1,	1,	-1}),
			new Pair<>("appeal", new Integer[]{	-1,	1,	1,	1,	1}),
			new Pair<>("deny", new Integer[]{	-1,	-1,	-1,	1,	1}),
			new Pair<>("threaten", new Integer[]{	-1,	1, -1,	1,	1}),
	} */

}
