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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.opennlp.corpus_server.UimaUtil;
import org.apache.uima.resource.metadata.TypeSystemDescription;

public class DerbyCorpusStore implements CorpusStore {

  private final static Logger LOGGER = Logger.getLogger(
      DerbyCorpusStore.class .getName());

  private DataSource dataSource;
  private DerbyCorporaStore store;
  private final String corpusName;
  
  DerbyCorpusStore(DataSource dataSource, DerbyCorporaStore store, String corpusName) {
    this.dataSource = dataSource;
    this.store = store;
    this.corpusName = corpusName;
  }
  
  @Override
  public String getCorpusId() {
    return corpusName;
  }
  
  @Override
  public byte[] getCAS(String casId) throws IOException {
    
    byte casBytes[]  = null;
    
    try {
      Connection conn = dataSource.getConnection();
      Statement s = conn.createStatement();
      ResultSet casResult = s.executeQuery("select * FROM " + corpusName +
          " WHERE name='" + casId + "'");
      
      if (casResult.next()) {
        casBytes = casResult.getBytes(2);
      }
      
      casResult.close();
      s.close();
      conn.close();
      
    } catch (SQLException e) {
      
      if (LOGGER.isLoggable(Level.SEVERE)) {
        LOGGER.log(Level.SEVERE, "Failed to retrieve CAS: " + 
            casId, e);
      }
      
      throw new IOException(e);
    }
    
    return casBytes;
  }

  @Override
  public void addCAS(String casID, byte[] content) throws IOException {
    
    try {
      Connection conn = dataSource.getConnection();
      PreparedStatement ps = conn.prepareStatement("insert into " + 
          corpusName + " values (?, ?)");
      
      ps.setString(1, casID);
      
      Blob b = conn.createBlob();
      b.setBytes(1, content);
      ps.setBlob(2, b);
      
      ps.executeUpdate();
      
      conn.commit();
      
      ps.close();
      conn.close();
    } catch (SQLException e) {
      
      if (LOGGER.isLoggable(Level.SEVERE)) {
        LOGGER.log(Level.SEVERE, "Failed to add CAS: " + 
            casID, e);
      }
      
      throw new IOException(e);
    }
    
    for (CorporaChangeListener listener : store.getListeners()) {
      listener.addedCAS(this, casID);
    }
  }

  @Override
  public void updateCAS(String casID, byte[] content) throws IOException {
    try {
      Connection conn = dataSource.getConnection();
      PreparedStatement ps = conn.prepareStatement("update " + 
          corpusName + " set cas = ? where name = ?");
      
      ps.setString(2, casID);
      
      Blob b = conn.createBlob();
      b.setBytes(1, content);
      ps.setBlob(1, b);
      
      ps.executeUpdate();
      
      conn.commit();
      
      ps.close();
      conn.close();
    } catch (SQLException e) {
      
      if (LOGGER.isLoggable(Level.SEVERE)) {
        LOGGER.log(Level.SEVERE, "Failed to add CAS: " + 
            casID, e);
      }
      
      throw new IOException(e);
    }
    
    for (CorporaChangeListener listener : store.getListeners()) {
      listener.updatedCAS(this, casID);
    }
  }
  
  @Override
  public TypeSystemDescription getTypeSystem() throws IOException {
    
    TypeSystemDescription tsDescription = null;
    
    try {
      Connection conn = dataSource.getConnection();
      Statement s = conn.createStatement();
      ResultSet tsResult = s.executeQuery("select * FROM " + corpusName + 
          " WHERE name='_typesystem'");
      
      if (tsResult.next()) {
        byte tsBytes[] = tsResult.getBytes(2);
        tsDescription = UimaUtil.createTypeSystemDescription(
            new ByteArrayInputStream(tsBytes));
      }
      
      tsResult.close();
      s.close();
      conn.close();
    } catch (SQLException e) {
      
      if (LOGGER.isLoggable(Level.SEVERE)) {
        LOGGER.log(Level.SEVERE, "Failed to retrieve type system", e);
      }
      
      throw new IOException(e);
    }
    
    return tsDescription;
  }
}
