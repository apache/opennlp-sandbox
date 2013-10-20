/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2;

import java.util.List;
import java.util.Map;
import java.util.Set;
import opennlp.tools.namefind.TokenNameFinderModel;

/**
 *
 * @author Owner
 */
public interface Modelable extends ModelParameter{



  String annotate(String sentence, String namedEntity, String entityType);

  void writeAnnotatedSentences();

  Set<String> getAnnotatedSentences();

  void setAnnotatedSentences(Set<String> annotatedSentences);

  void addAnnotatedSentence(String annotatedSentence);

  void buildModel( String entityType);

  TokenNameFinderModel getModel();

  String[] tokenizeSentenceToWords(String sentence);
  

}
