/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.disambiguator.DatasetsReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import opennlp.tools.disambiguator.WordToDisambiguate;
import opennlp.tools.disambiguator.ims.WTDIMS;

/**
 * This class handles the extraction of Senseval-3 data from the different files
 * (training data, dictionary instances, etc.)
 */
public class SensevalReader {

  private String resourcesFolder = "src\\test\\resources\\";
  protected String sensevalDirectory = resourcesFolder + "senseval3\\";

  protected String data = sensevalDirectory + "EnglishLS.train";
  protected String sensemapFile = sensevalDirectory + "EnglishLS.sensemap";
  protected String wordList = sensevalDirectory + "EnglishLS.train.key";

  // protected String dict = sensevalDirectory + "EnglishLS.dictionary.xml";
  // protected String map = sensevalDirectory + "EnglishLS.sensemap";

  /**
   * The XML file of Senseval presents some issues that need to be fixed first
   */
  private String fixXmlFile() {

    // TODO fix this !

    return null;
  }

  public SensevalReader() {
    super();
  }

  /**
   * This extracts the equivalent senses. This serves in the case of the
   * coarse-grained disambiguation
   * 
   * @param sensemapFile
   *          the file containing the equivalent senses, each set of equivalent
   *          senses per line
   * @return a {@link HashMap} conaining the new sense ID ({@link Integer}) and
   *         an {@link ArrayList} of the equivalent senses original IDs
   */
  public HashMap<Integer, ArrayList<String>> getEquivalentSense() {

    HashMap<Integer, ArrayList<String>> mappedSenses = new HashMap<Integer, ArrayList<String>>();

    try (BufferedReader wordsList = new BufferedReader(new FileReader(
        sensemapFile))) {

      int index = 0;

      String line;

      while ((line = wordsList.readLine()) != null) {

        String[] temp = line.split("\\s");

        ArrayList<String> tempSenses = new ArrayList<String>();

        for (String sense : temp) {
          if (sense.length() > 1) {
            tempSenses.add(sense);
          }
        }

        mappedSenses.put(index, tempSenses);
        index++;

      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    return mappedSenses;

  }

  /**
   * This returns the list of words available in the Senseval data
   * 
   * @return {@link ArrayList} of the words available on the current Senseval
   *         set
   */
  public ArrayList<String> getSensevalWords() {

    ArrayList<String> wordTags = new ArrayList<String>();

    try (BufferedReader br = new BufferedReader(new FileReader(wordList))) {

      String line;

      while ((line = br.readLine()) != null) {

        String word = line.split("\\s")[0];

        if (!wordTags.contains(word)) {
          wordTags.add(word);
        }

      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    return wordTags;

  }

  /**
   * Main Senseval Reader: This checks if the data corresponding to the words to
   * disambiguate exist in the folder, and extract the
   * {@link WordToDisambiguate} instances
   * 
   * @param wordTag
   *          The word, of which we are looking for the instances
   * @return the list of the {@link WordToDisambiguate} instances of the word to
   *         disambiguate
   */
  public ArrayList<WordToDisambiguate> getSensevalData(String wordTag) {

    ArrayList<WordToDisambiguate> setInstances = new ArrayList<WordToDisambiguate>();

    try {

      File xmlFile = new File(data);
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(xmlFile);

      doc.getDocumentElement().normalize();

      NodeList lexelts = doc.getElementsByTagName("lexelt");

      for (int i = 0; i < lexelts.getLength(); i++) {

        Node nLexelt = lexelts.item(i);

        if (nLexelt.getNodeType() == Node.ELEMENT_NODE) {
          Element eLexelt = (Element) nLexelt;

          if (eLexelt.getAttribute("item").equals(wordTag)) {

            NodeList nInstances = nLexelt.getChildNodes();

            for (int j = 1; j < nInstances.getLength(); j++) {

              Node nInstance = nInstances.item(j);

              if (nInstance.getNodeType() == Node.ELEMENT_NODE) {

                Element eInstance = (Element) nInstance;

                String[] wordPos = eLexelt.getAttribute("item").split("\\.");
                String word = wordPos[0]; // Word
                String tag; // Part of Speech

                if (wordPos[1].equals("n")) {
                  tag = "noun";
                } else if (wordPos[1].equals("v")) {
                  tag = "verb";
                } else if (wordPos[1].equals("a")) {
                  tag = "adjective";
                } else {
                  tag = "adverb";
                }

                String id = eInstance.getAttribute("id");
                String source = eInstance.getAttribute("docsrc");

                ArrayList<String> answers = new ArrayList<String>();
                String sentence = "";
                String rawWord = "";

                NodeList nChildren = nInstance.getChildNodes();

                for (int k = 1; k < nChildren.getLength(); k++) {
                  Node nChild = nChildren.item(k);

                  if (nChild.getNodeName().equals("answer")) {
                    // String answer =
                    // nChild.getAttributes().item(0).getTextContent();
                    String senseid = nChild.getAttributes().item(1)
                        .getTextContent();

                    String temp = senseid;
                    // String[] temp = { answer, senseid };
                    answers.add(temp);
                  }

                  if (nChild.getNodeName().equals("context")) {
                    sentence = ((Element) nChild).getTextContent();

                    if (nChild.hasChildNodes()) {
                      // textbefore =
                      // nChild.getChildNodes().item(0).getTextContent();
                      rawWord = nChild.getChildNodes().item(1).getTextContent();
                      // textAfter =
                      // nChild.getChildNodes().item(2).getTextContent();
                    }
                  }

                }

                WTDIMS wordToDisambiguate = new WTDIMS(word, answers, sentence,
                    rawWord);
                setInstances.add(wordToDisambiguate);
              }
            }

          }

        }

      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    return setInstances;

  }

}
