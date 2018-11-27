package opennlp.tools.word2vec;

import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.InMemoryLookupCache;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.UimaSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public class W2VDistanceMeasurer {
	static W2VDistanceMeasurer instance;
	public Word2Vec vec = null;
	private String resourceDir = null;

	public synchronized static W2VDistanceMeasurer getInstance() {
		if (instance == null)
			instance = new W2VDistanceMeasurer();
		return instance;
	}

	public W2VDistanceMeasurer(){
		if (resourceDir ==null)
			try {
				resourceDir = new File( "." ).getCanonicalPath()+"/src/test/resources";
			} catch (IOException e) {
				e.printStackTrace();
				vec = null;
				return;
			}
	
		String pathToW2V = resourceDir + "/w2v/GoogleNews-vectors-negative300.bin.gz";
		File gModel = new File(pathToW2V);
		try {
			vec = WordVectorSerializer.loadGoogleModel(gModel, true);
		} catch (IOException e) {
			System.out.println("Word2vec model is not loaded");
			vec = null;
			return;
		} 
		
	} 

	public static void main(String[] args){

		W2VDistanceMeasurer vw2v = W2VDistanceMeasurer.getInstance();

		double value = vw2v.vec.similarity("product", "item");
		System.out.println(value);
	}


	public static void runCycle() {

		String filePath=null;
		try {
			filePath = new ClassPathResource("raw_sentences.txt").getFile().getAbsolutePath();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		System.out.println("Load & Vectorize Sentences....");
		// Strip white space before and after for each line
		SentenceIterator iter=null;
		try {
			iter = UimaSentenceIterator.createWithPath(filePath);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// Split on white spaces in the line to get words
		TokenizerFactory t = new DefaultTokenizerFactory();
		t.setTokenPreProcessor(new CommonPreprocessor());

		InMemoryLookupCache cache = new InMemoryLookupCache();
		WeightLookupTable table = new InMemoryLookupTable.Builder()
		.vectorLength(100)
		.useAdaGrad(false)
		.cache(cache)
		.lr(0.025f).build();

		System.out.println("Building model....");
		Word2Vec vec = new Word2Vec.Builder()
		.minWordFrequency(5).iterations(1)
		.layerSize(100).lookupTable(table)
		.stopWords(new ArrayList<String>())
		.vocabCache(cache).seed(42)
		.windowSize(5).iterate(iter).tokenizerFactory(t).build();

		System.out.println("Fitting Word2Vec model....");
		try {
			vec.fit();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Writing word vectors to text file....");
		// Write word
		try {
			WordVectorSerializer.writeWordVectors(vec, "pathToWriteto.txt");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Closest Words:");
		Collection<String> lst = vec.wordsNearest("day", 10);
		System.out.println(lst);
	}
}

