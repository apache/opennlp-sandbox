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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import opennlp.tools.ProfileReaderWriter;

/**
 * This class stores the taxonomy on the file-system.
 * 
 * @author Boris
 */
public class TaxonomySerializer implements Serializable {

  @Serial
  private static final long serialVersionUID = 7431412616514648388L;
  private static final String CSV = ".csv";

  private final Map<String, List<List<String>>> lemma_ExtendedAssocWords;
  private final Map<List<String>, List<List<String>>> assocWords_ExtendedAssocWords;

  public TaxonomySerializer(Map<String, List<List<String>>> lemma_ExtendedAssocWords,
      Map<List<String>, List<List<String>>> assocWords_ExtendedAssocWords) {

    this.lemma_ExtendedAssocWords = lemma_ExtendedAssocWords;
    this.assocWords_ExtendedAssocWords = assocWords_ExtendedAssocWords;
  }

  Map<String, List<List<String>>> getLemma_ExtendedAssocWords() {
    return lemma_ExtendedAssocWords;
  }

  void writeTaxonomy(Path outputDir, String filename) {
    final String outDir = outputDir.toAbsolutePath().toString();
    final String datFile = Paths.get(outDir, filename).toString();
    final String csvFile = Paths.get(outDir, filename + CSV).toString();
    final String csvListFile = Paths.get(outDir,filename + "_ListEntries" + CSV).toString();
    try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(datFile)))) {
      out.writeObject(this);
    } catch (IOException ex) {
      ex.printStackTrace();
    }

    List<String[]> taxo_list = new ArrayList<>();
    List<String> entries = new ArrayList<>(lemma_ExtendedAssocWords.keySet());
    for(String e: entries){
     List<String> lines = new ArrayList<>();
     lines.add(e);
     for(List<String> ls: lemma_ExtendedAssocWords.get(e)){
       lines.add(ls.toString());
     }
     taxo_list.add(lines.toArray(new String[0]));
    }
    ProfileReaderWriter.writeReport(taxo_list, csvFile);

    taxo_list = new ArrayList<>();
    List<List<String>> entriesList = new ArrayList<>(assocWords_ExtendedAssocWords.keySet());
    for(List<String> e: entriesList){
     List<String> lines = new ArrayList<>(e);
     for(List<String> ls: assocWords_ExtendedAssocWords.get(e)){
       lines.add(ls.toString());
     }
     taxo_list.add(lines.toArray(new String[0]));
    }
    ProfileReaderWriter.writeReport(taxo_list, csvListFile);
  }

  static TaxonomySerializer readTaxonomy(String filename) {
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (ObjectInputStream in = new ObjectInputStream(cl.getResourceAsStream(filename))) {
      return (TaxonomySerializer) in.readObject();
    } catch (IOException | ClassNotFoundException ex) {
      throw new RuntimeException(ex.getLocalizedMessage(), ex);
    }
  }
}
