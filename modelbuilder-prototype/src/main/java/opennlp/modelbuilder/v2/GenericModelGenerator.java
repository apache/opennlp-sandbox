/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2;

import java.util.HashMap;
import java.util.Map;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;

/**
 *
 *
 */
public class GenericModelGenerator implements SemiSupervisedModelGenerator{
 private Map<String, String> params = new HashMap<String, String>();

  @Override
  public void setParameters(Map<String, String> params) {
    this.params = params;
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
      System.out.println("\t\twriting annotated sentences....: ");
      modelable.writeAnnotatedSentences();
      modelable.buildModel(knownEntityProvider.getKnownEntitiesType());
      NameFinderME nf = new NameFinderME(modelable.getModel());
      System.out.println("\t\tannotated sentences: " + modelable.getAnnotatedSentences().size());
      System.out.println("\tPerforming NER");
      for (String sentence : sentenceProvider.getSentences()) {
        if (!validator.validSentence(sentence)) {
          continue;
        }
        String[] tokens = modelable.tokenizeSentenceToWords(sentence);

        Span[] find = nf.find(tokens);
        nf.clearAdaptiveData();

        String[] namedEntities = Span.spansToStrings(find, tokens);

        for (String namedEntity : namedEntities) {
          if (validator.validNamedEntity(namedEntity)) {
            knownEntityProvider.addKnownEntity(namedEntity);
            modelable.addAnnotatedSentence(modelable.annotate(sentence, namedEntity, knownEntityProvider.getKnownEntitiesType()));

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
