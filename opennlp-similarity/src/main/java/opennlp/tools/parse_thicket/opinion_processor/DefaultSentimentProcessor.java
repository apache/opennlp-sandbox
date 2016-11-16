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
package opennlp.tools.parse_thicket.opinion_processor;

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.util.logging.Redwood;

import java.util.Iterator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabeledWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.WordLemmaTag;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations.SentimentAnnotatedTree;
import edu.stanford.nlp.sentiment.SentimentUtils;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

public class DefaultSentimentProcessor {
	/** A logger for this class */
	private static final Logger log = Logger
			.getLogger("opennlp.tools.parse_thicket.opinion_processor.DefaultSentimentProcessor");

	private static final NumberFormat NF = new DecimalFormat("0.0000");

	enum Output {
		PENNTREES, VECTORS, ROOT, PROBABILITIES
	}

	enum Input {
		TEXT, TREES
	}

	/**
	 * Sets the labels on the tree (except the leaves) to be the integer
	 * value of the sentiment prediction.  Makes it easy to print out
	 * with Tree.toString()
	 */
	static void setSentimentLabels(Tree tree) {
		if (tree.isLeaf()) {
			return;
		}

		for (Tree child : tree.children()) {
			setSentimentLabels(child);
		}

		Label label = tree.label();
		if (!(label instanceof CoreLabel)) {
			throw new IllegalArgumentException("Required a tree with CoreLabels");
		}
		CoreLabel cl = (CoreLabel) label;
		cl.setValue(Integer.toString(RNNCoreAnnotations.getPredictedClass(tree)));
	}

	/**
	 * Sets the labels on the tree to be the indices of the nodes.
	 * Starts counting at the root and does a postorder traversal.
	 */
	static int setIndexLabels(Tree tree, int index) {
		if (tree.isLeaf()) {
			return index;
		}

		tree.label().setValue(Integer.toString(index));
		index++;
		for (Tree child : tree.children()) {
			index = setIndexLabels(child, index);
		}
		return index;
	}

	/**
	 * Outputs the vectors from the tree.  Counts the tree nodes the
	 * same as setIndexLabels.
	 */
	static int outputTreeVectors(PrintStream out, Tree tree, int index) {
		if (tree.isLeaf()) {
			return index;
		}

		out.print("  " + index + ":");
		SimpleMatrix vector = RNNCoreAnnotations.getNodeVector(tree);
		for (int i = 0; i < vector.getNumElements(); ++i) {
			out.print("  " + NF.format(vector.get(i)));
		}
		out.println();
		index++;
		for (Tree child : tree.children()) {
			index = outputTreeVectors(out, child, index);
		}
		return index;
	}

	/**
	 * Outputs the scores from the tree.  Counts the tree nodes the
	 * same as setIndexLabels.
	 */
	static int outputTreeScores(PrintStream out, Tree tree, int index) {
		if (tree.isLeaf()) {
			return index;
		}

		out.print("  " + index + ":");
		SimpleMatrix vector = RNNCoreAnnotations.getPredictions(tree);
		for (int i = 0; i < vector.getNumElements(); ++i) {
			out.print("  " + NF.format(vector.get(i)));
		}
		out.println();
		index++;
		for (Tree child : tree.children()) {
			index = outputTreeScores(out, child, index);
		}
		return index;
	}

	public static <T> String wordToString(T o, final boolean justValue) {
		return wordToString(o, justValue, null);
	}

