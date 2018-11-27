package opennlp.tools.parse_thicket.matching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.VerbNetProcessor;

public class ParseTreeNodeGeneralizer implements IGeneralizer<ParseTreeNode>{
	private LemmaGeneralizer lGen = new LemmaGeneralizer ();
	private PartOfSpeechGeneralizer posGen = new PartOfSpeechGeneralizer ();
	private VerbNetProcessor vnProc = VerbNetProcessor.getInstance(null);

	@Override
	public List<ParseTreeNode> generalize(Object o1, Object o2) {

		List<ParseTreeNode> results = new ArrayList<ParseTreeNode>();

		ParseTreeNode newNode = null;
		ParseTreeNode ch1 = (ParseTreeNode)o1, ch2 = (ParseTreeNode)o2;
		List<String> posGenStrList =  posGen.generalize(ch1.getPos(), ch2.getPos());
		if (!posGenStrList.isEmpty()){
			List<String> lemmaGen = lGen.generalize(ch1.getWord(), ch2.getWord());
			if (!lemmaGen.isEmpty())
				newNode = new ParseTreeNode(lemmaGen.get(0),  posGenStrList.get(0), "O", -1);
			else
				newNode = new ParseTreeNode("*",  posGenStrList.get(0), "O", -1);
		}
		
		newNode.setPhraseType(ch1.getPhraseType());
		//TODO separate NER generalizer
		//TODO multiword generalizer
		if (posGenStrList.get(0).startsWith("NN")){
			if (ch1.getNe()!=null && ch2.getNe()!=null && ch1.getNe().equals(ch2.getNe()))
					newNode.setNe(ch1.getNe());
		}
		if (posGenStrList.get(0).startsWith("VB")){	
			List<Map<String, List<String>>> verbNetGenList = vnProc .generalize(ch1.getWord(), ch2.getWord());
			if (verbNetGenList.size()>0){
				Map<String, List<String>> verbNetGen = verbNetGenList.get(0);
				Map<String, Object> attr = newNode.getAttributes();
				if (attr == null)
					attr = new HashMap<String, Object> ();
				try {
					List<String> phrDscr = (List<String>) attr.get("phrDescr");
					if (phrDscr!=null) // && phrDscr.size()>1)
						phrDscr = new ArrayList<String>(new HashSet<String>(phrDscr));
				} catch (Exception e) {
					System.err.println("Problem de-duplicating verbnet expr" + attr);
				}
				if (verbNetGen!=null){
					attr.putAll(verbNetGen);
					newNode.setAttributes(attr);
				}
			}
		} else if (posGenStrList.get(0).startsWith("NN")){
			//TODO
		}

		results.add(newNode);
		return results;
	}

}
