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
package opennlp.tools.similarity.apps.solr;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import net.billylieurance.azuresearch.AzureSearchImageResult;
import net.billylieurance.azuresearch.AzureSearchResultSet;
import net.billylieurance.azuresearch.AzureSearchWebResult;

import org.apache.commons.lang.StringUtils;
//import org.docx4j.Docx4J;
//import org.docx4j.convert.out.FOSettings;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;

import opennlp.tools.similarity.apps.ContentGeneratorSupport;
import opennlp.tools.similarity.apps.Fragment;
import opennlp.tools.similarity.apps.HitBase;



public class WordDocBuilderSingleImageSearchCall extends WordDocBuilder{
	
	public String buildWordDoc(List<HitBase> content, String title){
		
		String outputDocFilename =  absPath+"/written/"+ title.replace(' ','_').replace('\"', ' ').trim()+ ".docx";
		
		WordprocessingMLPackage wordMLPackage;
		List<String> imageURLs = getAllImageSearchResults(title);
		int count=0;
		try {
			wordMLPackage = WordprocessingMLPackage.createPackage();
			wordMLPackage.getMainDocumentPart().addStyledParagraphOfText("Title", title.toUpperCase());
			for(HitBase para: content){
				if (para.getFragments()==null || para.getFragments().size()<1) // no found content in this hit
						continue;
				try {
					if (!para.getTitle().endsWith("..") /*|| StringUtils.isAlphanumeric(para.getTitle())*/){
						String sectTitle = ContentGeneratorSupport.getPortionOfTitleWithoutDelimiters(para.getTitle());
						wordMLPackage.getMainDocumentPart().addStyledParagraphOfText("Subtitle",
							sectTitle);
					}
					String paraText = para.getFragments().toString().replace("[", "").replace("]", "").replace(" | ", "")
							.replace(".,", ".").replace(".\"", "\"").replace(". .", ".")
							.replace(",.", ".");
					wordMLPackage.getMainDocumentPart().addParagraphOfText(paraText);
					
					try {
						addImageByImageURLToPackage(count, wordMLPackage, imageURLs);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				count++;
			}
			// now add URLs
			wordMLPackage.getMainDocumentPart().addStyledParagraphOfText("Subtitle", "REFERENCES");
			for(HitBase para: content){
				if (para.getFragments()==null || para.getFragments().size()<1) // no found content in this hit
						continue;
				try {
					wordMLPackage.getMainDocumentPart().addStyledParagraphOfText("Subtitle",
							para.getTitle());
					String paraText = para.getUrl();
					wordMLPackage.getMainDocumentPart().addParagraphOfText(paraText);
					
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
	
	        
			wordMLPackage.save(new File(outputDocFilename));
			System.out.println("Finished creating docx ="+outputDocFilename);
		//TODO pdf export
			/*
			FOSettings foSettings = Docx4J.createFOSettings();
            foSettings.setWmlPackage(wordMLPackage);
            OutputStream os = new java.io.FileOutputStream(outputDocFilename.replace(".docx", ".pdf"));
            Docx4J.toFO(foSettings, os, Docx4J.FLAG_NONE);
        	System.out.println("Finished creating docx's PDF ="+outputDocFilename);
    	*/	
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return outputDocFilename;
	}
	
	protected void addImageByImageURLToPackage(int count,
			WordprocessingMLPackage wordMLPackage,
			List<String>  imageURLs) {
		if (count>imageURLs.size()-1)
			return;
		
		String url = imageURLs.get(count);
		String destinationFile = url.replace("http://", "").replace("/", "_");
		saveImageFromTheWeb(url, absPath+IMG_REL_PATH+destinationFile);
		File file = new File(absPath+IMG_REL_PATH+destinationFile);
        try {
			byte[] bytes = convertImageToByteArray(file);
			addImageToPackage(wordMLPackage, bytes);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	protected List<String>  getAllImageSearchResults(String title) {
		List<String> imageURLs = new ArrayList<String>();
		AzureSearchResultSet<AzureSearchImageResult> res = imageSearcher.runImageSearch(title);
		for(AzureSearchImageResult imResult: res){
			imageURLs.add(imResult.getMediaUrl());
		}
		return imageURLs;
		
	}

    
    public static void main(String[] args){
    	WordDocBuilderSingleImageSearchCall b = new WordDocBuilderSingleImageSearchCall();
    	List<HitBase> content = new ArrayList<HitBase>();
    	for(int i = 0; i<10; i++){
    		HitBase h = new HitBase();
    		h.setTitle("albert einstein "+i);
    		List<Fragment> frs = new ArrayList<Fragment>();
    		frs.add(new Fragment(" content "+i, 0));
    		h.setFragments(frs);
    		content.add(h);
    	}
    	
    	b.buildWordDoc(content, "albert einstein");
    }
}