	public static <T> String wordToString(T o, final boolean justValue,
			final String separator) {
		if (justValue && o instanceof Label) {
			if (o instanceof CoreLabel) {
				CoreLabel l = (CoreLabel) o;
				String w = l.value();
				if (w == null)
					w = l.word();
				return w;
			} else {
				return (((Label) o).value());
			}
		} else if (o instanceof CoreLabel) {
			CoreLabel l = ((CoreLabel) o);
			String w = l.value();
			if (w == null)
				w = l.word();
			if (l.tag() != null) {
				if (separator == null) {
					return w + CoreLabel.TAG_SEPARATOR + l.tag();
				} else {
					return w + separator + l.tag();
				}
			}
			return w;
			// an interface that covered these next four cases would be
			// nice, but we're moving away from these data types anyway
		} else if (separator != null && o instanceof TaggedWord) {
			return ((TaggedWord) o).toString(separator);
		} else if (separator != null && o instanceof LabeledWord) {
			return ((LabeledWord) o).toString();
		} else if (separator != null && o instanceof WordLemmaTag) {
			return ((WordLemmaTag) o).toString(separator);
		} else if (separator != null && o instanceof WordTag) {
			return ((WordTag) o).toString(separator);
		} else {
			return (o.toString());
		}
	}


	/**
	 * Returns the sentence as a string with a space between words.
	 * It prints out the {@code value()} of each item -
	 * this will give the expected answer for a short form representation
	 * of the "sentence" over a range of cases.  It is equivalent to
	 * calling {@code toString(true)}.
	 *
	 * TODO: Sentence used to be a subclass of ArrayList, with this
	 * method as the toString.  Therefore, there may be instances of
	 * ArrayList being printed that expect this method to be used.
	 *
	 * @param list The tokenized sentence to print out
	 * @return The tokenized sentence as a String
	 */
	public static <T> String listToString(List<T> list) {
		return listToString(list, true);
	}
	/**
	 * Returns the sentence as a string with a space between words.
	 * Designed to work robustly, even if the elements stored in the
	 * 'Sentence' are not of type Label.
	 *
	 * This one uses the default separators for any word type that uses
	 * separators, such as TaggedWord.
	 *
	 * @param list The tokenized sentence to print out
	 * @param justValue If {@code true} and the elements are of type
	 *                  {@code Label}, return just the
	 *                  {@code value()} of the {@code Label} of each word;
	 *                  otherwise,
	 *                  call the {@code toString()} method on each item.
	 * @return The sentence in String form
	 */
	public static <T> String listToString(List<T> list, final boolean justValue) {
		return listToString(list, justValue, null);
	}

	/**
	 * As already described, but if separator is not null, then objects
	 * such as TaggedWord
	 *
	 * @param separator The string used to separate Word and Tag
	 *                  in TaggedWord, etc
	 */
	public static <T> String listToString(List<T> list, final boolean justValue,
			final String separator) {
		StringBuilder s = new StringBuilder();
		for (Iterator<T> wordIterator = list.iterator(); wordIterator.hasNext();) {
			T o = wordIterator.next();
			s.append(wordToString(o, justValue, separator));
			if (wordIterator.hasNext()) {
				s.append(' ');
			}
		}
		return s.toString();
	}

	/**
	 * Outputs a tree using the output style requested
	 */
	static void outputTree(PrintStream out, CoreMap sentence, List<Output> outputFormats) {
		Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
		for (Output output : outputFormats) {
			switch (output) {
			case PENNTREES: {
				Tree copy = tree.deepCopy();
				setSentimentLabels(copy);
				out.println(copy);
				break;
			}
			case VECTORS: {
				Tree copy = tree.deepCopy();
				setIndexLabels(copy, 0);
				out.println(copy);
				outputTreeVectors(out, tree, 0);
				break;
			}
			case ROOT: {
				out.println("  " + sentence.get(SentimentCoreAnnotations.SentimentClass.class));
				break;
			}
			case PROBABILITIES: {
				Tree copy = tree.deepCopy();
				setIndexLabels(copy, 0);
				out.println(copy);
				outputTreeScores(out, tree, 0);
				break;
			}
			default:
				throw new IllegalArgumentException("Unknown output format " + output);
			}
		}
	}

