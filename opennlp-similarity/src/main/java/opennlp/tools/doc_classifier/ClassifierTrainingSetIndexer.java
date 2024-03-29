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
package opennlp.tools.doc_classifier;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.Tika;

public class ClassifierTrainingSetIndexer {
  
  private static final String[] DOMAINS = new String[] { "legal", "health", "computing", "engineering", "business" };
  private static final String RESOURCE_DIR = new File(".").getAbsolutePath().replace("/.", "") + "/src/main/resources";
  static final String INDEX_PATH = "/classif";
  static final String CLASSIF_TRAINING_CORPUS_PATH = "/training_corpus";
  protected final ArrayList<File> queue = new ArrayList<>();
  private final Tika tika = new Tika();

  private IndexWriter indexWriter = null;
  private String absolutePathTrainingSet = null;

  public ClassifierTrainingSetIndexer() {

    try {
      initIndexWriter(RESOURCE_DIR);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public ClassifierTrainingSetIndexer(String absolutePathTrainingSet) {
    this.absolutePathTrainingSet = absolutePathTrainingSet;
    try {
      initIndexWriter(RESOURCE_DIR);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void indexTrainingSet() {

    try {
      indexFileOrDirectory(Objects.requireNonNullElseGet(absolutePathTrainingSet,
              () -> RESOURCE_DIR + CLASSIF_TRAINING_CORPUS_PATH));
      indexWriter.commit();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  /*
  private void indexTrainingSample(String text, String flag, int id)
          throws IOException {

      Document doc = new Document();
      doc.add(new StringField("id", new Integer(id).toString(),
              Field.Store.YES));
      doc.add(new TextField("text", text.toLowerCase(), Field.Store.YES));
      doc.add(new StringField("class", flag.toLowerCase(), Field.Store.YES));
      indexWriter.addDocument(doc);

  }
  */

  private void addFiles(File file) {

    if (!file.exists()) {
      System.out.println(file + " does not exist.");
    }
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        if (f.getName().startsWith("."))
          continue;
        addFiles(f);
        System.out.println(f.getName());
      }
    } else {
      queue.add(file);

    }
  }

  // index last folder name, before filename itself

  public void indexFileOrDirectory(String fileName) throws IOException {
    addFiles(new File(fileName));

    List<File> files = new ArrayList<>(queue);
    for (File f : files) {
      if (!f.getName().endsWith(".xml")) {

        try {
          Document doc = new Document();

          String name = f.getPath();
          String className = null;
          for (String d : DOMAINS) {
            if (name.contains(d)) {
              className = d;
              break;
            }
          }

          try {
            doc.add(new TextField("text", tika.parse(f)));
          } catch (Exception e1) {
            e1.printStackTrace();
          }

          doc.add(new StringField("path", f.getPath(),
                  Field.Store.YES));
          doc.add(new StringField("class", className, Field.Store.YES));
          try {

            indexWriter.addDocument(doc);

          } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not add: " + f);
          }
        } catch (Exception ee) {
          ee.printStackTrace();
        }
      } else { // for xml files
        try (FileReader fr = new FileReader(f)) {
          Document doc = new Document();

          String name = f.getPath();
          String[] nparts = name.split("/");
          int len = nparts.length;
          name = nparts[len - 2];

          doc.add(new TextField("text", fr));
          doc.add(new StringField("path", f.getPath(), Field.Store.YES));
          doc.add(new StringField("class", name, Field.Store.YES));
          indexWriter.addDocument(doc);
        } catch (Exception ee) {
          ee.printStackTrace();
        }
      }

      queue.clear();
    }
  }

  public static String getIndexDir() {
    try {
      return new File(".").getCanonicalPath() + INDEX_PATH;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private void initIndexWriter(String dir) throws Exception {

    Directory indexDir = null;

    try {
      indexDir = FSDirectory.open(new File(dir + INDEX_PATH).toPath());
    } catch (IOException e) {
      e.printStackTrace();
    }

    IndexWriterConfig luceneConfig = new IndexWriterConfig(new StandardAnalyzer());
    luceneConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    indexWriter = new IndexWriter(indexDir, luceneConfig);

  }

  void close() {
    try {
      indexWriter.commit();
      indexWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static String getCategoryFromFilePath(String path){
    String className = null;
    for (String d : DOMAINS) {
      if (path.contains("/" + d + "/")) {
        className = d;
        break;
      }
    }
    return className;
  }

  public static void main(String[] args) {
    ClassifierTrainingSetIndexer indexer;
    if (args!=null && args.length==1){
      String relativeDirWithTrainingCorpus = args[0];
      // expect corpus relative to 'resource' directory, such as 'training_corpus'
      if (!relativeDirWithTrainingCorpus.startsWith("/"))
        relativeDirWithTrainingCorpus = "/"+relativeDirWithTrainingCorpus;
      indexer = new ClassifierTrainingSetIndexer(relativeDirWithTrainingCorpus);
    } else {
      // expect corpus in the default location, "/training_corpus" in the resource directory
      indexer = new ClassifierTrainingSetIndexer();
    }
    try {
      indexer.indexTrainingSet();
    } catch (Exception e) {
      e.printStackTrace();
    }
    indexer.close();
  }

}
