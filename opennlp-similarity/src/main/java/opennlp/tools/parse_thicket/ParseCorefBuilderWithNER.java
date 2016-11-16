package opennlp.tools.parse_thicket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations.SentimentAnnotatedTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import opennlp.tools.parse_thicket.ArcType;
import opennlp.tools.parse_thicket.Pair;
import opennlp.tools.parse_thicket.ParseCorefsBuilder;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;

public class ParseCorefBuilderWithNER extends ParseCorefsBuilder {

	private static ParseCorefBuilderWithNER instanceNER;

	public synchronized static ParseCorefBuilderWithNER  getInstance() {
		if (instanceNER == null)
			instanceNER = new ParseCorefBuilderWithNER ();

		return instanceNER;
	}


	AbstractSequenceClassifier<CoreLabel> classifier = null;

	ParseCorefBuilderWithNER() {
		super();
		classifier = CRFClassifier.getDefaultClassifier();
	}

	public ParseThicket buildParseThicket(String text){
		List<Tree> ptTrees = new ArrayList<Tree>();
		// all numbering from 1, not 0
		List<WordWordInterSentenceRelationArc> arcs = new ArrayList<WordWordInterSentenceRelationArc>();
		List<List<ParseTreeNode>> nodesThicket = new ArrayList<List<ParseTreeNode>>();
		List<Float> sentimentProfile = new ArrayList<Float>();

		annotation = new Annotation(text);
		try {
			pipeline.annotate(annotation);
			List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
			List<List<CoreLabel>> nerClassesText = classifier.classify(text);
			

			int nSent = 0;
			if (sentences != null && sentences.size() > 0) 
				for(CoreMap sentence: sentences){
					List<ParseTreeNode> nodes = new ArrayList<ParseTreeNode>();
					if (nSent>=nerClassesText.size())
						break;
					List<CoreLabel> nerClassesSent = nerClassesText .get(nSent);

					// traversing the words in the current sentence
					// a CoreLabel is a CoreMap with additional token-specific methods
					Class<TokensAnnotation> tokenAnn = TokensAnnotation.class;
					List<CoreLabel> coreLabelList = sentence.get(tokenAnn);
					int count=1;
					for (CoreLabel token: coreLabelList ) {
						if (count-1>=nerClassesSent.size())
							break;
						CoreLabel classNerWord = 	nerClassesSent .get(count-1);
						// this is the text of the token
						String lemma = token.get(TextAnnotation.class);
						// this is the POS tag of the token
						String pos = token.get(PartOfSpeechAnnotation.class);
						// this is the NER label of the token
						String ne = token.get(NamedEntityTagAnnotation.class);     


						ParseTreeNode p = new ParseTreeNode(lemma, pos, ne, count);
						String ner = classNerWord .get(CoreAnnotations.AnswerAnnotation.class);
						if (!ner.equals("O")){
							Map<String, Object> nerMap = new HashMap<String, Object>();
							nerMap.put("ner", ner);
							p.setAttributes(nerMap);
						}
						nodes.add(p);
						count++;
					}	
					nSent++;
					nodesThicket.add(nodes);
					Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
					// now sentiment for given sentence
					Tree sentimentTree = sentence.get(SentimentAnnotatedTree.class);
					float sentiment = RNNCoreAnnotations.getPredictedClass(sentimentTree);
					sentimentProfile.add(sentiment);
					ptTrees.add(tree);
				}
		} catch (Exception e) {
			e.printStackTrace();
		}


		// now coreferences
		Map<Integer, CorefChain> corefs = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
		List<CorefChain> chains = new ArrayList<CorefChain>(corefs.values());
		for(CorefChain c: chains){
			//System.out.println(c);
			List<CorefMention> mentions = c.getMentionsInTextualOrder();
			//System.out.println(mentions);
			if (mentions.size()>1)
				for(int i=0; i<mentions.size(); i++){
					for(int j=i+1; j<mentions.size(); j++){
						CorefMention mi = mentions.get(i), mj=mentions.get(j);


						int niSentence = mi.position.get(0);
						int niWord = mi.startIndex;
						int njSentence = mj.position.get(0);
						int njWord = mj.startIndex;

						ArcType arcType = new ArcType("coref-", mj.mentionType+"-"+mj.animacy, 0, 0);

						WordWordInterSentenceRelationArc arc = 
								new WordWordInterSentenceRelationArc(new Pair<Integer, Integer>(niSentence,niWord), 
										new Pair<Integer, Integer>(njSentence,njWord), mi.mentionSpan, mj.mentionSpan, 
										arcType);
						arcs.add(arc);
					}
				}
		}
		List<WordWordInterSentenceRelationArc> arcsCA = buildCAarcs(nodesThicket);
		arcs.addAll(arcsCA);

		ParseThicket result = new ParseThicket(ptTrees, arcs);
		result.setSentimentProfile(sentimentProfile);
		result.setNodesThicket(nodesThicket);
		return result;
	}

	public static void main(String[] args){
		new ParseCorefBuilderWithNER ().buildParseThicket("No one knows yet what General Prayuth's real intentions are. He has good reason to worry about resistance. "
				+ "The pro-government Red-Shirt movement is far better organised than eight years ago, and could still be financed by former Prime Minister Thaksin Shinawatra's deep pockets.");
	}

}
