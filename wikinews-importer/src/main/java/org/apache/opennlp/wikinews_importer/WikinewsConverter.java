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

package org.apache.opennlp.wikinews_importer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.xml.sax.SAXException;

/**
 * Demo application which reads an uncompressed Wikipedia XML dump
 * file and writes each article as an XMI file.
 */
public class WikinewsConverter {

  static class CASArticleFilter implements IArticleFilter {

    private final TypeSystemDescription tsDesc;
    private final File outputFolder;
    private List<String> endOfArtilceMarkers = new ArrayList<String>();
    
    CASArticleFilter(TypeSystemDescription tsDesc, File outputFolder) {
      
      this.tsDesc = tsDesc;
      this.outputFolder = outputFolder;
      
      endOfArtilceMarkers.add("{{haveyoursay}}");
      endOfArtilceMarkers.add("== Sources ==");
      endOfArtilceMarkers.add("==Sources==");
      endOfArtilceMarkers.add("== Source ==");
      endOfArtilceMarkers.add("==Source==");
      endOfArtilceMarkers.add("==References==");
      endOfArtilceMarkers.add("== References ==");
      endOfArtilceMarkers.add("=== References===");
    }
    
    
      public static String titleToUri(String title) {
      try {
          return URLEncoder.encode(title.replaceAll(" ", "_"), "UTF-8");
      } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
      }
    }
    
    public void process(WikiArticle page, Siteinfo siteinfo)
        throws SAXException {
      
      if (page.getIntegerNamespace() == 0 && page.isMain()) {

        if (page.getText().toLowerCase().contains("{publish}")) {
          
          String pageText = page.getText();
          

          int cutIndex = pageText.length();

          for (String endMarker : endOfArtilceMarkers) {
            int endMarkerIndex = pageText.indexOf(endMarker);
              if (endMarkerIndex != -1 && endMarkerIndex < cutIndex) {
                cutIndex = endMarkerIndex;
              }
          }
          
          if (cutIndex < pageText.length()) {
            pageText = pageText.substring(0, cutIndex);
          }
          
          WikinewsWikiModel wikiModel = new WikinewsWikiModel("http://en.wikinews.org/wiki/${image}", 
              "http://en.wikinews.org/wiki/${title}");
          
          AnnotatingMarkupParser converter = new AnnotatingMarkupParser();
          String plainStr = wikiModel.render(converter, pageText);
          
          CAS articleCAS = UimaUtil.createEmptyCAS(tsDesc);
          
          // TODO: find a way to nicely add title ..
          StringBuilder articleText = new StringBuilder();
          articleText.append(page.getTitle());
          
          int endOffsetTitle = articleText.length();
          
          articleText.append("\n");
          articleText.append("\n");
          
          int bodyOffset = articleText.length();
          
          articleText.append(plainStr); // Note: Add offset to annotations ... by this
          
          articleCAS.setDocumentLanguage("en");
          articleCAS.setDocumentText(articleText.toString());
          
          AnnotationFS headlineAnnotation = articleCAS.createAnnotation(articleCAS.getTypeSystem()
              .getType("org.apache.opennlp.annotations.Headline"),
              0, endOffsetTitle);
          
          articleCAS.addFsToIndexes(headlineAnnotation);
          
          for (Annotation paraAnn : converter.getParagraphAnnotations()) {
            AnnotationFS paraAnnFS = articleCAS.createAnnotation(articleCAS.getTypeSystem()
                .getType("org.apache.opennlp.annotations.Paragraph"),
                bodyOffset + paraAnn.begin, bodyOffset + paraAnn.end);
            
            articleCAS.addFsToIndexes(paraAnnFS);
          }
          
          for (Annotation subHeadAnn : converter.getHeaderAnnotations()) {
            AnnotationFS subHeadAnnFS = articleCAS.createAnnotation(
                articleCAS.getTypeSystem()
                .getType("org.apache.opennlp.annotations.SubHeadline"),
                bodyOffset + subHeadAnn.begin, bodyOffset + subHeadAnn.end);
            
            articleCAS.addFsToIndexes(subHeadAnnFS);
          }
          
          Type wikiLinkType = articleCAS.getTypeSystem()
              .getType("org.apache.opennlp.annotations.WikiLink");
          Feature linkFeature = wikiLinkType.getFeatureByBaseName("link");
          
          for (Annotation wikiLinkAnn : converter.getWikiLinkAnnotations()) {
            AnnotationFS wikiLinkAnnFS = articleCAS.createAnnotation(
                articleCAS.getTypeSystem()
                .getType("org.apache.opennlp.annotations.WikiLink"),
                bodyOffset + wikiLinkAnn.begin, bodyOffset + wikiLinkAnn.end);
            
            wikiLinkAnnFS.setStringValue(linkFeature, wikiLinkAnn.value);
            
            articleCAS.addFsToIndexes(wikiLinkAnnFS);
          }
          
          CAS markupCas = articleCAS.createView("WikiMarkup");
          markupCas.setDocumentText(page.toString());
          
          // now serialize CAS
          OutputStream casOut = null;
          try {
              casOut = new FileOutputStream(outputFolder.getAbsolutePath() +
            		  File.separator + titleToUri(page.getTitle()) + ".xmi");
              
              UimaUtil.serializeCASToXmi(articleCAS, casOut);
          }
          catch (IOException e) {
            e.printStackTrace();
          }
          finally {
            try {
            if (casOut != null)
                casOut.close();
              } catch (IOException e) {
              }
          }
          
        }
      }
    }
  }

  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: Parser <XML-File> <Output-Folder>"); 
      System.exit(-1);
    }
    
    // TODO: Should to be configurable!
    TypeSystemDescription tsDesc = UimaUtil.createTypeSystemDescription(
        new FileInputStream("samples/TypeSystem.xml"));

    File outputFolder = new File(args[1]);
    outputFolder.mkdirs();
    
    String bz2Filename = args[0];
    try {
      IArticleFilter handler = new CASArticleFilter(tsDesc, new File(args[1]));
      WikiXMLParser wxp = new WikiXMLParser(bz2Filename, handler);
      wxp.parse();
    } catch (Exception e) {
      System.out.println("Parsing the corpus failed:");
      System.out.println();
      e.printStackTrace();
    }
  }
}
