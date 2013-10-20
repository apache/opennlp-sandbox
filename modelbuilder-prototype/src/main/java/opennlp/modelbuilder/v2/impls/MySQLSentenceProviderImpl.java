/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
    String url = "jdbc:mysql://127.0.0.1:3306/wink";
    String username = "root";
    String password = "559447";

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
