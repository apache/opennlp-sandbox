/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.word2vec;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.InMemoryLookupCache;
import org.deeplearning4j.text.sentenceiterator.FileSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.common.primitives.Pair;

public class W2VDistanceMeasurer {
	static W2VDistanceMeasurer instance;
	public Word2Vec vec = null;

	public synchronized static W2VDistanceMeasurer getInstance() {
		if (instance == null)
			instance = new W2VDistanceMeasurer();
		return instance;
	}

	public W2VDistanceMeasurer(){
		String resourceDir = null;
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
			Pair<InMemoryLookupTable, VocabCache> pair = WordVectorSerializer.loadTxt(Files.newInputStream(gModel.toPath()));
			vec = WordVectorSerializer.fromPair(pair);
		} catch (IOException e) {
			System.out.println("Word2vec model is not loaded");
			vec = null;
		} 
		
	} 

	public static void main(String[] args){

		W2VDistanceMeasurer vw2v = W2VDistanceMeasurer.getInstance();

		double value = vw2v.vec.similarity("product", "item");
		System.out.println(value);
	}


	public static void runCycle() {

		SentenceIterator iter=null;
		try {
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			String filePath = new File(cl.getResource("/raw_sentences.txt").toURI()).getAbsolutePath();
			// Strip white space before and after for each line
			System.out.println("Load & Vectorize Sentences....");
			iter = new FileSentenceIterator(new File(filePath));
		} catch (URISyntaxException e1) {
			e1.printStackTrace();
		}

		// Split on white spaces in the line to get words
		TokenizerFactory t = new DefaultTokenizerFactory();
		t.setTokenPreProcessor(new CommonPreprocessor());

		InMemoryLookupCache cache = new InMemoryLookupCache();
		WeightLookupTable<VocabWord> table = new InMemoryLookupTable.Builder<VocabWord>()
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
		vec.fit();

		System.out.println("Writing word vectors to text file....");
		// Write word
		WordVectorSerializer.writeWord2VecModel(vec, "pathToWriteTo.txt");

		System.out.println("Closest Words:");
		Collection<String> lst = vec.wordsNearest("day", 10);
		System.out.println(lst);
	}
}

