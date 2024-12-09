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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.postag.POSTagger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This class handles the extraction of
 * <a href="https://web.eecs.umich.edu/~mihalcea/senseval/senseval3/data.html">Senseval-3</a>
 * data from the different files (training data, dictionary instances, etc.)
 */
public class SensevalReader {

  private static final Logger LOG = LoggerFactory.getLogger(SensevalReader.class);

  private String sensemapFile;
  private String data;
  private String wordList;

  private Document trainDoc;

  public SensevalReader(String sensevalDirectory) {
    super();
    LOG.warn("Reading from: {} ...", sensevalDirectory);
    setSensevalDirectory(sensevalDirectory);
    try {
      initTrainDocument();
    } catch (IOException | ParserConfigurationException | SAXException e) {
      throw new RuntimeException(e);
    }
  }

  private void initTrainDocument() throws IOException, ParserConfigurationException, SAXException {
    final InputStream resource;
    try {
      if (data.endsWith(".train.gz")) {
        resource = new GZIPInputStream(new FileInputStream(data));
      } else {
        resource = new FileInputStream(data);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error opening or loading Senseval data from specified resource file!", e);
    }
    final EntityResolver noop = (publicId, systemId) -> new InputSource(new StringReader(""));

    try (InputStream xmlFileInputStream = new BufferedInputStream(resource)) {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      DocumentBuilder dBuilder = dbf.newDocumentBuilder();
      dBuilder.setEntityResolver(noop);
      trainDoc = dBuilder.parse(xmlFileInputStream);
      trainDoc.getDocumentElement().normalize();
    }
  }

  private void setSensevalDirectory(String sensevalDirectory) {
    this.data = sensevalDirectory + "EnglishLS.train.gz";
    this.wordList = sensevalDirectory + "EnglishLS.train.key.gz";
    this.sensemapFile = sensevalDirectory + "EnglishLS.sensemap";
  }

  /**
   * This extracts the equivalent senses. This serves in the case of the
   * coarse-grained disambiguation
   *
   * @return a {@link HashMap} conaining the new sense ID ({@link Integer}) and
   *         an {@link ArrayList} of the equivalent senses original IDs
   */
  public HashMap<Integer, ArrayList<String>> getEquivalentSense() {

    HashMap<Integer, ArrayList<String>> mappedSenses = new HashMap<>();

    try (BufferedReader wordsList = new BufferedReader(new FileReader(sensemapFile))) {

      int index = 0;
      String line;

      while ((line = wordsList.readLine()) != null) {

        String[] temp = line.split("\\s");

        ArrayList<String> tempSenses = new ArrayList<>();

        for (String sense : temp) {
          if (sense.length() > 1) {
            tempSenses.add(sense);
          }
        }

        mappedSenses.put(index, tempSenses);
        index++;

      }

    } catch (IOException e) {
      throw new RuntimeException("Error reading Senseval data from specified resource file!", e);
    }

    return mappedSenses;

  }

  /**
   * This returns the list of words available in the Senseval data
   * 
   * @return {@link ArrayList} of the words available on the current Senseval
   *         set
   */
  public List<String> getSensevalWords() {
    LOG.debug("Getting senseval words...");

    List<String> wordTags = new ArrayList<>();
    final InputStream resource;
    try {
      if (wordList.endsWith(".train.key.gz")) {
        resource = new GZIPInputStream(new FileInputStream(wordList));
      } else {
        resource = new FileInputStream(wordList);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error opening or loading Senseval wordlist from specified resource file!", e);
    }

    try (BufferedReader br = new BufferedReader(new InputStreamReader(resource))) {
      String line;
      while ((line = br.readLine()) != null) {
        String word = line.split("\\s")[0];
        if (!wordTags.contains(word)) {
          wordTags.add(word);
        }
      }

    } catch (IOException e) {
      LOG.error("Problems reading {}: {}", wordList, e.getLocalizedMessage(), e);
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
  public List<WSDSample> getSensevalData(String wordTag) {

    final Lemmatizer lemmatizer = WSDHelper.getLemmatizer();
    final POSTagger tagger = WSDHelper.getTagger();
    
    List<WSDSample> setInstances = new ArrayList<>();

    NodeList lexelts = trainDoc.getElementsByTagName("lexelt");

    for (int i = 0; i < lexelts.getLength(); i++) {

      Node nLexelt = lexelts.item(i);

      if (nLexelt.getNodeType() == Node.ELEMENT_NODE) {
        Element eLexelt = (Element) nLexelt;

        if (eLexelt.getAttribute("item").equals(wordTag)) {

          NodeList nInstances = nLexelt.getChildNodes();

          for (int j = 1; j < nInstances.getLength(); j++) {

            Node nInstance = nInstances.item(j);

            if (nInstance.getNodeType() == Node.ELEMENT_NODE) {
              List<String> senseIDs = new ArrayList<>();
              String rawWord;
              String[] finalText = null;
              int index = 0;

              NodeList nChildren = nInstance.getChildNodes();

              for (int k = 1; k < nChildren.getLength(); k++) {
                Node nChild = nChildren.item(k);

                if (nChild.getNodeName().equals("answer")) {
                  // String answer =
                  // nChild.getAttributes().item(0).getTextContent();

                  String temp = nChild.getAttributes().item(1).getTextContent();
                  // String[] temp = { answer, senseid };
                  senseIDs.add(temp);
                }

                if (nChild.getNodeName().equals("context")) {

                  if (nChild.hasChildNodes()) {
                    String textBefore = nChild.getChildNodes().item(0).getTextContent();
                    rawWord = nChild.getChildNodes().item(1).getTextContent();
                    String textAfter = nChild.getChildNodes().item(2).getTextContent();

                    List<String> textBeforeTokenized = Arrays.asList(textBefore.split("\\s"));
                    List<String> textAfterTokenized = Arrays.asList(textAfter.split("\\s"));

                    textBeforeTokenized.removeAll(Collections.singleton(null));
                    // textBeforeTokenized.removeAll(Collections.singleton(""));
                    textAfterTokenized.removeAll(Collections.singleton(null));
                    // textAfterTokenized.removeAll(Collections.singleton(""));

                    finalText = new String[textBeforeTokenized.size() + 1
                        + textAfterTokenized.size()];

                    int l = 0;
                    for (String tempWord : textBeforeTokenized) {
                      finalText[l] = tempWord;
                      l++;
                    }
                    index = l;
                    finalText[l] = rawWord.toLowerCase();
                    l++;
                    for (String tempWord : textAfterTokenized) {
                      finalText[l] = tempWord;
                      l++;
                    }

                  }
                }

              }

              final String[] words = finalText;
              final String[] tags = tagger.tag(finalText);
              String[] lemmas = lemmatizer.lemmatize(words, tags);

              WSDSample wtd = new WSDSample(words, tags, lemmas, index, senseIDs.toArray(new String[0]));
              setInstances.add(wtd);

            }
          }
        }
      }
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
