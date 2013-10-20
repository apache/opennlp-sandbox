/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2;

import java.util.Collection;
import java.util.Set;

/**
 *
 * @author Owner
 */
public interface ModelGenerationValidator extends ModelParameter {

  Boolean validSentence(String sentence);

  Boolean validNamedEntity(String namedEntity);
  


  Collection<String> getBlackList();
}
