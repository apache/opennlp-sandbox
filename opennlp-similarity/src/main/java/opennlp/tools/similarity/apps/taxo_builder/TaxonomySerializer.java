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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.jsmlearning.ProfileReaderWriter;

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

     String csvFilename = filename+".csv";
     List<String[]> taxo_list = new  ArrayList<String[]>();
     List<String> entries = new ArrayList<String>(lemma_ExtendedAssocWords.keySet());
     for(String e: entries){
    	 List<String> lines = new ArrayList<String>();
    	 lines.add(e);
    	 for(List<String> ls: lemma_ExtendedAssocWords.get(e)){
    		 lines.add(ls.toString());
    	 }
    	 taxo_list.add((String[])lines.toArray(new String[0]));
     }
     ProfileReaderWriter.writeReport(taxo_list, csvFilename);
     
     String csvFilenameListEntries = filename+"_ListEntries.csv";
     taxo_list = new  ArrayList<String[]>();
     List<List<String>> entriesList = new ArrayList<List<String>>( assocWords_ExtendedAssocWords.keySet());
     for(List<String> e: entriesList){
       List<String> lines = new ArrayList<String>(e);
    	 for(List<String> ls: assocWords_ExtendedAssocWords.get(e)){
    		 lines.add(ls.toString());
    	 }
    	 taxo_list.add((String[])lines.toArray(new String[0]));
     }
     ProfileReaderWriter.writeReport(taxo_list, csvFilenameListEntries);
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
