/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package opennlp.modelbuilder.v2.impls;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.modelbuilder.v2.KnownEntityProvider;

/**
 *
 * @author Owner
 */
public class FileKnownEntityProvider implements KnownEntityProvider {
  private Map<String, String> params = new HashMap<String, String>();
  Set<String> knownEntities = new HashSet<String>();

  @Override
  public Set<String> getKnownEntities() {
    if (knownEntities.isEmpty()) {
      try {
        InputStream fis;
        BufferedReader br;
        String line;

        fis = new FileInputStream(params.get("knownentityfile"));
        br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
        while ((line = br.readLine()) != null) {
          knownEntities.add(line);
        }

        // Done with the file
        br.close();
        br = null;
        fis = null;
      } catch (FileNotFoundException ex) {
        Logger.getLogger(FileKnownEntityProvider.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IOException ex) {
        Logger.getLogger(FileKnownEntityProvider.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    return knownEntities;
  }

  @Override
  public void addKnownEntity(String unambiguousEntity) {
    knownEntities.add(unambiguousEntity);
  }

  @Override
  public String getKnownEntitiesType() {
 
    return params.get("knownentitytype");
  }



  @Override
  public void setParameters(Map<String, String> params) {
    this.params = params;
  }
}
