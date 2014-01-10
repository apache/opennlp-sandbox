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
package opennlp.addons.modelbuilder;

import java.io.File;
import opennlp.addons.modelbuilder.impls.BaseModelBuilderParams;
import opennlp.addons.modelbuilder.impls.FileKnownEntityProvider;
import opennlp.addons.modelbuilder.impls.FileModelValidatorImpl;
import opennlp.addons.modelbuilder.impls.FileSentenceProvider;
import opennlp.addons.modelbuilder.impls.GenericModelGenerator;
import opennlp.addons.modelbuilder.impls.GenericModelableImpl;

/**
 *
 * Utilizes the filebased implementations to produce an NER model from user
 * The basic processing is such
 * read in the list of known entities
 * annotate the sentences based on the list of known entities
 * create a model from the annotations
 * perform NER with the model on the sentences
 * add the NER results to the annotations
 * rebuild the model
 * loop
 * defined data
 */
public class DefaultModelBuilderUtil {

  /**
   *
   * @param sentences                a file that contains one sentence per line.
   *                                 There should be at least 15K sentences
   *                                 consisting of a representative sample from
   *                                 user data
   * @param knownEntities            a file consisting of a simple list of
   *                                 unambiguous entities, one entry per line.
   *                                 For instance, if one was trying to build a
   *                                 person NER model then this file would be a
   *                                 list of person names that are unambiguous
   *                                 and are known to exist in the sentences
   *                                 file
   * @param knownEntitiesBlacklist   This file contains a list of known bad hits
   *                                 that the NER phase of this processing might
   *                                 catch early one before the model iterates
   *                                 to maturity
   * @param modelOutFile             the location where the model will be
   *                                 written to
   * @param annotatedSentenceOutFile where the annotated sentences produced by
   *                                 this process will be written to
   * @param namedEntityType          the type of entity... for example, person,
   *                                 location, organization...
   * @param iterations               how many times to repeat the iterative loop
   *                                 of annotation, model generation, and NER
   */
  public static void generateModel(File sentences, File knownEntities, File knownEntitiesBlacklist,
          File modelOutFile, File annotatedSentenceOutFile, String namedEntityType, int iterations) {
    SemiSupervisedModelGenerator modelGenerator = new GenericModelGenerator();
    BaseModelBuilderParams params = new BaseModelBuilderParams();
    params.setAnnotatedTrainingDataFile(annotatedSentenceOutFile);
    params.setSentenceFile(sentences);
    params.setEntityType(namedEntityType);
    params.setKnownEntitiesFile(knownEntities);
    params.setModelFile(modelOutFile);
    params.setKnownEntityBlacklist(knownEntitiesBlacklist);
    /**
     * sentence providers feed this process with user data derived sentences
     * this impl just reads line by line through a file
     */
    SentenceProvider sentenceProvider = new FileSentenceProvider();
    sentenceProvider.setParameters(params);
    /**
     * KnownEntityProviders provide a seed list of known entities... such as
     * Barack Obama for person, or Germany for location obviously these would
     * want to be prolific, non ambiguous names
     */
    KnownEntityProvider knownEntityProvider = new FileKnownEntityProvider();
    knownEntityProvider.setParameters(params);
    /**
     * ModelGenerationValidators try to weed out bad hits by the iterations of
     * the name finder. Since this is a recursive process, with each iteration
     * the namefinder will get more and more greedy if bad entities are allowed
     * in this provides a mechanism for throwing out obviously bad hits. A good
     * impl may be to make sure a location is actually within a noun phrase
     * etc...users can make this as specific as they need for their dat and
     * their use case
     */
    ModelGenerationValidator validator = new FileModelValidatorImpl();
    validator.setParameters(params);
    /**
     * Modelable's write and read the annotated sentences, as well as create and
     * write the NER models
     */
    Modelable modelable = new GenericModelableImpl();
    modelable.setParameters(params);

    /**
     * the modelGenerator actually runs the process with a set number of
     * iterations... could be better by actually calculating the diff between
     * runs and stopping based on a thresh, but for extrememly large sentence
     * sets this may be too much.
     */
    modelGenerator.build(sentenceProvider, knownEntityProvider, validator, modelable, iterations);

  }
}
