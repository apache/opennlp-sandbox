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

import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import opennlp.modelbuilder.v2.SentenceProvider;

/**
 *
 * @author Owner
 */
public class MySQLSentenceProviderImpl implements SentenceProvider {

  Set<String> sentences = new HashSet<String>();

  @Override
  public Set<String> getSentences() {
    try {
      if (sentences.isEmpty()) {
        return getData();
      }
    } catch (Exception e) {
    }
    return sentences;
  }

  private Set<String> getData() throws Exception {

    Connection con = getMySqlConnection();
    if (con.isClosed()) {
      con = getMySqlConnection();
    }
    CallableStatement cs;
    cs = con.prepareCall("CALL getTrainingSentences()");

    ResultSet rs;
    try {
      rs = cs.executeQuery();
      while (rs.next()) {
        sentences.add(rs.getString(1));
      }

    } catch (SQLException ex) {
      throw ex;
    } catch (Exception e) {
      System.err.println(e);
    } finally {
      con.close();
    }

    return sentences;
  }

  private static Connection getMySqlConnection() throws Exception {
    // EntityLinkerProperties property = new EntityLinkerProperties(new File("c:\\temp\\opennlpmodels\\entitylinker.properties"));
    String driver = "org.gjt.mm.mysql.Driver";
    String url = "jdbc:mysql://localhost:3306/db";
    String username = "root";
    String password = "??";

    Class.forName(driver);
    Connection conn = DriverManager.getConnection(url, username, password);
    return conn;
  }

 private Map<String, String> params = new HashMap<String,String>();

  @Override
  public void setParameters(Map<String, String> params) {
    this.params = params;
  }
}
