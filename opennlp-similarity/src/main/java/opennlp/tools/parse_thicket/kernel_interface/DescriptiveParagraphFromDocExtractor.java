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
import java.util.Arrays;
import java.util.List;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

public class DescriptiveParagraphFromDocExtractor {
	protected static  Tika tika = new Tika();
	private static int MIN_PARA_LENGTH = 200, //120, 
			MIN_NUM_WORDS=15, 
			MAX_PARA_LENGTH = 500, //200 
			TEXT_PORTION_FOR_ANALYSIS = 20000, 
			MAX_PARA_OUTPUT=20;
	public static String getFirstParagraphFromFile(File f) {

		String text = "";
		try {
			try {
				text = tika.parseToString(f);
			} catch (TikaException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			//text = FileUtils.readFileToString(f, null);
			if (text.length()>TEXT_PORTION_FOR_ANALYSIS)
				text = text.substring(0, TEXT_PORTION_FOR_ANALYSIS);
			float avgSentSizeThr = (float)MIN_PARA_LENGTH/4f; //2f
			String[] portions = text.split("\\.\\n");
			for(String p: portions){
				float avgSentSize = (float)p.length()/(float)p.split("\\n\\n").length;

				if (p.length()> MIN_PARA_LENGTH && p.split(" ").length>MIN_NUM_WORDS &&
						avgSentSize > avgSentSizeThr &&  p.length() < MAX_PARA_LENGTH){
					return normalizePara(p);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// if not a suitable paragraph found, return the whole text
		if (text.length()>150)
			text = text.substring(0, 150);
		return text;
	}

	public static List<String> getLongParagraphsFromFile(File f) {
		List<String> results = new ArrayList<String>();
		String text = "";
		try {
			try {
				text = tika.parseToString(f);
			} catch (TikaException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			//text = FileUtils.readFileToString(f, null);
			if (text.length()>TEXT_PORTION_FOR_ANALYSIS)
				text = text.substring(0, TEXT_PORTION_FOR_ANALYSIS);
			float avgSentSizeThr = (float)MIN_PARA_LENGTH/4f; //2f
			String[] portions = text.split("\\.\\n");
			if (portions.length<2)
				portions = text.split("\\n\\n");
			if (portions.length<2)
				portions = text.split("\\n \\n");
			if (portions.length<2){
				String[] sentences = text.replace('.','&').split(" & ");
				List<String> portionsLst = new ArrayList<String>();
				int totalChars = 0;
				String buffer = "";
				for(String sent: sentences){
					totalChars+=sent.length();
					if (totalChars>MAX_PARA_LENGTH){
						portionsLst.add(buffer);
						buffer="";
						totalChars = 0;
					} else {
						buffer+= sent + ". ";
					}
				}
				portions = portionsLst.toArray(new String[0]);
			}
			for(String p: portions){
				try {
					float avgSentSize = (float)p.length()/(float)p.split("\\n\\n").length;

					if (p.length()> MIN_PARA_LENGTH && p.split(" ").length>MIN_NUM_WORDS &&
							avgSentSize > avgSentSizeThr) {  
						if (p.length() < MAX_PARA_LENGTH){
							results.add(normalizePara(p)); 
						}
						else { // reduce length to the latest '.' in substring
							
							String pReduced = p;
							if (p.length()>= MAX_PARA_LENGTH+80)
								pReduced = p.substring(0, MAX_PARA_LENGTH+80);
							int indexPeriod = pReduced.lastIndexOf('.');
							if (indexPeriod>-1){
								pReduced = pReduced.substring(0, indexPeriod);
							}
							results.add(normalizePara(pReduced));
						}
						if (results.size()>MAX_PARA_OUTPUT)
							break;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (results.size()<1){
				if (text.length()>= MAX_PARA_LENGTH+80)
					text = text.substring(0, MAX_PARA_LENGTH+80);
				results.add(text);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		if (results.size()<1){
			System.err.println("Failed to extract text from "+f.getName());
		}

		return results;
	}

	private static String normalizePara(String p){
		p = p.replaceAll("\\n", " ").replaceAll("\\.\\.", " ").replaceAll("  ", " ");
		p = p.replaceAll("[^A-Za-z0-9 _\\.,\\!]", "");
		return p;
	}

	public static void main(String args[]){
		List<String> results = getLongParagraphsFromFile(new File(
				"/Users/borisgalitsky/Documents/workspace/deepContentInspection/src/test/resources/1k/design_doc_posNeg/pos/2IP40 Detail Design Document.pdf"
				//+ " Online Screening Tool - Delottie.pdf"
				));
		System.out.println(results);

		String res = getFirstParagraphFromFile(new File("/Users/borisgalitsky/Documents/workspace/deepContentInspection/src/test/resources/1k/"
				+ "design_doc/2004Schalk_BCI2000Implementation.pdf"));
		System.out.println(res);
		results = getLongParagraphsFromFile(new File("/Users/borisgalitsky/Documents/workspace/deepContentInspection/src/test/resources/1k/"
				+ "design_doc/2004Schalk_BCI2000Implementation.pdf"));
		System.out.println(results);

	}
}
