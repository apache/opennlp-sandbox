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
package opennlp.addons.geoentitylinker;

import java.util.HashMap;
import java.util.Map;
import opennlp.tools.entitylinker.domain.BaseLink;

/**
 *
 * Stores a record from a geographic placenames gazateer
 */
public class GazateerEntry extends BaseLink {

  private Double latitude;
  private Double longitude;
  private String source;
  private String indexID;
  private Map<String, String> indexData=new HashMap<>();

  public String getIndexID() {
    return indexID;
  }

  public void setIndexID(String indexID) {
    this.indexID = indexID;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Map<String, String> getIndexData() {
    return indexData;
  }

  public void setIndexData(Map<String, String> indexData) {
    this.indexData = indexData;
  }
  
}
