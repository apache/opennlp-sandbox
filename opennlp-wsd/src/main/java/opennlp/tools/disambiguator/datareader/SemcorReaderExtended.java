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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * This class reads Semcor data.
 *
 */
public class SemcorReaderExtended {

  private static final String ELEMENT_CONTEXTFILE = "contextfile";
  private static final String ATTRIBUTE_CONCORDANCE = "concordance";

  private static final String ELEMENT_CONTEXT = "context";
  private static final String ATTRIBUTE_FILENAME = "filename";
  private static final String ATTRIBUTE_PARAS = "paras";

  private static final String ELEMENT_PARAGRAPH = "p";
  private static final String ATTRIBUTE_PARAGRAPHNUM = "pnum";

  private static final String ELEMENT_SENTENCE = "s";
  private static final String ATTRIBUTE_SENTENCENUM = "snum";

  private static final String ELEMENT_WORDFORM = "wf";
  private static final String ATTRIBUTE_CMD = "cmd";
  private static final String ATTRIBUTE_RDF = "rdf";
  private static final String ATTRIBUTE_POS = "pos";
  private static final String ATTRIBUTE_LEMMA = "lemma";
  private static final String ATTRIBUTE_WNSN = "wnsn";
  private static final String ATTRIBUTE_LEXSN = "lexsn";
  private static final String ELEMENT_PUNCTUATION = "punc";

  private String semcorDirectory;
  private static final String[] folders = { "brown1", "brown2", "brownv" };
  private static final String tagfiles = "/tagfiles/";
  
  public SemcorReaderExtended(String semcorDirectory) {
    super();
    setSemcorDirectory(semcorDirectory);
  }

  private void setSemcorDirectory(String semcorDirectory) {
    this.semcorDirectory = semcorDirectory;
  }

  /**
   * This serves to read one Semcor XML file
   */
  private ArrayList<Sentence> readFile(String file) {
    ArrayList<Sentence> result = new ArrayList<>();
    final EntityResolver noop = (publicId, systemId) -> new InputSource(new StringReader(""));
    try (InputStream xmlFile = new BufferedInputStream(new FileInputStream(file))) {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      DocumentBuilder dBuilder = dbf.newDocumentBuilder();
      dBuilder.setEntityResolver(noop);
      Document doc = dBuilder.parse(xmlFile);

      doc.getDocumentElement().normalize();

      NodeList paragraphs = doc.getElementsByTagName(ELEMENT_PARAGRAPH);

      for (int i = 0; i < paragraphs.getLength(); i++) {

        Node nParagraph = paragraphs.item(i);

        if (nParagraph.getNodeType() == Node.ELEMENT_NODE) {

          Element eParagraph = (Element) nParagraph;
          // THE PARAGRAPH ID
          int paragraphID = Integer.parseInt(eParagraph
              .getAttribute(ATTRIBUTE_PARAGRAPHNUM));

          NodeList nSentences = nParagraph.getChildNodes();

          for (int j = 1; j < nSentences.getLength(); j++) {

            Node nSentence = nSentences.item(j);
            if (nSentence.getNodeType() == Node.ELEMENT_NODE) {

              Element eSentence = (Element) nSentence;
              // THE SENTENCE ID
              int sentenceID = Integer.parseInt(eSentence
                  .getAttribute(ATTRIBUTE_SENTENCENUM));
              Sentence isentence = new Sentence(paragraphID, sentenceID);

              NodeList nWords = nSentence.getChildNodes();

              int wnum = 0;
              for (int k = 0; k < nWords.getLength(); k++) {
                Node nWord = nWords.item(k);

                if (nWord.getNodeType() == Node.ELEMENT_NODE) {

                  if (nWord.getNodeName().equals(ELEMENT_WORDFORM)) {

                    Element eWord = (Element) nWord;
                    String word = eWord.getTextContent();
                    String cmd = eWord.getAttribute(ATTRIBUTE_CMD);
                    String pos = eWord.getAttribute(ATTRIBUTE_POS);
                    if (eWord.getAttribute(ATTRIBUTE_CMD).equals("done")) {
                      // if the word is already disambiguated
                      String lemma = eWord.getAttribute(ATTRIBUTE_LEMMA);
                      String wnsn = eWord.getAttribute(ATTRIBUTE_WNSN);
                      String lexsn = eWord.getAttribute(ATTRIBUTE_LEXSN);

                      Word iword = new Word(paragraphID, sentenceID, wnum,
                          Word.Type.WORD, word, cmd, pos, lemma, wnsn, lexsn);
                      isentence.addIword(iword);

                      // System.out.println("*** " + iword.toString() + " ***");

                    } else {
                      // if the word is not disambiguated
                      Word iword = new Word(paragraphID, sentenceID, wnum,
                          Word.Type.WORD, word, cmd, pos);
                      isentence.addIword(iword);
                    }
                    wnum++;

                  } else if (nWord.getNodeName().equals(ELEMENT_PUNCTUATION)) {
                    Element eWord = (Element) nWord;
                    String word = eWord.getTextContent();
                    Word iword = new Word(paragraphID, sentenceID, wnum,
                        Word.Type.PUNCTUATIONMARK, word);
                    isentence.addIword(iword);
                    wnum++;
                  }
                }
              }
              result.add(isentence);
            }
          }
        }
      }
    } catch (Exception e) {
      WSDHelper.print("Reading " + file);
      e.printStackTrace();
    }

    return result;
  }

