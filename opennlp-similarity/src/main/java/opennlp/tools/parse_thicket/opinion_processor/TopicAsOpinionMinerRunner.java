package opennlp.tools.parse_thicket.opinion_processor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class TopicAsOpinionMinerRunner {
	private List<File> queue;
	private final static String reviewSource = "/Users/bgalitsky/Documents/solr/example/exampledocs/publication_page0.json";
	NamedEntityExtractor neExtractor = new NamedEntityExtractor();
	Set<String> allPhrases = new HashSet<String>();
	
	public void processJSONfileWithReviews(){
		List<String[]> report = new ArrayList<String[]>();
		report.add(new String[] { "text", "phrases of potential interest list" , });

		
		String content=null;
		try {
			content = FileUtils.readFileToString(new File(reviewSource));
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] texts = StringUtils.substringsBetween(content, "summary\":\"", "\"");
		for(String text: texts){
			report.clear();
			EntityExtractionResult result = neExtractor.extractEntities(text);
			//report.add(new String[]{text});
			allPhrases.addAll(result.extractedNERWords);
			allPhrases = new HashSet<String>(allPhrases);
			for(String p: allPhrases){
				report.add(new String[]{p});
			}
			/*
			String[] phrases = (String[])result.extractedNERWords.toArray(new String[0]);
			if (phrases!=null && phrases.length>0)
				report.add(phrases);
			*/
			/*report.add((String[])result.extractedSentimentPhrases.toArray(new String[0]));
			List<String> stringPhrases = new ArrayList<String>(),
					nodePhrases = new ArrayList<String>();
			for(List<ParseTreeNode> chList: result.extractedSentimentPhrases){
				String buf = "", nodeBuf="";
				for(ParseTreeNode ch: chList){
					buf+=ch.getWord()+ " ";
					nodeBuf+=ch.toString()+ " ";
				}
				stringPhrases.add(buf.trim());
				nodePhrases.add(nodeBuf.trim());
			}
			report.add((String[])stringPhrases.toArray(new String[0]));
			report.add((String[])nodePhrases.toArray(new String[0]));
			*/
			
			ProfileReaderWriter.writeReport(report, "phrasesExtracted3.csv");
		}
	}

	private void addFiles(File file) {

		if (!file.exists()) {
			System.out.println(file + " does not exist.");

			if (file.isDirectory()) {
				for (File f : file.listFiles()) {
					if (f.getName().startsWith("."))
						continue;
					addFiles(f);
					System.out.println(f.getName());
				}
			} else {
				queue.add(file);

			}
		}
	}
	
	public static void main(String[] args){
		TopicAsOpinionMinerRunner runner = new TopicAsOpinionMinerRunner();
		runner.processJSONfileWithReviews();

	}
}

/*
	public void processDirectory(String path){
		List<String[]> report = new ArrayList<String[]>();
		report.add(new String[] { "filename", "named entity list", "phrases of potential interest list" });

		List<String> allNamedEntities = new ArrayList<String>();

		addFiles(new File(path));
		for(File f: queue){
			List<String> entities = (List<String>) extractEntities(f.getAbsolutePath()).getFirst();
			List<String> opinions = (List<String>) extractEntities(f.getAbsolutePath()).getSecond();
			report.add(new String[]{ f.getName(), entities.toString(),  opinions.toString()});	
			ProfileReaderWriter.writeReport(report, "nameEntitiesExtracted.csv");

			allNamedEntities.addAll(entities);

			allNamedEntities = new ArrayList<String>(new HashSet<String> (allNamedEntities ));


		}
		ProfileReaderWriter.writeReport(report, "nameEntitiesTopicsOfInterestExtracted.csv");
	} 
} */
