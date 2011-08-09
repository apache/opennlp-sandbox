/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.opennlp.corpus_server.store;

import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.derby.jdbc.EmbeddedDataSource40;

public class DerbyCorporaStore extends AbstractCorporaStore {

  private final static Logger LOGGER = Logger.getLogger(
      DerbyCorporaStore.class .getName());
  
  private DataSource dataSource = null;

  @Override
  public void initialize() {

    EmbeddedDataSource40 ds = new EmbeddedDataSource40(); 
    ds.setDatabaseName("XmiCasDB");
    ds.setCreateDatabase("create");
    
    dataSource = ds;
  }

  @Override
  public void createCorpus(String corpusName, byte[] typeSystemBytes)
      throws IOException {
    
    try {
      Connection conn = dataSource.getConnection();
      Statement s = conn.createStatement();
      s.execute("create table " + corpusName + 
          "(name varchar(1024), cas blob, unique (name))");

      // Insert the type system
      PreparedStatement ps = conn.prepareStatement("insert into " + corpusName
          + " values (?, ?)");

      ps.setString(1, "_typesystem");

      Blob b = conn.createBlob();
      b.setBytes(1, typeSystemBytes);
      ps.setBlob(2, b);

      ps.executeUpdate();

      conn.commit();
      
      ps.close();
      conn.close();

    } catch (SQLException e) {
      
      if (LOGGER.isLoggable(Level.SEVERE)) {
        LOGGER.log(Level.SEVERE, "Failed to create corpus: " + 
            corpusName, e);
      }
      
      throw new IOException(e);
    }
  }

  @Override
  public CorpusStore getCorpus(String corpusId) {
    return new DerbyCorpusStore(dataSource, this, corpusId);
  }

  @Override
  public void shutdown() throws IOException {
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      if (((e.getErrorCode() == 50000) && ("XJ015".equals(e.getSQLState())))) {
        // We got the expected exception

        // Note that for single database shutdown, the expected
        // SQL state is "08006", and the error code is 45000.
      } else {
        // if the error code or SQLState is different, we have
        // an unexpected exception (shutdown failed)
        throw new IOException(e);
      }
    }
  }
}
