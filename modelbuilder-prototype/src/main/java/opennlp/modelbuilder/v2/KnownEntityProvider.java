/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2;

import java.util.List;
import java.util.Set;



/**
 *
 * @author Owner
 */
public interface KnownEntityProvider extends ModelParameter{
  /**
 * returns a list of known non ambiguous entities.
 * @return a set of entities
 */
  Set<String> getKnownEntities();
/**
 * adds to the set of known entities. Overriding classes should hold this list in a class level set.
 * @param unambiguousEntity 
 */
  void addKnownEntity(String unambiguousEntity);
/**
 * defines the type of entity that the set contains, ie person, location, organization.
 * @return 
 */
  String getKnownEntitiesType();
  
  
  
}
