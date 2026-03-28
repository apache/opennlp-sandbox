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

import java.util.Random;

public class RandomNoiseGenerator {
	
	public int [][] AlterCellsWithProbability(double p, int [][] binaryContext){
		
		Random rnd = new Random();
		int n = binaryContext.length;
		int m = binaryContext[0].length;
		
		for (int i = 0; i < n; i++ )
			for (int j = 0; j < m; j++)
				if (rnd.nextFloat() <= p){
					if (binaryContext[i][j] == 0)
						binaryContext[i][j] = 1;
					else 
						binaryContext[i][j] = 0;
				}
		return binaryContext;
	}
	
	public int[][] AddObjectsAttributesWithProbability(int numberObjAttr, double d, int [][] binaryContext){
		
		Random rnd = new Random();
		int nRandObjs = rnd.nextInt(numberObjAttr);
		int nRandAttr = numberObjAttr - nRandObjs;
		
		int [][] newBinaryContext = new int [binaryContext.length + nRandObjs][binaryContext[0].length + nRandAttr];
		for (int i = 0; i < binaryContext.length; i++)
			for (int  j = 0; j < binaryContext[0].length; j++)
				newBinaryContext[i][j] = binaryContext[i][j];
		
		for (int i = binaryContext.length; i < binaryContext.length + nRandObjs; i++)
			for (int  j = 0; j < binaryContext[0].length + nRandAttr; j++)
				newBinaryContext[i][j] = (rnd.nextFloat() <= d) ? 1 : 0;
		
		for (int  j = binaryContext[0].length; j < binaryContext[0].length + nRandAttr; j++)
			for (int i = 0; i < binaryContext.length; i++)
				newBinaryContext[i][j] = (rnd.nextFloat() <= d) ? 1 : 0;
		
		return newBinaryContext;
	}
}

	