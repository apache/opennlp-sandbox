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

package opennlp.tools.coref;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import opennlp.tools.commons.Trainer;
import opennlp.tools.coref.linker.DefaultLinker;
import opennlp.tools.coref.linker.Linker;
import opennlp.tools.coref.linker.LinkerMode;
import opennlp.tools.coref.linker.TreebankLinker;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.MentionFinder;
import opennlp.tools.coref.sim.GenderModel;
import opennlp.tools.coref.sim.NumberModel;
import opennlp.tools.coref.sim.SimilarityModel;
import opennlp.tools.coref.sim.TrainModel;
import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.ObjectStream;

/**
 * A {@link Trainer} implementation for co-reference resolution models.
 *
 * @see Trainer
 * @see CorefModel
 * @see CorefSample
 */
public class CorefTrainer extends AbstractTrainer implements Trainer {

  private static boolean containsToken(String token, Parse p) {
    for (Parse node : p.getTagNodes()) {
      if (node.getCoveredText().equals(token))
        return true;
    }
    return false;
  }
  
  private static Mention[] getMentions(CorefSample sample, MentionFinder mentionFinder) {
    List<Mention> mentions = new ArrayList<>();
    for (opennlp.tools.coref.mention.Parse corefParse : sample.getParses()) {
      Parse p = ((DefaultParse) corefParse).getParse();
      Mention[] extents = mentionFinder.getMentions(corefParse);
      for (Mention extent : extents) {
        if (extent.getParse() == null) {
          Stack<Parse> nodes = new Stack<>();
          nodes.add(p);

          while (!nodes.isEmpty()) {
            Parse node = nodes.pop();
            if (node.getSpan().equals(extent.getSpan()) && node.getType().startsWith("NML")) {
              DefaultParse corefParseNode = new DefaultParse(node, corefParse.getSentenceNumber());
              extent.setParse(corefParseNode);
              extent.setId(corefParseNode.getEntityId());
              break;
            }
            nodes.addAll(Arrays.asList(node.getChildren()));
          }
        }
      }
      mentions.addAll(Arrays.asList(extents));
    }
    return mentions.toArray(new Mention[0]);
  }
  
  public static void train(String modelDirectory, ObjectStream<CorefSample> samples,
      boolean useTreebank, boolean useDiscourseModel) throws IOException {
    
    TrainModel<SimilarityModel> simTrain =
            SimilarityModel.trainModel(modelDirectory + "/coref/sim");
    TrainModel<GenderModel> genTrain =
            GenderModel.trainModel(modelDirectory + "/coref/gen");
    TrainModel<NumberModel> numTrain =
            NumberModel.trainModel(modelDirectory + "/coref/num");
    
    Linker simLinker;
    
    if (useTreebank) {
      simLinker = new TreebankLinker(modelDirectory + "/coref/", LinkerMode.SIM);
    }
    else {
      simLinker = new DefaultLinker(modelDirectory + "/coref/" ,LinkerMode.SIM);
    }
    
    // TODO: Feed with training data ...
    for (CorefSample sample = samples.read(); sample != null; sample = samples.read()) {
      
      Mention[] mentions = getMentions(sample, simLinker.getMentionFinder());
      MentionContext[] extentContexts = simLinker.constructMentionContexts(mentions);
      
      simTrain.setExtents(extentContexts);
      genTrain.setExtents(extentContexts);
      numTrain.setExtents(extentContexts);
    }

    final SimilarityModel simModel = simTrain.trainModel();
    final GenderModel genderModel = genTrain.trainModel();
    final NumberModel numberModel = numTrain.trainModel();

    // Done with similarity training, now train the linkers
 
    // Training data needs to be read in again and the stream must be reset
    samples.reset();
    
    // Now create linkers
    Linker trainLinker;
    if (useTreebank) {
      trainLinker = new TreebankLinker(modelDirectory, LinkerMode.TRAIN,
              simModel, genderModel, numberModel, useDiscourseModel, -1);
    } else {
      trainLinker = new DefaultLinker(modelDirectory, LinkerMode.TRAIN,
              simModel, genderModel, numberModel, useDiscourseModel, -1);
    }
    
    for (CorefSample sample = samples.read(); sample != null; sample = samples.read())  {
      Mention[] mentions = getMentions(sample, trainLinker.getMentionFinder());
      trainLinker.setEntities(mentions);
    }
    trainLinker.train();
  }

}
