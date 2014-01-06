package opennlp.tools.parse_thicket.kernel_interface;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.Tree;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class PT2ExtendedTreeForestBuilder {
	private Matcher matcher = new Matcher();	
	private TreeKernelRunner tkRunner = new TreeKernelRunner();
	private static final String modelFileName = "model.txt",
			trainingFileName = "training.txt";
	
	private List<String[]> formTrainingSetFromText(String para,  boolean positive){
		String prefix = null;
		if (positive)
			prefix=" 1 ";
		else
			prefix=" -1 ";
			
		ParseThicket pt = matcher.buildParseThicketFromTextWithRST(para);
		List<Tree> forest = pt.getSentences();
		List<String[]> treeBankBuffer = new ArrayList<String[]>();
		for(Tree t: forest){
			treeBankBuffer.add(new String[] {prefix+"|BT| "+t.toString()+ " |ET|"});
		}
		return treeBankBuffer;
	}
	
	public void formPosNegTrainingSet(String pos, String neg, String path){
		List<String[]> list = formTrainingSetFromText(pos,  true), 
				negList= formTrainingSetFromText(neg, false);
		list.addAll(negList);
		ProfileReaderWriter.writeReport(list, path+trainingFileName, ' ');
		tkRunner.runLearner(path, trainingFileName, modelFileName);
	}
	
	public void classifySentences(String sentences, String path){
		ParseThicket pt = matcher.buildParseThicketFromTextWithRST(sentences);
		List<Tree> forest = pt.getSentences();
		List<String[]> treeBankBuffer = new ArrayList<String[]>();
		for(Tree t: forest){
			treeBankBuffer.add(new String[] {" 0 |BT| "+t.toString()+ " |ET|"});
		}
		
		ProfileReaderWriter.writeReport(treeBankBuffer, path+"unknown.txt", ' ');
		tkRunner.runClassifier(path, "unknown.txt", modelFileName, "classifier_output.txt");
		
		
	}
	
	
	public static void main(String[] args){
		
		PT2ExtendedTreeForestBuilder builder = new PT2ExtendedTreeForestBuilder();
		
			
		String posSents = "Iran refuses to accept the UN proposal to end its dispute over its work on nuclear weapons."+
				"UN nuclear watchdog passes a resolution condemning Iran for developing its second uranium enrichment site in secret. " +
				"A recent IAEA report presented diagrams that suggested Iran was secretly working on nuclear weapons. " +
				"Iran envoy says its nuclear development is for peaceful purpose, and the material evidence against it has been fabricated by the US. ";

		String negSents = "Iran refuses the UN offer to end a conflict over its nuclear weapons."+
						"UN passes a resolution prohibiting Iran from developing its uranium enrichment site. " +
						"A recent UN report presented charts saying Iran was working on nuclear weapons. " +
				"Iran envoy to UN states its nuclear development is for peaceful purpose, and the evidence against its claim is fabricated by the US. ";
		builder.formPosNegTrainingSet(posSents, negSents, "C:\\stanford-corenlp\\tree_kernel\\");
		
		
		builder.classifySentences("Iran refuses Iraq's offer to end its conflict with UN. Iran passes a resolution prohibiting UN from doing second" +
				" uranium enrichment site. Envoy to US says its nuclear development is for peaceful purposes. Material evidence againt US has been fabricated by UN.", 
				
				"C:\\stanford-corenlp\\tree_kernel\\");
	}
}
