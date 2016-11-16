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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;

import junit.framework.TestCase;

public class FCATest extends TestCase{
	ConceptLattice cl=null;
/*
	public void testConceptLattice() {


		try {
			cl = new ConceptLattice("src/test/resources/fca/sports.cxt",true);
			cl.printLatticeStats();
			cl.printLatticeFull();
			cl.printBinContext();

			FcaWriter wt = new FcaWriter();
			wt.WriteStatsToCvs("stats.csv", cl, 0);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();
		}

		FcaConverter converter = new FcaConverter();
		int [][] binCon = converter.latticeToContext(cl);

		if (binCon!=null){
			ConceptLattice new_cl = new ConceptLattice(binCon.length, binCon[0].length, binCon, false);	
			new_cl.printLatticeStats();
			new_cl.printLatticeFull();
			new_cl.printBinContext();
			FcaWriter wt = new FcaWriter();
			wt.WriteStatsToCvs("stats.txt", cl, 0);
			//wt.WriteAsCxt("cl.cxt", cl);
			wt.WriteAsCxt("cl_new.cxt", new_cl);
		}
	}		

	public void testRandom(){
		RandomNoiseGenerator rng = new RandomNoiseGenerator();
		try {
			cl = new ConceptLattice("src/test/resources/fca/sports.cxt",true);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//int[][] bc = rng.AddObjectsAttributesWithProbability(10, 0.5, cl.binaryContext);
		int[][] bc = rng.AlterCellsWithProbability(0.2, cl.binaryContext);
		ConceptLattice new_cl = new ConceptLattice(bc.length, bc[0].length, bc, false);	
		new_cl.printLatticeStats();
		new_cl.printLattice();
	}
*/
}

