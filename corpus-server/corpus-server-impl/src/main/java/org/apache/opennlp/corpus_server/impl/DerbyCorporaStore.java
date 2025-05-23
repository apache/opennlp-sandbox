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

package org.apache.opennlp.corpus_server.impl;

import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;
import org.slf4j.LoggerFactory;

import org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.apache.opennlp.corpus_server.store.AbstractCorporaStore;
import org.apache.opennlp.corpus_server.store.CorporaChangeListener;
import org.apache.opennlp.corpus_server.store.CorpusStore;

public class DerbyCorporaStore extends AbstractCorporaStore {

  private final static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DerbyCorporaStore.class.getName());
  public static final String DB_NAME = "XmiCasDB";

  private DataSource dataSource = null;

  @Override
  public void initialize(String basePath) {

    EmbeddedDataSource ds = new EmbeddedConnectionPoolDataSource();
    String dbName = DB_NAME;
    if (basePath != null) {
      dbName = basePath + DB_NAME;
    }
    ds.setDatabaseName(dbName);
    ds.setCreateDatabase("create");

    dataSource = ds;
  }

  private Set<String> getAllTables(Connection targetDBConn) throws SQLException {
    Set<String> tables = new HashSet<>();
    DatabaseMetaData md = targetDBConn.getMetaData();
    readTable(tables, md, "TABLE", null);
    readTable(tables, md, "VIEW", null);
    return tables;
  }

  private void readTable(Set<String> set, DatabaseMetaData md, String searchCriteria, String schema)
          throws SQLException {
    try (ResultSet rs = md.getTables(null, schema, null, new String[]{ searchCriteria })) {
      while (rs.next()) {
        set.add(rs.getString("TABLE_NAME").toLowerCase());
      }
    }
  }

  @Override
  public void createCorpus(String corpusName, byte[] typeSystemBytes, byte[] indexMapping)
      throws IOException {
    
    try (Connection conn = dataSource.getConnection()) {
      Set<String> existingTables = getAllTables(conn);
      if (!existingTables.contains(corpusName)) {
        Statement s = conn.createStatement();
        s.execute("create table " + corpusName + "(name varchar(1024), cas blob, unique (name))");
        s.close();
        // Insert the type system
        PreparedStatement typeSystemPS = conn.prepareStatement("insert into " + corpusName
                + " values (?, ?)");

        typeSystemPS.setString(1, "_typesystem");

        Blob typeSystemBlob = conn.createBlob();
        typeSystemBlob.setBytes(1, typeSystemBytes);
        typeSystemPS.setBlob(2, typeSystemBlob);

        typeSystemPS.executeUpdate();

        PreparedStatement indexMappingPS = conn.prepareStatement("insert into " + corpusName
                + " values (?, ?)");

        indexMappingPS.setString(1, "_indexMapping");

        Blob indexMappingBlob = conn.createBlob();
        indexMappingBlob.setBytes(1, indexMapping);
        indexMappingPS.setBlob(2, indexMappingBlob);

        indexMappingPS.executeUpdate();

        conn.commit();

        typeSystemPS.close();
        indexMappingPS.close();
      }

    } catch (SQLException e) {
      LOGGER.error("Failed to create corpus: {}", corpusName, e);
      throw new IOException(e);
    }

    LOGGER.info("Created new corpus: {}", corpusName);

    for (CorporaChangeListener listener : getListeners()) {
      // TODO: Maybe optimize this, or just pass the corpus id
      listener.addedCorpus(getCorpus(corpusName));
    }
  }

  @Override
  public Set<String> getCorpusIds() throws IOException {
    
    Set<String> corpusIds = new HashSet<>();
    
    try (Connection conn = dataSource.getConnection()) {
      DatabaseMetaData dbmd = conn.getMetaData();

      String[] types = { "TABLE" };
      ResultSet resultSet = dbmd.getTables(null, null, "%", types);

      while (resultSet.next()) {
        String tableName = resultSet.getString(3);
        corpusIds.add(tableName.toLowerCase());
      }

    } catch (SQLException e) {
      LOGGER.error("Failed to retrieve corpus ids!", e);
      throw new IOException(e);
    }
    
    return Collections.unmodifiableSet(corpusIds); 
  }
  
  @Override
  public void dropCorpus(String corpusName) throws IOException {

    try (Connection conn = dataSource.getConnection()) {
      Statement s = conn.createStatement();
      s.execute("drop table " + corpusName);
      s.close();

      conn.commit();
    } catch (SQLException e) {
      LOGGER.error("Failed to create corpus: {}", corpusName, e);
      throw new IOException(e);
    }

    for (CorporaChangeListener listener : getListeners()) {
      listener.droppedCorpus(getCorpus(corpusName));
    }
  }
  
  @Override
  public CorpusStore getCorpus(String corpusId) {
    
    // It must be ensured that the table exist, otherwise
    // null must be returned, because there is no corpus
    // matching the provided id.
    
    // Note:
    // A table might be deleted later on, that case must be handled well!
    
    DerbyCorpusStore corpusStore = null;
    
    try (Connection conn = dataSource.getConnection()) {
      DatabaseMetaData metadata = conn.getMetaData();
      String[] names = { "TABLE" };
      ResultSet tableNames = metadata.getTables(null, null, null, names);

      while (tableNames.next()) {
        String tab = tableNames.getString("TABLE_NAME");
        
        if (tab.equalsIgnoreCase(corpusId)) {
          corpusStore = new DerbyCorpusStore(dataSource, this, corpusId);
          break;
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Failed to check if corpus exists!", e);
      return null;
    }
    
    return corpusStore;
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
