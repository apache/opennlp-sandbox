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

package opennlp.tools.disambiguator.datareader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

/**
 * This class handles the extraction of Senseval-3 data from the different files
 * (training data, dictionary instances, etc.)
 */
public class SensevalReader {

  protected String sensevalDirectory = "src/test/resources/senseval3/";

  protected String data = sensevalDirectory + "EnglishLS.train";
  protected String sensemapFile = sensevalDirectory + "EnglishLS.sensemap";
  protected String wordList = sensevalDirectory + "EnglishLS.train.key";

  public String getSensevalDirectory() {
    return sensevalDirectory;
  }

  public void setSensevalDirectory(String sensevalDirectory) {
    this.sensevalDirectory = sensevalDirectory;

    this.data = sensevalDirectory + "EnglishLS.train";
    this.sensemapFile = sensevalDirectory + "EnglishLS.sensemap";
    this.wordList = sensevalDirectory + "EnglishLS.train.key";
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
   * disambiguate exist in the folder, and extract the {@link WSDSample}
   * instances
   * 
   * @param wordTag
   *          The word, of which we are looking for the instances
   * @return the list of the {@link WSDSample} instances of the word to
   *         disambiguate
   */
  public ArrayList<WSDSample> getSensevalData(String wordTag) {

    ArrayList<WSDSample> setInstances = new ArrayList<WSDSample>();

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
                ArrayList<String> senseIDs = new ArrayList<String>();
                String rawWord = "";
                String[] finalText = null;
                int index = 0;

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
                    senseIDs.add(temp);
                  }

                  if (nChild.getNodeName().equals("context")) {

                    if (nChild.hasChildNodes()) {
                      String textBefore = nChild.getChildNodes().item(0)
                          .getTextContent();
                      rawWord = nChild.getChildNodes().item(1).getTextContent();
                      String textAfter = nChild.getChildNodes().item(2)
                          .getTextContent();

                      ArrayList<String> textBeforeTokenzed = new ArrayList<String>(
                          Arrays.asList(textBefore.split("\\s")));
                      ArrayList<String> textAfterTokenzed = new ArrayList<String>(
                          Arrays.asList(textAfter.split("\\s")));

                      textBeforeTokenzed.removeAll(Collections.singleton(null));
                      textBeforeTokenzed.removeAll(Collections.singleton(""));

                      textAfterTokenzed.removeAll(Collections.singleton(null));
                      textAfterTokenzed.removeAll(Collections.singleton(""));

                      finalText = new String[textBeforeTokenzed.size() + 1
                          + textAfterTokenzed.size()];

                      int l = 0;
                      for (String tempWord : textBeforeTokenzed) {
                        finalText[l] = tempWord;
                        l++;
                      }
                      index = l;
                      finalText[l] = rawWord.toLowerCase();
                      l++;
                      for (String tempWord : textAfterTokenzed) {
                        finalText[l] = tempWord;
                        l++;
                      }

                    }
                  }

                }

                String[] words = finalText;
                String[] tags = WSDHelper.getTagger().tag(words);
                String[] lemmas = new String[words.length];

                for (int k = 0; k < words.length; k++) {
                  lemmas[k] = WSDHelper.getLemmatizer().lemmatize(words[k],
                      tags[k]);
                }

                WSDSample wtd = new WSDSample(words, tags, lemmas, index,
                    senseIDs.toArray(new String[0]));
                setInstances.add(wtd);

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

  /**
   * Main Senseval Reader: This checks if the data corresponding to the words to
   * disambiguate exist in the folder, and extract the
   * 
   * @param wordTag
   *          The word, of which we are looking for the instances
   * @return the stream of {@link WSDSample} of the word to disambiguate
   */
  public ObjectStream<WSDSample> getSensevalDataStream(String wordTag) {
    return ObjectStreamUtils.createObjectStream(getSensevalData(wordTag));
  }

}
