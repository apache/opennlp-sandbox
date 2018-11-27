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

package opennlp.tools.lang.english;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;

import opennlp.tools.coref.DefaultLinker;
import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.Linker;
import opennlp.tools.coref.LinkerMode;
import opennlp.tools.coref.mention.DefaultParse;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.PTBMentionFinder;
import opennlp.tools.parser.Parse;


/**
 * This class perform coreference for treebank style parses.
 * It will only perform coreference over constituents defined in the trees and
 * will not generate new constituents for pre-nominal entities or sub-entities in
 * simple coordinated noun phrases.  This linker requires that named-entity information also be provided.
 * This information can be added to the parse using the -parse option with EnglishNameFinder.
 * 
 * @deprecated will be removed soon!
 */
@Deprecated
public class TreebankLinker extends DefaultLinker {

  public TreebankLinker(String project, LinkerMode mode) throws IOException {
    super(project,mode);
  }

  public TreebankLinker(String project, LinkerMode mode, boolean useDiscourseModel) throws IOException {
    super(project,mode,useDiscourseModel);
  }

  public TreebankLinker(String project, LinkerMode mode, boolean useDiscourseModel,
                        double fixedNonReferentialProbability) throws IOException {
    super(project,mode,useDiscourseModel,fixedNonReferentialProbability);
  }

  @Override
  protected void initMentionFinder() {
    mentionFinder = PTBMentionFinder.getInstance(headFinder);
  }

  private static void showEntities(DiscourseEntity[] entities) {
    for (int ei = 0, en = entities.length; ei < en;ei++) {
      System.out.println(ei + " " + entities[ei]);
    }
  }

  /**
   * Identitifies corefernce relationships for parsed input passed via standard in.
   * @param args The model directory.
   * @throws IOException when the model directory can not be read.
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: TreebankLinker model_directory < parses");
      System.exit(1);
    }
    BufferedReader in;
    int ai = 0;
    String dataDir = args[ai++];
    if (ai == args.length) {
      in = new BufferedReader(new InputStreamReader(System.in));
    }
    else {
      in = new BufferedReader(new FileReader(args[ai]));
    }
    Linker treebankLinker = new TreebankLinker(dataDir,LinkerMode.TEST);
    int sentenceNumber = 0;
    List<Mention> document = new ArrayList<Mention>();
    List<Parse> parses = new ArrayList<Parse>();
    for (String line = in.readLine();null != line;line = in.readLine()) {
      if (line.equals("")) {
        DiscourseEntity[] entities =
            treebankLinker.getEntities(document.toArray(new Mention[document.size()]));
        //showEntities(entities);
        new CorefParse(parses,entities).show();
        sentenceNumber = 0;
        document.clear();
        parses.clear();
      }
      else {
        Parse p = Parse.parseParse(line);
        parses.add(p);
        Mention[] extents = treebankLinker.getMentionFinder().getMentions(new DefaultParse(p,sentenceNumber));
        //construct new parses for mentions which don't have constituents.
        for (int ei = 0, en = extents.length; ei < en; ei++) {
          //System.err.println("PennTreebankLiner.main: "+ei+" "+extents[ei]);

          if (extents[ei].getParse() == null) {
            //not sure how to get head index, but its not used at this point.
            Parse snp = new Parse(p.getText(),extents[ei].getSpan(),"NML",1.0,0);
            p.insert(snp);
            extents[ei].setParse(new DefaultParse(snp,sentenceNumber));
          }

        }
        document.addAll(Arrays.asList(extents));
        sentenceNumber++;
      }
    }
    if (document.size() > 0) {
      DiscourseEntity[] entities = treebankLinker.getEntities(document.toArray(new Mention[document.size()]));
      //showEntities(entities);
      (new CorefParse(parses, entities)).show();
    }
  }
}