	/**
	 * Reads an annotation from the given filename using the requested input.
	 */
	public static List<Annotation> getAnnotations(StanfordCoreNLP tokenizer, Input inputFormat, String filename, boolean filterUnknown) {
		switch (inputFormat) {
		case TEXT: {
			String text = IOUtils.slurpFileNoExceptions(filename);
			Annotation annotation = new Annotation(text);
			tokenizer.annotate(annotation);
			List<Annotation> annotations = Generics.newArrayList();
			for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
				Annotation nextAnnotation = new Annotation(sentence.get(CoreAnnotations.TextAnnotation.class));
				nextAnnotation.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
				annotations.add(nextAnnotation);
			}
			return annotations;
		}
		case TREES: {
			List<Tree> trees;
			if (filterUnknown) {
				trees = SentimentUtils.readTreesWithGoldLabels(filename);
				trees = SentimentUtils.filterUnknownRoots(trees);
			} else {
				trees = Generics.newArrayList();
				MemoryTreebank treebank = new MemoryTreebank("utf-8");
				treebank.loadPath(filename, null);
				for (Tree tree : treebank) {
					trees.add(tree);
				}
			}

			List<Annotation> annotations = Generics.newArrayList();
			for (Tree tree : trees) {
				CoreMap sentence = new Annotation(listToString(tree.yield()));
				sentence.set(TreeCoreAnnotations.TreeAnnotation.class, tree);
				List<CoreMap> sentences = Collections.singletonList(sentence);
				Annotation annotation = new Annotation("");
				annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
				annotations.add(annotation);
			}
			return annotations;
		}
		default:
			throw new IllegalArgumentException("Unknown format " + inputFormat);
		}
	}

	/** Runs the tree-based sentiment model on some text. */
	public void processTextWithArgs(String[] args) throws IOException {
		String parserModel = null;
		String sentimentModel = null;

		String filename = null;
		String fileList = null;
		boolean stdin = false;

		boolean filterUnknown = false;

		List<Output> outputFormats = Collections.singletonList(Output.ROOT);
		Input inputFormat = Input.TEXT;

		String tlppClass = "DEFAULT_TLPP_CLASS";

		for (int argIndex = 0; argIndex < args.length; ) {
			if (args[argIndex].equalsIgnoreCase("-sentimentModel")) {
				sentimentModel = args[argIndex + 1];
				argIndex += 2;
			} else if (args[argIndex].equalsIgnoreCase("-parserModel")) {
				parserModel = args[argIndex + 1];
				argIndex += 2;
			} else if (args[argIndex].equalsIgnoreCase("-file")) {
				filename = args[argIndex + 1];
				argIndex += 2;
			} else if (args[argIndex].equalsIgnoreCase("-fileList")) {
				fileList = args[argIndex + 1];
				argIndex += 2;
			} else if (args[argIndex].equalsIgnoreCase("-stdin")) {
				stdin = true;
				argIndex++;
			} else if (args[argIndex].equalsIgnoreCase("-input")) {
				inputFormat = Input.valueOf(args[argIndex + 1].toUpperCase());
				argIndex += 2;
			} else if (args[argIndex].equalsIgnoreCase("-output")) {
				String[] formats = args[argIndex + 1].split(",");
				outputFormats = new ArrayList<>();
				for (String format : formats) {
					outputFormats.add(Output.valueOf(format.toUpperCase()));
				}
				argIndex += 2;
			} else if (args[argIndex].equalsIgnoreCase("-filterUnknown")) {
				filterUnknown = true;
				argIndex++;
			} else if (args[argIndex].equalsIgnoreCase("-tlppClass")) {
				tlppClass = args[argIndex + 1];
				argIndex += 2;
			} else if (args[argIndex].equalsIgnoreCase("-help")) {
				System.exit(0);
			} else {
				log.info("Unknown argument " + args[argIndex + 1]);
				throw new IllegalArgumentException("Unknown argument " + args[argIndex + 1]);
			}
		}

		// We construct two pipelines.  One handles tokenization, if
		// necessary.  The other takes tokenized sentences and converts
		// them to sentiment trees.
		Properties pipelineProps = new Properties();
		Properties tokenizerProps = null;
		if (sentimentModel != null) {
			pipelineProps.setProperty("sentiment.model", sentimentModel);
		}
		if (parserModel != null) {
			pipelineProps.setProperty("parse.model", parserModel);
		}
		if (inputFormat == Input.TREES) {
			pipelineProps.setProperty("annotators", "binarizer, sentiment");
			pipelineProps.setProperty("customAnnotatorClass.binarizer", "edu.stanford.nlp.pipeline.BinarizerAnnotator");
			pipelineProps.setProperty("binarizer.tlppClass", tlppClass);
			pipelineProps.setProperty("enforceRequirements", "false");
		} else {
			pipelineProps.setProperty("annotators", "parse, sentiment");
			pipelineProps.setProperty("enforceRequirements", "false");
			tokenizerProps = new Properties();
			tokenizerProps.setProperty("annotators", "tokenize, ssplit");
		}

		if (stdin && tokenizerProps != null) {
			tokenizerProps.setProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "true");
		}

		int count = 0;
		if (filename != null) count++;
		if (fileList != null) count++;
		if (stdin) count++;
		if (count > 1) {
			throw new IllegalArgumentException("Please only specify one of -file, -fileList or -stdin");
		}
		if (count == 0) {
			throw new IllegalArgumentException("Please specify either -file, -fileList or -stdin");
		}

		StanfordCoreNLP tokenizer = (tokenizerProps == null) ? null : new StanfordCoreNLP(tokenizerProps);
		StanfordCoreNLP pipeline = new StanfordCoreNLP(pipelineProps);

		if (filename != null) {
			// Process a file.  The pipeline will do tokenization, which
			// means it will split it into sentences as best as possible
			// with the tokenizer.
			List<Annotation> annotations = getAnnotations(tokenizer, inputFormat, filename, filterUnknown);
			for (Annotation annotation : annotations) {
				pipeline.annotate(annotation);

				for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
					System.out.println(sentence);
					outputTree(System.out, sentence, outputFormats);
				}
			}
		} else if (fileList != null) {
			// Process multiple files.  The pipeline will do tokenization,
			// which means it will split it into sentences as best as
			// possible with the tokenizer.  Output will go to filename.out
			// for each file.
			for (String file : fileList.split(",")) {
				List<Annotation> annotations = getAnnotations(tokenizer, inputFormat, file, filterUnknown);
				FileOutputStream fout = new FileOutputStream(file + ".out");
				PrintStream pout = new PrintStream(fout);
				for (Annotation annotation : annotations) {
					pipeline.annotate(annotation);

					for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
						pout.println(sentence);
						outputTree(pout, sentence, outputFormats);
					}
				}
				pout.flush();
				fout.close();
			}
		} else {
			// Process stdin.  Each line will be treated as a single sentence.
			log.info("Reading in text from stdin.");
			log.info("Please enter one sentence per line.");
			log.info("Processing will end when EOF is reached.");
			BufferedReader reader = IOUtils.readerFromStdin("utf-8");

			for (String line; (line = reader.readLine()) != null; ) {
				line = line.trim();
				if ( ! line.isEmpty()) {
					Annotation annotation = tokenizer.process(line);
					pipeline.annotate(annotation);
					for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
						outputTree(System.out, sentence, outputFormats);
					}
				} else {
					// Output blank lines for blank lines so the tool can be
					// used for line-by-line text processing
					System.out.println();
				}
			}

		}
	}

	public float getNumericSentimentValue(String expression) {
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		int mainSentiment = 0;
		if (expression != null && expression.length() > 0) {
			int longest = 0;
			Annotation annotation = pipeline.process(expression);
			for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
				Tree tree = sentence.get(SentimentAnnotatedTree.class);
				int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
				String partText = sentence.toString();
				if (partText.length() > longest) {
					mainSentiment = sentiment;
					longest = partText.length();
				}
			}
		}
		return mainSentiment;
	}
}
