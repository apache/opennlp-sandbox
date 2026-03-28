/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.parse_thicket;

public class ArcType{
	private String type; // rst
	private String subtype; // rst-explain
	private Integer type_id;
	private Integer subtype_id;
	
	public ArcType(String type, // rst
	String subtype, // rst-explain
	Integer type_id,
	Integer subtype_id){
		this.type = type; // rst
		this.subtype = subtype; // rst-explain
		this.type_id= type_id;
		this.subtype_id = subtype_id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSubtype() {
		return subtype;
	}

	public void setSubtype(String subtype) {
		this.subtype = subtype;
	}

	public Integer getType_id() {
		return type_id;
	}

	public void setType_id(Integer type_id) {
		this.type_id = type_id;
	}

	public Integer getSubtype_id() {
		return subtype_id;
	}

	public void setSubtype_id(Integer subtype_id) {
		this.subtype_id = subtype_id;
	}
	
	public String toString(){
		return type+":"+subtype;
	}
}