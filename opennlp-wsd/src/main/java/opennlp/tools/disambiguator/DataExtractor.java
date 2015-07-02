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

package opennlp.tools.disambiguator;

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

import opennlp.tools.disambiguator.DictionaryInstance;
import opennlp.tools.disambiguator.DistributionInstance;
import opennlp.tools.disambiguator.ims.WTDIMS;

public class DataExtractor {

  public DataExtractor() {
    super();
  }

  /**
   * Extract the dictionary from the dictionary XML file and map the senses
   */
  private ArrayList<DictionaryInstance> extractDictionary(String xmlLocation) {

    ArrayList<DictionaryInstance> dictionary = new ArrayList<DictionaryInstance>();

    // HashMap<Integer, DictionaryInstance> dictionary = new HashMap<Integer,
    // DictionaryInstance>();

    try {

      File xmlFile = new File(xmlLocation);
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(xmlFile);
      doc.getDocumentElement().normalize();

      NodeList nLexelts = doc.getElementsByTagName("lexelt");

      int index = 0;

      for (int i = 0; i < nLexelts.getLength(); i++) {

        Node nLexelt = nLexelts.item(i);

        Element eLexelt = (Element) nLexelt;

        String word = eLexelt.getAttribute("item");

        if (nLexelt.getNodeType() == Node.ELEMENT_NODE) {

          NodeList nSenses = eLexelt.getChildNodes();

          for (int j = 0; j < nSenses.getLength(); j++) {

            if (nSenses.item(j).getNodeType() == Node.ELEMENT_NODE) {

              Element eSense = (Element) nSenses.item(j);

              int ind = index; // rather use this than the ID used by default
              String id = eSense.getAttribute("id");
              String source = eSense.getAttribute("source");
              String[] synset = eSense.getAttribute("synset").split("\\s");
              String gloss = eSense.getAttribute("gloss");

              DictionaryInstance wd = new DictionaryInstance(ind, word, id,
                  source, synset, gloss);

              dictionary.add(wd);
              index++;
            }
          }

        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return dictionary;

  }

  private HashMap<Integer, ArrayList<String>> getEquivalentSense(
      String sensemapFile) {

    HashMap<Integer, ArrayList<String>> mappedSenses = new HashMap<Integer, ArrayList<String>>();

    try (BufferedReader wordsList = new BufferedReader(new FileReader(
        sensemapFile))) {

      int index = 0;

      String line;

      // Read the file
      while ((line = wordsList.readLine()) != null) {

        String[] temp = line.split("\\s");

        ArrayList<String> tempSenses = new ArrayList<String>();

        for (String sense : temp) {
          if (sense.length() > 1) {
            // System.out.println(sense);
            tempSenses.add(sense);
          }
        }

        mappedSenses.put(index, tempSenses);
        // System.out.println(index);
        index++;

      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    return mappedSenses;

  }

  private HashMap<String, ArrayList<DictionaryInstance>> extractOptimalDictionary(
      String xmlLocation, String sensemapFile) {

    HashMap<String, ArrayList<DictionaryInstance>> optimizedDictionary = new HashMap<String, ArrayList<DictionaryInstance>>();

    HashMap<Integer, ArrayList<String>> equivalentSenses = getEquivalentSense(sensemapFile);

    ArrayList<DictionaryInstance> dictionary = extractDictionary(xmlLocation);

    for (int mapKey : equivalentSenses.keySet()) {
      ArrayList<String> sensesIds = equivalentSenses.get(mapKey);
      ArrayList<DictionaryInstance> optimizedDictionaryInstance = new ArrayList<DictionaryInstance>();

      String word = "";

      for (String senseId : sensesIds) {
        for (int i = 0; i < dictionary.size(); i++) {
          if (dictionary.get(i).getId().equals(senseId)) {
            optimizedDictionaryInstance.add(dictionary.get(i));
            word = dictionary.get(i).getWord();
            word = word + "_" + mapKey;
            break;
          }
        }

      }

      optimizedDictionary.put(word, optimizedDictionaryInstance);
    }

    return optimizedDictionary;
  }

  public HashMap<String, ArrayList<DictionaryInstance>> extractWordSenses(
      String xmlLocation, String sensemapFile, String wordTag) {

    /**
     * word tag has to be in the format "word.t" (e.g., "activate.v", "smart.a",
     * etc.)
     */

    HashMap<String, ArrayList<DictionaryInstance>> wordSenses = new HashMap<String, ArrayList<DictionaryInstance>>();

    HashMap<String, ArrayList<DictionaryInstance>> optimalDictionary = extractOptimalDictionary(
        xmlLocation, sensemapFile);

    int i = 0;
    for (String key : optimalDictionary.keySet()) {
      if (key.startsWith(wordTag)) {
        String newKey = wordTag + "_" + i;
        wordSenses.put(newKey, optimalDictionary.get(key));
        i++;
      }
    }

    return wordSenses;
  }

  public HashMap<String, String> getDictionaryInstance(String xmlLocation,
      String sensemapFile, String wordTag) {

    HashMap<String, ArrayList<DictionaryInstance>> dict = extractWordSenses(
        xmlLocation, sensemapFile, wordTag);

    HashMap<String, String> senses = new HashMap<String, String>();

    for (String key : dict.keySet()) {
      String sense = dict.get(key).get(0).getGloss();
      senses.put(key, sense);
    }

    return senses;

  }

  /**
   * Extract the Dictionary Map [USELESS UNLESS USED FOR STATISTICS LATER !!!]
   */

  public HashMap<Integer, DistributionInstance> extractWords(String listOfWords) {

    HashMap<Integer, DistributionInstance> instances = new HashMap<Integer, DistributionInstance>();

    try (BufferedReader wordsList = new BufferedReader(new FileReader(
        listOfWords))) {

      String line;

      int index = 0;

      // Read the file
      while ((line = wordsList.readLine()) != null) {

        String[] temp = line.split("\\t");

        String[] wordPos = temp[0].split("\\.");

        String tag;

        if (wordPos[1].equals("n")) {
          tag = "noun";
        } else if (wordPos[1].equals("v")) {
          tag = "verb";
        } else if (wordPos[1].equals("a")) {
          tag = "adjective";
        } else {
          tag = "adverb";
        }

        DistributionInstance word = new DistributionInstance(wordPos[0], tag,
            Integer.parseInt(temp[1]), Integer.parseInt(temp[2]));

        instances.put(index, word);

        index++;

      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    return instances;
  }

  /**
   * Extract the training instances from the training/test set File
   */

  public ArrayList<WTDIMS> extractWSDInstances(String xmlDataSet) {

    ArrayList<WTDIMS> setInstances = new ArrayList<WTDIMS>();

    try {

      File xmlFile = new File(xmlDataSet);
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(xmlFile);

      doc.getDocumentElement().normalize();

      NodeList lexelts = doc.getElementsByTagName("lexelt");

      for (int i = 0; i < lexelts.getLength(); i++) {

        Node nLexelt = lexelts.item(i);

        if (nLexelt.getNodeType() == Node.ELEMENT_NODE) {
          Element eLexelt = (Element) nLexelt;

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
                    // System.out.println(rawWord);
                  }
                }

              }

              WTDIMS wordToDisambiguate = new WTDIMS(word, answers, sentence,
                  rawWord);
              setInstances.add(wordToDisambiguate);
              // System.out.print(index + "\t");
              // System.out.println(wordToDisambiguate.toString());
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
