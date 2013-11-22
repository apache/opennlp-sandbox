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
package opennlp.modelbuilder.v2.impls;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.modelbuilder.v2.KnownEntityProvider;

/**
 *

 */
public class LocationKnownEntityProviderImpl implements KnownEntityProvider {
 
  Set<String> ret = new HashSet<String>();

  @Override
  public Set<String> getKnownEntities() {
    if (ret.isEmpty()) {
      try {
        getData();
      } catch (Exception ex) {
        Logger.getLogger(LocationKnownEntityProviderImpl.class.getName()).log(Level.SEVERE, null, ex);
      }

    }
    return ret;
  }
   private Set<String> getData() throws Exception {

    Connection con = getMySqlConnection();
    if (con.isClosed()) {
      con = getMySqlConnection();
    }
    CallableStatement cs;
    cs = con.prepareCall("CALL getcountrylist()");

    ResultSet rs;
    try {
      rs = cs.executeQuery();
      while (rs.next()) {
        ret.add(rs.getString("full_name_nd_ro"));
      }

    } catch (SQLException ex) {
      throw ex;
    } catch (Exception e) {
      System.err.println(e);
    } finally {
      con.close();
    }

    return ret;
  }
  private static Connection getMySqlConnection() throws Exception {
    // EntityLinkerProperties property = new EntityLinkerProperties(new File("c:\\temp\\opennlpmodels\\entitylinker.properties"));
    String driver = "org.gjt.mm.mysql.Driver";
    String url = "jdbc:mysql://127.0.0.1:3306/world";
    String username = "root";
    String password = "559447";

    Class.forName(driver);
    Connection conn = DriverManager.getConnection(url, username, password);
    return conn;
  }
  @Override
  public String getKnownEntitiesType() {
    return "location";
  }

  @Override
  public void addKnownEntity(String unambiguousEntity) {
    ret.add(unambiguousEntity);
  }

 private Map<String, String> params = new HashMap<String, String>();

  @Override
  public void setParameters(Map<String, String> params) {
    this.params = params;
  }
}
