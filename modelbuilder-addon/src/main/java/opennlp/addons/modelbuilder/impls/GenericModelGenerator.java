/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.addons.modelbuilder.impls;

import java.util.HashMap;
import java.util.Map;
import opennlp.addons.modelbuilder.KnownEntityProvider;
import opennlp.addons.modelbuilder.ModelGenerationValidator;
import opennlp.addons.modelbuilder.Modelable;
import opennlp.addons.modelbuilder.SemiSupervisedModelGenerator;
import opennlp.addons.modelbuilder.SentenceProvider;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;

/**
 *
 * Generic impl that handles all processing using the default file implementations
 */
public class GenericModelGenerator implements SemiSupervisedModelGenerator {

  private Map<String, String> params = new HashMap<String, String>();

  @Override
  public void setParameters(BaseModelBuilderParams params) {
    this.params = params.getAdditionalParams();
  }

  @Override
  public void build(SentenceProvider sentenceProvider, KnownEntityProvider knownEntityProvider,
          ModelGenerationValidator validator, Modelable modelable, int iterations) {
    for (int iteration = 0; iteration < iterations; iteration++) {
      System.out.println("ITERATION: " + iteration);
      System.out.println("\tPerfoming Known Entity Annotation");
      System.out.println("\t\tknowns: " + knownEntityProvider.getKnownEntities().size());
      System.out.println("\t\treading data....: ");
      for (String sentence : sentenceProvider.getSentences()) {
        for (String knownEntity : knownEntityProvider.getKnownEntities()) {
          if (sentence.contains(knownEntity)) {
            //if the same sentence has multiple hits should they be annotated separately?
            modelable.addAnnotatedSentence(modelable.annotate(sentence, knownEntity, knownEntityProvider.getKnownEntitiesType()));
          }
        }
      }
      if (sentenceProvider.getSentences().isEmpty()) {
        System.out.println("No sentences in file");
        return;
      }
      if (knownEntityProvider.getKnownEntities().isEmpty()) {
        System.out.println("No known entities in file");
        return;
      }
      System.out.println("\t\twriting annotated sentences....: ");
      modelable.writeAnnotatedSentences();
          System.out.println("\t\tbuilding model.... ");
      modelable.buildModel(knownEntityProvider.getKnownEntitiesType());
      System.out.println("\t\tmodel building complete.... ");
      NameFinderME nf = new NameFinderME(modelable.getModel());
      System.out.println("\t\tannotated sentences: " + modelable.getAnnotatedSentences().size());
      System.out.println("\tPerforming NER with new model");
      System.out.println("\t\tPrinting NER Results. Add undesired results to the blacklist file and start over");
      for (String sentence : sentenceProvider.getSentences()) {
        if (!validator.validSentence(sentence)) {
          continue;
        }
        String[] tokens = modelable.tokenizeSentenceToWords(sentence);

        Span[] find = nf.find(tokens);
        nf.clearAdaptiveData();

        String[] namedEntities = Span.spansToStrings(find, tokens);

        for (String namedEntity : namedEntities) {
          System.out.println("\t\t" + namedEntity);
          if (validator.validNamedEntity(namedEntity)) {

            knownEntityProvider.addKnownEntity(namedEntity);
            modelable.addAnnotatedSentence(modelable.annotate(sentence, namedEntity, knownEntityProvider.getKnownEntitiesType()));

          } else {
            System.out.println("\t\t" + namedEntity + "...already blacklisted");
          }
        }
      }
      System.out.println("\t\tannotated sentences: " + modelable.getAnnotatedSentences().size());
      System.out.println("\t\tknowns: " + knownEntityProvider.getKnownEntities().size());
    }
    modelable.writeAnnotatedSentences();
    modelable.buildModel(knownEntityProvider.getKnownEntitiesType());
  }
}
