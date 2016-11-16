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
package opennlp.tools.parse_thicket.kernel_interface;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;


import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.parse_thicket.apps.MultiSentenceSearchResultsProcessor;
import opennlp.tools.parse_thicket.matching.Matcher;

public class TreeKernelBasedClassifier {
	protected static Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.TreeKernelBasedClassifier");
	protected ArrayList<File> queuePos = new ArrayList<File>(), queueNeg = new ArrayList<File>();
  
	protected Matcher matcher = new Matcher();
	protected TreeKernelRunner tkRunner = new TreeKernelRunner();
	protected TreeExtenderByAnotherLinkedTree treeExtender = new TreeExtenderByAnotherLinkedTree();


	protected String path;
	public void setKernelPath (String path){
		this.path=path;
	}
	protected static final String modelFileName = "model.txt";

	protected static final String trainingFileName = "training.txt";

	protected static final String unknownToBeClassified = "unknown.txt";

	protected static final String classifierOutput = "classifier_output.txt";
	protected static final Float MIN_SVM_SCORE_TOBE_IN = 0.2f;
	
	/* main entry point to SVM TK classifier
     * gets a file, reads it outside of CI, extracts longer paragraphs and builds parse thickets for them.
     * Then parse thicket dump is processed by svm_classify
     */
	public Boolean classifyText(File f){
		FileUtils.deleteQuietly(new File(path+unknownToBeClassified)); 
		if (!(new File(path+modelFileName).exists())){
			LOG.severe("Model file '" +modelFileName + "'is absent: skip SVM classification");
			return null;
		}
		Map<Integer, Integer> countObject = new HashMap<Integer, Integer>(); 
		int itemCount=0, objectCount = 0;
		List<String> treeBankBuffer = new ArrayList<String>();	
		List<String> texts=DescriptiveParagraphFromDocExtractor.getLongParagraphsFromFile(f);
		List<String> lines = formTreeKernelStructuresMultiplePara(texts, "0");
		for(String l: lines){
			countObject.put(itemCount, objectCount);
			itemCount++;
		}
		objectCount++;
		treeBankBuffer.addAll(lines);		

		// write the lists of samples to a file
		try {
			FileUtils.writeLines(new File(path+unknownToBeClassified), null, treeBankBuffer);
		} catch (IOException e) {
			LOG.severe("Problem creating parse thicket files '"+ path+unknownToBeClassified + "' to be classified\n"+ e.getMessage() );
		}

		tkRunner.runClassifier(path, unknownToBeClassified, modelFileName, classifierOutput);
		// read classification results
		List<String[]> classifResults = ProfileReaderWriter.readProfiles(path+classifierOutput, ' ');


		itemCount=0; objectCount = 0;
		int currentItemCount=0;
		float accum = 0;
		LOG.info("\nsvm scores per paragraph: " );
		for(String[] line: classifResults){
			Float val = Float.parseFloat(line[0]);
			System.out.print(val+" ");
			accum+=val;
			currentItemCount++;
		}

		float averaged = accum/(float)currentItemCount;
		LOG.info("\n average = "+averaged);
		currentItemCount=0;
		Boolean in = false;
		if (averaged> MIN_SVM_SCORE_TOBE_IN)
			return true;
		else
			return false;
	}

