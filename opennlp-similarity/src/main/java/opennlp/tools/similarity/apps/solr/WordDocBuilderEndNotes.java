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
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;

import net.billylieurance.azuresearch.AzureSearchImageResult;
import net.billylieurance.azuresearch.AzureSearchResultSet;
import net.billylieurance.azuresearch.AzureSearchWebResult;

import org.apache.commons.lang.StringUtils;
import org.docx4j.XmlUtils;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.EndnotesPart;
import org.docx4j.wml.CTEndnotes;
import org.docx4j.wml.CTFtnEdn;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.R;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.Fragment;
import opennlp.tools.similarity.apps.HitBase;

public class WordDocBuilderEndNotes extends WordDocBuilderSingleImageSearchCall{
	
	public String buildWordDoc(List<HitBase> content, String title){
		
		String outputDocFinename =  absPath+"written/"+ title.replace(' ','_').replace('\"', ' ').trim()+ ".docx";
		
		WordprocessingMLPackage wordMLPackage=null;
		
       
		List<String> imageURLs = getAllImageSearchResults(title);
		int count=0;
		BigInteger refId = BigInteger.ONE;
		try {
			wordMLPackage = WordprocessingMLPackage.createPackage();
			
			
			CTEndnotes endnotes = null;
			try {
				EndnotesPart ep = new EndnotesPart();
				endnotes = Context.getWmlObjectFactory().createCTEndnotes();
				ep.setJaxbElement(endnotes);
				wordMLPackage.getMainDocumentPart().addTargetPart(ep);
			} catch (InvalidFormatException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			
			
			
			wordMLPackage.getMainDocumentPart().addStyledParagraphOfText("Title", title.toUpperCase());
			for(HitBase para: content){
				if (para.getFragments()==null || para.getFragments().size()<1) // no found content in this hit
						continue;
				try {
					String processedParaTitle = processParagraphTitle(para.getTitle());
					
					if (processedParaTitle!=null && 
							!processedParaTitle.endsWith("..") || StringUtils.isAlphanumeric(processedParaTitle)){
						wordMLPackage.getMainDocumentPart().addStyledParagraphOfText("Subtitle",processedParaTitle);
					}
					String paraText = processParagraphText(para.getFragments().toString());
					wordMLPackage.getMainDocumentPart().addParagraphOfText(paraText);
					
					 CTFtnEdn endnote = Context.getWmlObjectFactory().createCTFtnEdn();
			         endnotes.getEndnote().add(endnote);
			        
			         endnote.setId(refId);
			         refId.add(BigInteger.ONE);
			         String url = para.getUrl();
			         String endnoteBody = "<w:p xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" ><w:pPr><w:pStyle w:val=\"EndnoteText\"/></w:pPr><w:r><w:rPr>" +
			         		"<w:rStyle w:val=\"EndnoteReference\"/></w:rPr><w:endnoteRef/></w:r><w:r><w:t xml:space=\"preserve\"> "+ url + "</w:t></w:r></w:p>";
			         try {
						endnote.getEGBlockLevelElts().add( XmlUtils.unmarshalString(endnoteBody));
					} catch (JAXBException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			         
			         // Add the body text referencing it
			         String docBody = "<w:p xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" ><w:r><w:t>"//+ paraText
			         /*+ refId.toString()*/ +"</w:t></w:r><w:r><w:rPr><w:rStyle w:val=\"EndnoteReference\"/></w:rPr><w:endnoteReference w:id=\""+refId.toString()+"\"/></w:r></w:p>";
			         
			         try {
			        	 wordMLPackage.getMainDocumentPart().addParagraph(docBody);
					} catch (JAXBException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					try {
						addImageByImageURLToPackage(count, wordMLPackage, imageURLs);
					} catch (Exception e) {
						// no need to report issues
						//e.printStackTrace();
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
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
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
	
	        
			try {
				wordMLPackage.save(new File(outputDocFinename));
				System.out.println("Finished creating docx ="+outputDocFinename);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			try {
				String fileNameToDownload = "/var/www/wrt_latest/"+title.replace(' ','_').replace('\"', ' ').trim()+ ".docx";
				wordMLPackage.save(new File(fileNameToDownload));
				System.out.println("Wrote a doc for download :"+fileNameToDownload);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return outputDocFinename;
	}
	
	public static String processParagraphText(String title){
		
		return title.replace("[", "").replace("]", "").replace(" | ", "")
		.replace(".,", ".").replace(".\"", "\"").replace(". .", ".")
		.replace(",.", ".");
	}
	
	public static String processParagraphTitle(String title){
		String titleDelim = title.replace('-', '&').replace('|', '&');
		String[] titleParts = titleDelim.split("&");
		
		int lenCurr = -1; 
		String bestPart = null;
		for(String candidatePart: titleParts ){ // if this part longer and does not have periods
			if (lenCurr< candidatePart.length() && candidatePart.indexOf('.')<0){
				lenCurr = candidatePart.length();
				bestPart = candidatePart;
			}
		}
		
		return bestPart;
	}

    
    public static void main(String[] args){
    	WordDocBuilderEndNotes b = new WordDocBuilderEndNotes();
    	List<HitBase> content = new ArrayList<HitBase>();
    	for(int i = 0; i<10; i++){
    		HitBase h = new HitBase();
    		h.setTitle("albert einstein "+i);
    		List<Fragment> frs = new ArrayList<Fragment>();
    		frs.add(new Fragment(" content "+i, 0));
    		h.setFragments(frs);
    		h.setUrl("http://www."+i+".com");
    		content.add(h);
    	}
    	
    	b.buildWordDoc(content, "albert einstein");
    }
}
