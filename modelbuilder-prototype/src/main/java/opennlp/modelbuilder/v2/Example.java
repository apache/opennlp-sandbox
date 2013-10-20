/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2;

import java.util.HashMap;
import java.util.Map;
import opennlp.modelbuilder.v2.impls.FileKnownEntityProvider;
import opennlp.modelbuilder.v2.impls.FileModelValidatorImpl;
import opennlp.modelbuilder.v2.impls.FileSentenceProvider;
import opennlp.modelbuilder.v2.impls.ModelableImpl;

/**
 *
 * @author Owner
 */
public class Example {

  public static void main(String[] args) {

    GenericModelGenerator modelGenerator = new GenericModelGenerator();
    //every component has a map as a place to recieve params
    //these are required for the current file-based impls
    Map<String, String> params = new HashMap<String, String>();
    params.put("sentencesfile", "/the/file");
    params.put("knownentityfile", "/the/file");
    params.put("knownentitytype", "person");
    params.put("blacklistfile", "/the/file");
    params.put("modelablepath", "/the/file");

    /**
     * sentence providers feed this process with user data derived sentences
     * this impl just reads line by line through a file
     */
    SentenceProvider sentenceProvider = new FileSentenceProvider();
    sentenceProvider.setParameters(params);
    /**
     *KnownEntityProviders provide a seed list of known entities... such as Barack Obama for person, or Germany for location
     * obviously these would want to be prolific, non ambiguous names
     */
    KnownEntityProvider knownEntityProvider = new FileKnownEntityProvider();
    knownEntityProvider.setParameters(params);
    /**
     * ModelGenerationValidators try to weed out bad hits by the iterations of the name finder.
     * Since this is a recursive process, with each iteration the namefinder will get more and more greedy if bad entities are allowed in
     * this provides a mechanism for throwing out obviously bad hits.
     * A good impl may be to make sure a location is actually within a noun phrase etc...users can make this as specific as they need for their dat
     * and their use case
     */
    ModelGenerationValidator validator = new FileModelValidatorImpl();
    validator.setParameters(params);
    /**
     * Modelable's write and read the annotated sentences, as well as create and write the NER models
     */

    Modelable modelable = new ModelableImpl();
    modelable.setParameters(params);

    /**
     * the modelGenerator actually runs the process with a set number of iterations... could be better by actually calculating the
     * diff between runs and stopping based on a thresh, but for extrememly large sentence sets this may be too much.
     */
    modelGenerator.build(sentenceProvider, knownEntityProvider, validator, modelable, 3);

  }
}