  /**
   * One Semcor folder reader: This reads all the files in one semcor folder,
   * and return all the instances in the format {@link WSDSample} of a
   * specific word
   * 
   * @param file
   *          the name of the file to read
   * @param wordTag
   *          The word, of which we are looking for the instances
   * @return the list of the {@link WSDSample} instances
   */
  private ArrayList<WSDSample> getSemcorOneFileData(String file, String wordTag) {

    ArrayList<WSDSample> setInstances = new ArrayList<>();

    try {

      ArrayList<Sentence> isentences = readFile(file);
      for (int j = 0; j < isentences.size(); j++) {
        Sentence isentence = isentences.get(j);
        ArrayList<Word> iwords = isentence.getIwords();
        for (int k = 0; k < iwords.size(); k++) {
          Word iword = iwords.get(k);
          if (iword.isInstanceOf(wordTag)) {

            String sentence;
            int index;

            if (j == 0) {
              // case of the first sentence, we consider the current sentence
              // and the next two ones
              sentence = isentences.get(j).toString() + " "
                  + isentences.get(j + 1).toString() + " "
                  + isentences.get(j + 2).toString();
              index = k;
            } else if (j == isentences.size() - 1) {
              // case of the last sentence, we consider the current sentence and
              // the previous two ones
              sentence = isentences.get(j - 2).toString() + " "
                  + isentences.get(j - 1).toString() + " "
                  + isentences.get(j).toString();
              index = isentences.get(j - 2).getIwords().size()
                  + isentences.get(j - 1).getIwords().size() + k;
            } else {
              // case of a sentence in the middle, we consider the previous
              // sentence + the current one + the next one
              sentence = isentences.get(j - 1).toString() + " "
                  + isentences.get(j).toString() + " "
                  + isentences.get(j + 1).toString();
              index = isentences.get(j - 1).getIwords().size() + k;
            }
            ArrayList<String> senses = new ArrayList<>();
            String sense = iword.getLexsn();
            if (sense != null) {
              senses.add(sense);
            }

            if (!senses.isEmpty()) {
              final Lemmatizer lemmatizer = WSDHelper.getLemmatizer();
              final POSTagger tagger = WSDHelper.getTagger();

              final String[] words = sentence.split("\\s");
              final String[] tags = tagger.tag(words);
              String[] lemmas = lemmatizer.lemmatize(words, tags);

              WSDSample wtd = new WSDSample(words, tags, lemmas, index, senses.toArray(new String[0]));
              setInstances.add(wtd);
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
   * One Semcor folder reader: This reads all the files in one semcor folder,
   * and return all the instances in the format {@link WSDSample} of a
   * specific word
   * 
   * @param folder
   *          the name of the folder. Three folders exist in Semcor3.0, which
   *          are ["brown1", "brown2", "brownv"]
   * @param wordTag
   *          The word, of which we are looking for the instances
   * @return the list of the {@link WSDSample} instances
   */
  private ArrayList<WSDSample> getSemcorFolderData(String folder, String wordTag) {

    ArrayList<WSDSample> result = new ArrayList<>();

    String directory = semcorDirectory + folder + tagfiles;
    File tempFolder = new File(directory);
    File[] listOfFiles;

    if (tempFolder.isDirectory()) {
      listOfFiles = tempFolder.listFiles();
      for (File file : listOfFiles) {

        ArrayList<WSDSample> list = getSemcorOneFileData(
            directory + file.getName(), wordTag);
        result.addAll(list);
      }
    }

    return result;

  }

  /**
   * Semcor reader: This reads all the files in semcor, and return all the
   * instances in the format {@link WSDSample} of a specific word
   * 
   * @param wordTag
   *          The word, of which we are looking for the instances
   * @return the list of the {@link WSDSample} instances of the word to
   *         disambiguate
   */
  public ArrayList<WSDSample> getSemcorData(String wordTag) {

    ArrayList<WSDSample> result = new ArrayList<>();

    for (String folder : folders) {
      ArrayList<WSDSample> list = getSemcorFolderData(folder, wordTag);
      result.addAll(list);
    }

    return result;

  }

  /**
   * Semcor reader: This reads all the files in semcor, and return all the
   * instances in the format {@link WSDSample} of a specific word
   * 
   * @param wordTag
   *          The word, of which we are looking for the instances
   * @return the stream of {@link WSDSample} of the word to disambiguate
   */
  public ObjectStream<WSDSample> getSemcorDataStream(String wordTag) {
    return ObjectStreamUtils.createObjectStream(getSemcorData(wordTag));
  }

}
