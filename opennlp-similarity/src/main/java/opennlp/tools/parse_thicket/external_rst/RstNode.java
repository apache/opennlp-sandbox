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
package opennlp.tools.parse_thicket.external_rst;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.utils.Pair;

public class RstNode {
	Boolean isNucleus;
	Pair<Integer, Integer> span;
	Integer leaf;
	String rel2par;
	String text;
	Integer level;
	
	public Boolean getIsNucleus() {
		return isNucleus;
	}
	public void setIsNucleus(Boolean isNucleus) {
		this.isNucleus = isNucleus;
	}
	public Pair<Integer, Integer> getSpan() {
		return span;
	}
	public void setSpan(Pair<Integer, Integer> span) {
		this.span = span;
	}
	public Integer getLeaf() {
		return leaf;
	}
	public void setLeaf(Integer leaf) {
		this.leaf = leaf;
	}
	public String getRel2par() {
		return rel2par;
	}
	public void setRel2par(String rel2par) {
		this.rel2par = rel2par;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	
	public String toString() {
		String ret = "";
		if (isNucleus!=null && isNucleus)
			ret+="Nucleus ";
		if (span!=null)
			ret+="["+span.getFirst()+" "+ span.getSecond()+"]";
		ret += " >> "+ rel2par;
		if (text!=null)
			ret+= " >> "+text;
		return ret;
	}
	public RstNode(String line) {
		if (StringUtils.trim(line).startsWith(")"))
			return;
		

		level = line.indexOf("(");
		line = line.substring(line.indexOf("(")+2);
		
		isNucleus = line.substring(0, line.indexOf("(")).indexOf("Nucleus")>-1;
		line = line.substring(line.indexOf("(")+1);
		if (line.startsWith("span")){
			line = line.substring(5);
			try {
				span = new Pair<Integer, Integer>();
				String[] spanStr = line.substring(0, line.indexOf(")")).split(" "); 
				span.setFirst(Integer.parseInt(spanStr[0]));
				span.setSecond(Integer.parseInt(spanStr[1]));
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} else if (line.startsWith("leaf")){
			try {
				String leafStr = line.substring(5, line.indexOf(")"));
				leaf = Integer.parseInt(leafStr);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} else System.err.println("Problem parsing RST results: '"+line);
		
		line = line.substring(line.indexOf("rel2par")+8);
		rel2par = line.substring(0, line.indexOf(")")).trim();
		
		text = StringUtils.substringBetween(line, "_!", "_!)");

		
	}

	 public static void main(String[] args){
		 RstNode n1 = new RstNode("        ( Nucleus (leaf 7) (rel2par span) (text _!that it usually takes a day_!) )"),
		 n2 = new RstNode("       )"),
		 n3 = new RstNode("          ( Satellite (span 15 16) (rel2par Explanation)");
		 
	 }

}
