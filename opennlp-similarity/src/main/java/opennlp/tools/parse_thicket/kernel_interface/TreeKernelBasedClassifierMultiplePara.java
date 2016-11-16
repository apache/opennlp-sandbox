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
import java.util.Collection;
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

public class TreeKernelBasedClassifierMultiplePara extends TreeKernelBasedClassifier{
	boolean bShortRun = false;
	public void setShortRun(){
		bShortRun = true;
	}


	public void trainClassifier(
			String posDirectory, String negDirectory) {

		queuePos.clear(); queueNeg.clear();
		addFilesPos(new File(posDirectory));
		addFilesNeg(new File(negDirectory));

		List<File> filesPos = new ArrayList<File>(queuePos), filesNeg = new ArrayList<File>(queueNeg);

		Collection<String> treeBankBuffer = new ArrayList<String>();
		int countPos=0, countNeg=0;

		for (File f : filesPos) {
			// get first paragraph of text
			List<String> texts=DescriptiveParagraphFromDocExtractor.getLongParagraphsFromFile(f);		
			List<String> lines = formTreeKernelStructuresMultiplePara(texts, "1");
			treeBankBuffer.addAll(lines);		
			if (bShortRun && countPos>3000)
				break;

			countPos++;
		}	
		for (File f : filesNeg) {
			// get first paragraph of text 
			List<String> texts=DescriptiveParagraphFromDocExtractor.getLongParagraphsFromFile(f);	
			List<String> lines = formTreeKernelStructuresMultiplePara(texts, "-1");
			treeBankBuffer.addAll(lines);	
			if (bShortRun && countNeg>3000)
				break;

			countNeg++;
		}	

		// write the lists of samples to a file
		try {
			FileUtils.writeLines(new File(path+trainingFileName), null, treeBankBuffer);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//	ProfileReaderWriter.writeReport(treeBankBuffer, path+trainingFileName, ' ');
		// build the model
		tkRunner.runLearner(path, trainingFileName, modelFileName);
	}

	public List<String[]> classifyFilesInDirectory(String dirFilesToBeClassified){
		Map<Integer, Integer> countObject = new HashMap<Integer, Integer>(); 
		int itemCount=0, objectCount = 0;
		List<String> treeBankBuffer = new ArrayList<String>();
		queuePos.clear();
		addFilesPos(new File( dirFilesToBeClassified));
		List<File> filesUnkn = new ArrayList<File>(queuePos);
		for (File f : filesUnkn) {	
			List<String> texts=DescriptiveParagraphFromDocExtractor.getLongParagraphsFromFile(f);
			List<String> lines = formTreeKernelStructuresMultiplePara(texts, "0");
			for(String l: lines){
				countObject.put(itemCount, objectCount);
				itemCount++;
			}
			objectCount++;
			treeBankBuffer.addAll(lines);		
		}	
		// write the lists of samples to a file
		try {
			FileUtils.writeLines(new File(path+unknownToBeClassified), null, treeBankBuffer);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		tkRunner.runClassifier(path, unknownToBeClassified, modelFileName, classifierOutput);
		// read classification results
		List<String[]> classifResults = ProfileReaderWriter.readProfiles(path+classifierOutput, ' ');
		// iterate through classification results and set them as scores for hits
		List<String[]>results = new ArrayList<String[]>();

		itemCount=0; objectCount = 0;
		int currentItemCount=0;
		float accum = 0;
		for(String[] line: classifResults){
			Float val = Float.parseFloat(line[0]);
			accum+=val;
			// last line
			Boolean bLastLine = false;
			if (itemCount==classifResults.size()-1)
				bLastLine = true;

			if (objectCount== countObject .get(itemCount) /*&& !bLastLine*/){
				itemCount++; 
				currentItemCount++;
				continue;
			}
			else while(objectCount!= countObject .get(itemCount)-1){
				objectCount++;
				String[] rline = new String[]{filesUnkn.get(objectCount).getName(), "unknown", "0",
						filesUnkn.get(objectCount).getAbsolutePath() , new Integer(itemCount).toString(), new Integer(objectCount).toString()}; 
				results.add(rline);
			}
			objectCount = countObject.get(itemCount);
			itemCount++; 

			float averaged = accum/(float)currentItemCount;
			currentItemCount=0;
			Boolean in = false;
			if (averaged> MIN_SVM_SCORE_TOBE_IN)
				in = true;

			String[] rline = new String[]{filesUnkn.get(objectCount).getName(), in.toString(), new Float(averaged).toString(),
					filesUnkn.get(objectCount).getAbsolutePath() , new Integer(itemCount).toString(), new Integer(objectCount).toString()}; 
			results.add(rline);
			accum=0;
		}
		return results;

	}


	protected List<String> formTreeKernelStructuresMultiplePara(List<String> texts, String flag) {
		List<String> extendedTreesDumpTotal = new ArrayList<String>();
		try {
			for(String text: texts){
				// get the parses from original documents, and form the training dataset
				System.out.println("About to build pt from "+text);
				ParseThicket pt = matcher.buildParseThicketFromTextWithRST(text);
				System.out.print("About to build extended forest ");
				List<String> extendedTreesDump = treeExtender.buildForestForCorefArcs(pt);
				for(String line: extendedTreesDump)
					extendedTreesDumpTotal.add(flag + " |BT| "+line + " |ET| ");
				System.out.println("DONE");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return extendedTreesDumpTotal;
	}

	public static void main(String[] args){
		VerbNetProcessor p = VerbNetProcessor.
				getInstance("/Users/bgalitsky/Documents/relevance-based-on-parse-trees/src/test/resources"); 

		TreeKernelBasedClassifierMultiplePara proc = new TreeKernelBasedClassifierMultiplePara();
		proc.setKernelPath("/Users/bgalitsky/Documents/relevance-based-on-parse-trees/src/test/resources/tree_kernel/");
		proc.trainClassifier(

				"/Users/bgalitsky/Documents/relevance-based-on-parse-trees/src/test/resources/style_recognizer/txt/ted",
				"/Users/bgalitsky/Documents/relevance-based-on-parse-trees/src/test/resources/style_recognizer/txt/Tedi");

		//		List<String[]>res = proc.classifyFilesInDirectory(args[2]);
		//		ProfileReaderWriter.writeReport(res, "svmDesignDocReport05plus.csv");
	}

}
