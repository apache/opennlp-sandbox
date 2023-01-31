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
package opennlp.tools.parse_thicket.pattern_structure;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import opennlp.tools.fca.ConceptLattice;
import opennlp.tools.fca.FormalConcept;

public class PatternStructureWriter {
	
	public void WriteStatsToTxt(String filename, PhrasePatternStructure ps){
			
		String formatStr = "[%5.2f; %5.2f]  %s   %s%n";
		Writer writer = null;

		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream(filename), "utf-8"));
		    writer.write("PatternStructure size: " + ps.conceptList.size()+ " with " + ps.objectCount + "objects\n");
		    
		    for (PhraseConcept c : ps.conceptList){
		    	writer.write(String.format(formatStr,c.intLogStabilityBottom, c.intLogStabilityUp, c.extent, c.intent));
		    }
		    writer.close();
		    
		} catch (IOException ex) {
			System.err.println(ex.getMessage());
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
	}
	

		public static void main(String[] args) {
			
		}
}
			