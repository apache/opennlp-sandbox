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
package opennlp.tools.similarity.apps.taxo_builder;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class stores the taxonomy on the file-system
 * 
 * @author Boris
 * 
 */
public class TaxonomySerializer implements Serializable {

  private static final long serialVersionUID = 7431412616514648388L;
  private Map<String, List<List<String>>> lemma_ExtendedAssocWords = new HashMap<String, List<List<String>>>();
  private Map<List<String>, List<List<String>>> assocWords_ExtendedAssocWords = new HashMap<List<String>, List<List<String>>>();

  public TaxonomySerializer(
      Map<String, List<List<String>>> lemma_ExtendedAssocWords,
      Map<List<String>, List<List<String>>> assocWords_ExtendedAssocWords) {

    this.lemma_ExtendedAssocWords = lemma_ExtendedAssocWords;
    this.assocWords_ExtendedAssocWords = assocWords_ExtendedAssocWords;
  }

  public TaxonomySerializer() {
    // TODO Auto-generated constructor stub
  }

  public Map<List<String>, List<List<String>>> getAssocWords_ExtendedAssocWords() {
    return assocWords_ExtendedAssocWords;
  }

  public Map<String, List<List<String>>> getLemma_ExtendedAssocWords() {
    return lemma_ExtendedAssocWords;
  }

  public void setLemma_ExtendedAssocWords(
      Map<String, List<List<String>>> lemma_ExtendedAssocWords) {
    this.lemma_ExtendedAssocWords = lemma_ExtendedAssocWords;
  }

  public void setAssocWords_ExtendedAssocWords(
      Map<List<String>, List<List<String>>> assocWords_ExtendedAssocWords) {
    this.assocWords_ExtendedAssocWords = assocWords_ExtendedAssocWords;
  }

  public void writeTaxonomy(String filename) {
    FileOutputStream fos = null;
    ObjectOutputStream out = null;
    try {
      fos = new FileOutputStream(filename);
      out = new ObjectOutputStream(fos);
      out.writeObject(this);
      out.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    }

  }

  public static TaxonomySerializer readTaxonomy(String filename) {
    TaxonomySerializer data = null;
    FileInputStream fis = null;
    ObjectInputStream in = null;
    try {
      fis = new FileInputStream(filename);
      in = new ObjectInputStream(fis);
      data = (TaxonomySerializer) in.readObject();
      in.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    } catch (ClassNotFoundException ex) {
      ex.printStackTrace();
    }

    // System.out.print(data.lemma_ExtendedAssocWords);

    return data;

  }
}
