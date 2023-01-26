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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.tika.Tika;

public class ClassifierTrainingSetIndexer {
  
  public static String resourceDir = new File(".").getAbsolutePath().replace("/.", "") + "/src/main/resources";
  public static String INDEX_PATH = "/classif", CLASSIF_TRAINING_CORPUS_PATH = "/training_corpus";
  protected ArrayList<File> queue = new ArrayList<>();
  Tika tika = new Tika();

  IndexWriter indexWriter = null;
  protected static String[] domains =  new String[] { "legal", "health", "computing", "engineering", "business" };
  private String absolutePathTrainingSet=null;

  public ClassifierTrainingSetIndexer() {

    try {
      initIndexWriter(resourceDir);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public ClassifierTrainingSetIndexer(String absolutePathTrainingSet) {
    this.absolutePathTrainingSet = absolutePathTrainingSet;
    try {
      initIndexWriter(resourceDir);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void indexTrainingSet() {

    try {
      if (absolutePathTrainingSet==null)
        indexFileOrDirectory(resourceDir
                + CLASSIF_TRAINING_CORPUS_PATH);
      else
        indexFileOrDirectory(
                this.absolutePathTrainingSet);

    } catch (IOException e1) {
      e1.printStackTrace();
    }
    try {
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

    List<File> files = new ArrayList<File>(queue);
    for (File f : files) {
      if (!f.getName().endsWith(".xml")) {

        try {
          Document doc = new Document();

          String name = f.getPath();
          String className = null;
          for (String d : domains) {
            if (name.indexOf(d) > -1) {
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
        try {
          Document doc = new Document();

          String name = new String(f.getPath());
          String[] nparts = name.split("/");
          int len = nparts.length;
          name = nparts[len - 2];

          FileReader fr = new FileReader(f);
          doc.add(new TextField("text", fr));

          doc.add(new StringField("path", f.getPath(),
                  Field.Store.YES));
          doc.add(new StringField("class", name, Field.Store.YES));
          try {

            indexWriter.addDocument(doc);

          } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not add: " + f);
          } finally {
            fr.close();
          }
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
    for (String d : domains) {
      if (path.indexOf("/"+d+"/") > -1) {
        className = d;
        break;
      }
    }
    return className;
  }

  public static void main(String[] args) {
    ClassifierTrainingSetIndexer indexer = null;
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
