/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2;

/**
 *
 * @author Owner
 */
public interface SemiSupervisedModelGenerator extends ModelParameter {

  void build(SentenceProvider sentenceProvider, KnownEntityProvider knownEntityProvider, 
          ModelGenerationValidator validator, Modelable modelable, int iterations);
}
