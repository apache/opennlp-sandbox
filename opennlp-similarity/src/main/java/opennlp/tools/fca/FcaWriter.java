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
package opennlp.tools.fca;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class FcaWriter {
	
	public void WriteAsCxt(String filename, ConceptLattice cl){
		
		Writer writer = null;

		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream(filename), "utf-8"));
		    writer.write("B\n");
		    writer.write("\n");
		    writer.write(String.valueOf(cl.objectCount)+"\n");
		    writer.write(cl.attributeCount + "\n");
		    writer.write("\n");		    
		    
		    for (int obj = 0; obj < cl.objectCount; obj++){
		    	writer.write(obj + "\n");
		    }	    
		    for (int attr = 0; attr < cl.attributeCount; attr++){
		    	writer.write(attr + "\n");
		    }
		    
		    for (int i = 0; i < cl.objectCount; i++){
		    	for (int j = 0; j < cl.attributeCount; j++){
		    		writer.write((cl.binaryContext[i][j] == 0) ? '.':'X');
		    	}
		    	writer.write("\n");
		    }
		    
		    writer.close();
		    
		} catch (IOException ex) {
			System.err.println(ex.getMessage());
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
	}
	
	public void WriteStatsToTxt(String filename, ConceptLattice cl, int patternStructureSize){
		///2n+1
		int intentSize = 2*cl.attributeCount+1; //4*cl.ob+1;
		String formatStrHeader = "%-9s %-9s %-13s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-"+ intentSize +"s%n";
		String formatStr = "%6.3f %6.3f [%5.3f; %5.3f] %6.3f %6.3f %6.3f %6.3f %6.3f %6.3f %6.3f %6.3f %6.3f %6.3f %6.3f %6.3f %"+ intentSize +"s%n";
		String formatStr2 = "%9.2f %9.2f [%5.2f; %5.2f] %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f %-"+ intentSize +"s%n";
		Writer writer = null;

		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream(filename), "utf-8"));
		    writer.write("Lattice size: " + cl.getSize() +", obj: " + cl.objectCount  + ", attr: " + cl.attributeCount +"\n");
		    writer.write("PatternStructure size: " + patternStructureSize + "\n");
		    writer.write(String.format(formatStrHeader, "probability", "separation", "stability", "blCV", "blCFC", "blCU", "blP", "blS_SMCaa", "blS_SMCam", "blS_SMCma", "blS_SMCma", "blS_Jaa",  "blS_Jam",  "blS_Jma", "blS_Jmm", "extent"));			 
	    	
		    for (FormalConcept c : cl.conceptList){
		    	writer.write(String.format(formatStr2, c.probability, c.separation, c.intLogStabilityBottom, c.intLogStabilityUp, c.blCV, c.blCFC, c.blCU, c.blP, 
		    			c.blS_SMCaa,  c.blS_SMCam, c.blS_SMCma, c.blS_SMCmm, c.blS_Jaa,  c.blS_Jam,  c.blS_Jma, c.blS_Jmm, c.extent));
		    }

		    writer.close();
		    
		} catch (IOException ex) {
			System.err.println(ex.getMessage());
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
	}
	
	
	public void WriteStatsToCvs(String filename, ConceptLattice cl, int patternStructureSize){
		///2n+1
		int intentSize = 2*cl.attributeCount+1; //4*cl.ob+1;
		String formatStrHeader = "%s; %s; %s; %s; %s; %s; %s; %s; %s; %s; %s; %s; %s; %s; %s; %s%n";
		String formatStr2 = "%.2f; %.2f; [%5.2f : %5.2f]; %.2f; %.2f; %.2f; %.2f; %.2f; %.2f; %.2f; %.2f; %.2f; %.2f; %.2f; %.2f; %s%n";
		Writer writer = null;

		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream(filename), "utf-8"));
		    writer.write("Lattice size: " + cl.getSize() +", obj: " + cl.objectCount  + ", attr: " + cl.attributeCount +"\n");
		    writer.write("PatternStructure size: " + patternStructureSize + "\n");
		    writer.write(String.format(formatStrHeader, "probability", "separation", "stability", "blCV", "blCFC", "blCU", "blP", "blS_SMCaa", "blS_SMCam", "blS_SMCma", "blS_SMCma", "blS_Jaa",  "blS_Jam",  "blS_Jma", "blS_Jmm", "extent"));			 
	    	
		    for (FormalConcept c : cl.conceptList){
		    	writer.write(String.format(formatStr2, c.probability, c.separation, c.intLogStabilityBottom, c.intLogStabilityUp, c.blCV, c.blCFC, c.blCU, c.blP, 
		    			c.blS_SMCaa,  c.blS_SMCam, c.blS_SMCma, c.blS_SMCmm, c.blS_Jaa,  c.blS_Jam,  c.blS_Jma, c.blS_Jmm, c.extent));
		    }

		    writer.close();
		    
		} catch (IOException ex) {
			 System.err.println(ex.getMessage());
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
	}
	
	
public static void main(String []args) throws FileNotFoundException, IOException{
	
	ConceptLattice cl = new ConceptLattice("sports.cxt",false);	
	FcaWriter writer = new FcaWriter();
	writer.WriteAsCxt("res.cxt",cl);
		
	}

}