	protected void addFilesPos(File file) {

		if (!file.exists()) {
			System.out.println(file + " does not exist.");
		}
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				//if (!(f.getName().endsWith(".txt") || f.getName().endsWith(".pdf")))
				//	continue;
				addFilesPos(f);
				System.out.println(f.getName());
			}
		} else {
			queuePos.add(file);
		}
	}
	
	protected void addFilesNeg(File file) {

		if (!file.exists()) {
			System.out.println(file + " does not exist.");
		}
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				//if (!(f.getName().endsWith(".txt")||f.getName().endsWith(".pdf")))
				//	continue;
				addFilesNeg(f);
				System.out.println(f.getName());
			}
		} else {
			queueNeg.add(file);
		}
	}

	protected void trainClassifier(
			String posDirectory, String negDirectory) {
		
		queuePos.clear(); queueNeg.clear();
		addFilesPos(new File(posDirectory));
		addFilesNeg(new File(negDirectory));
		
		List<File> filesPos = new ArrayList<File>(queuePos), filesNeg = new ArrayList<File>(queueNeg);
		
		List<String[]> treeBankBuffer = new ArrayList<String[]>();

		for (File f : filesPos) {
			// get first paragraph of text
			String text=DescriptiveParagraphFromDocExtractor.getFirstParagraphFromFile(f);		
			treeBankBuffer.add(new String[]{formTreeKernelStructure(text, "1")});		
		}	
		for (File f : filesNeg) {
			// get first paragraph of text
			String text=DescriptiveParagraphFromDocExtractor.getFirstParagraphFromFile(f);
			treeBankBuffer.add(new String[]{formTreeKernelStructure(text, "-1")});		
		}	
		
		// write the lists of samples to a file
		ProfileReaderWriter.writeReport(treeBankBuffer, path+trainingFileName, ' ');
		// build the model
		tkRunner.runLearner(path, trainingFileName, modelFileName);
	}

	public List<String[]> classifyFilesInDirectory(String dirFilesToBeClassified){
		List<String[]> treeBankBuffer = new ArrayList<String[]>();
		queuePos.clear();
		addFilesPos(new File( dirFilesToBeClassified));
		List<File> filesUnkn = new ArrayList<File>(queuePos);
		for (File f : filesUnkn) {	
			String text=DescriptiveParagraphFromDocExtractor.getFirstParagraphFromFile(f);
			String line = formTreeKernelStructure(text, "0");
			treeBankBuffer.add(new String[]{line});		
		}	
	
		// form a file from the texts to be classified
		ProfileReaderWriter.writeReport(treeBankBuffer, path+unknownToBeClassified, ' ');
		
		tkRunner.runClassifier(path, unknownToBeClassified, modelFileName, classifierOutput);
		// read classification results
		List<String[]> classifResults = ProfileReaderWriter.readProfiles(path+classifierOutput, ' ');
		// iterate through classification results and set them as scores for hits
		List<String[]>results = new ArrayList<String[]>();
		int count=0;
		for(String[] line: classifResults){
			Float val = Float.parseFloat(line[0]);
			Boolean in = false;
			if (val> MIN_SVM_SCORE_TOBE_IN)
				in = true;
			
			String[] rline = new String[]{filesUnkn.get(count).getName(), in.toString(), line[0], filesUnkn.get(count).getAbsolutePath() }; // treeBankBuffer.get(count).toString() };
			results.add(rline);
			count++;
			
		}
		return results;

	}

	protected List<String> formTreeKernelStructuresMultiplePara(List<String> texts, String flag) {
		List<String> extendedTreesDumpTotal = new ArrayList<String>();
		try {

			for(String text: texts){
				// get the parses from original documents, and form the training dataset
				LOG.info("About to build pt from "+text);
				ParseThicket pt = matcher.buildParseThicketFromTextWithRST(text);
				LOG.info("About to build extended forest ");
				List<String> extendedTreesDump = treeExtender.buildForestForCorefArcs(pt);
				for(String line: extendedTreesDump)
					extendedTreesDumpTotal.add(flag + " |BT| "+line + " |ET| ");
				LOG.info("DONE");
			}

		} catch (Exception e) {
			LOG.severe("Problem forming  parse thicket flat file to be classified\n"+ e.getMessage() );
		}
		return extendedTreesDumpTotal;
	}
	protected String formTreeKernelStructure(String text, String flag) {
		String treeBankBuffer = "";
		try {
			// get the parses from original documents, and form the training dataset
			LOG.info("About to build pt from "+text);
			ParseThicket pt = matcher.buildParseThicketFromTextWithRST(text);
			LOG.info("About to build extended forest ");
			List<String> extendedTreesDump = treeExtender.buildForestForCorefArcs(pt);
			LOG.info("DONE");

			treeBankBuffer+=flag;
			// form the list of training samples
			for(String t: extendedTreesDump ){
				if (BracesProcessor.isBalanced(t))
					treeBankBuffer+=" |BT| "+t;
				else
					System.err.println("Wrong tree: " + t);
			}
			if (extendedTreesDump.size()<1)
				treeBankBuffer+=" |BT| ";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return treeBankBuffer+ " |ET|";
	}

	public static void main(String[] args){
		VerbNetProcessor p = VerbNetProcessor.
				getInstance("/Users/borisgalitsky/Documents/workspace/deepContentInspection/src/test/resources"); 
				
		TreeKernelBasedClassifier proc = new TreeKernelBasedClassifier();
		proc.setKernelPath("/Users/borisgalitsky/Documents/tree_kernel/");
		proc.trainClassifier(args[0], args[1]);
		List<String[]>res = proc.classifyFilesInDirectory(args[2]);
		ProfileReaderWriter.writeReport(res, "svmDesignDocReport03minus.csv");
	}

}
