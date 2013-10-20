/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2;

import java.util.Set;

/**
 *
 * @author Owner
 */
public interface SentenceProvider extends ModelParameter {

  Set<String> getSentences();
}
