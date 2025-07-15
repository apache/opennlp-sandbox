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
import java.util.ArrayList;
import java.util.List;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;

import opennlp.tools.similarity.apps.ContentGeneratorSupport;
import opennlp.tools.similarity.apps.Fragment;
import opennlp.tools.similarity.apps.HitBase;

public class WordDocBuilderSingleImageSearchCall extends WordDocBuilder {

	@Override
	public String buildWordDoc(List<HitBase> content, String title){
		
		String outputDocFilename =  absPath + "/written/" +
						title.replace(' ','_').replace('\"', ' ').trim()+ ".docx";
		
		WordprocessingMLPackage wordMLPackage;
		List<String> imageURLs = new ArrayList<>(); //getAllImageSearchResults(title);
		int count=0;
		try {
			wordMLPackage = WordprocessingMLPackage.createPackage();
			MainDocumentPart mdp = wordMLPackage.getMainDocumentPart();
			mdp.addStyledParagraphOfText("Title", title.toUpperCase());
			for(HitBase para: content){
				if (para.getFragments()==null || para.getFragments().size()<1) // no found content in this hit
						continue;
				try {
					if (!para.getTitle().endsWith("..") /*|| StringUtils.isAlphanumeric(para.getTitle())*/){
						String sectTitle = ContentGeneratorSupport.getPortionOfTitleWithoutDelimiters(para.getTitle());
						mdp.addStyledParagraphOfText("Subtitle", sectTitle);
					}
					String paraText = para.getFragments().toString().replace("[", "").replace("]", "").replace(" | ", "")
							.replace(".,", ".").replace(".\"", "\"").replace(". .", ".")
							.replace(",.", ".");
					mdp.addParagraphOfText(paraText);
					
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
			mdp.addStyledParagraphOfText("Subtitle", "REFERENCES");
			for(HitBase para: content){
				if (para.getFragments()==null || para.getFragments().size()<1) // no found content in this hit
						continue;
				try {
					mdp.addStyledParagraphOfText("Subtitle", para.getTitle());
					String paraText = para.getUrl();
					mdp.addParagraphOfText(paraText);

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
	        
			wordMLPackage.save(new File(outputDocFilename));
			System.out.println("Finished creating docx ="+outputDocFilename);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return outputDocFilename;
	}
	
	protected void addImageByImageURLToPackage(int count, WordprocessingMLPackage wordMLPackage,
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
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
    
	public static void main(String[] args){
		WordDocBuilderSingleImageSearchCall b = new WordDocBuilderSingleImageSearchCall();
		List<HitBase> content = new ArrayList<>();
		for(int i = 0; i<10; i++){
			HitBase h = new HitBase();
			h.setTitle("albert einstein "+i);
			List<Fragment> frs = new ArrayList<>();
			frs.add(new Fragment(" content "+i, 0));
			h.setFragments(frs);
			content.add(h);
		}

		b.buildWordDoc(content, "albert einstein");
	}
}
