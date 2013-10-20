/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2.impls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import opennlp.modelbuilder.v2.KnownEntityProvider;

/**
 *
 * @author Owner
 */
public class PersonKnownEntityProviderImpl implements KnownEntityProvider {

  Set<String> ret = new HashSet<String>();

  @Override
  public Set<String> getKnownEntities() {
    if (ret.isEmpty()) {
      ret.add("Barack Obama");
      ret.add("Mitt Romney");
      ret.add("John Doe");
      ret.add("Bill Gates");
      ret.add("Nguyen Tan Dung");
      ret.add("Hassanal Bolkiah");
      ret.add("Bashar al-Assad");
      ret.add("Faysal Khabbaz Hamou");
      ret.add("Dr Talwar");
      ret.add("Mr. Bolkiah");
      ret.add("Bashar");
      ret.add("Romney");
      ret.add("Obama");
      ret.add("the President");
      ret.add("Mr. Gates");
      ret.add("Romney");



      ret.add("Xi Jinping");
      ret.add("Hassanal Bolkiah");
      ret.add("Leon Panetta");
      ret.add("Paul Beales");
      ret.add("Mr Rajapaksa");
      ret.add("Mohammed ");
      ret.add("Ieng Thirith");
      ret.add("Mr Xi");
      ret.add("John Sudworth");
      ret.add("Ieng Thirith");
      ret.add("Aung San Suu Kyi");

      ret.add("Khorshid");
      ret.add("Karrie Webb");
      ret.add("Doyle McManus");
      ret.add("Pope John Paul");
      ret.add("Roland Buerk");
      ret.add("Paul Ryan");
      ret.add("Tammy Baldwin");
      ret.add("Ben Unger");
      ret.add("Chris Christie");
      ret.add("Mary Magdalene");
      ret.add("George Walker Bush");
      ret.add("Melendez-Martinez");
      ret.add("Osiel Cardenas Guillen");
      ret.add("President Molina");
      ret.add("Lubaina Himid");
      ret.add("Elizabeth Frink");
      ret.add("Graham Sutherland");
      ret.add("Gorman Adams");
      ret.add("Peter Sheasby");
      ret.add("Andrew Walker");
      ret.add("Elias Garcia Martinez");
      ret.add("Elias Martinez");

    }
    return ret;
  }

  @Override
  public String getKnownEntitiesType() {
    return "person";
  }

  @Override
  public void addKnownEntity(String unambiguousEntity) {
    ret.add(unambiguousEntity);
  }

  private Map<String, String> params = new HashMap<String,String>();

  @Override
  public void setParameters(Map<String, String> params) {
    this.params = params;
  }
}
