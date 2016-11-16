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

package opennlp.tools.parse_thicket.kernel_interface.style_classif;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.parse_thicket.kernel_interface.TreeKernelBasedClassifierMultiplePara;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class TSNE_ImporterProcessor {
	private static String importFilePath = "all-tsne2.txt";
	public String resourceWorkDir = new File(".").getAbsolutePath().replace("/.", "") + 
			"/src/test/resources/style_recognizer/";

	public void importFileCreatClassifDirs() {
		Map<Integer, String> id_Text = new HashMap<Integer, String>();
		Map<Integer, String> id_Label = new HashMap<Integer, String>();

		try {
			FileUtils.cleanDirectory(new File(resourceWorkDir+"/txt"));
		} catch (IOException e2) {
			e2.printStackTrace();
		}

		String text = null;
		try {
			text = FileUtils.readFileToString(new File(resourceWorkDir+importFilePath ), Charset.defaultCharset().toString());
		} catch (IOException e) {

			e.printStackTrace();
		}

		String[] portions = StringUtils.substringsBetween(text, "<text ", "/text>");
		for(int i=0; i<portions.length; i++){
			String label = StringUtils.substringBetween(portions[i], "id=\"", "\">");
			String po =  StringUtils.substringBetween(portions[i],  "\">", "<");
			id_Text.put(i, po);
			id_Label.put(i, label);
			if (true){
				String localDirName = label.substring(0, 4);
				if (!new File(resourceWorkDir+"txt/"+localDirName).exists())
					try {
						FileUtils.forceMkdir(new File(resourceWorkDir+"txt/"+localDirName));
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				try {
					label = label.replace('/', '_');
					String fullPath = resourceWorkDir+"txt/"+localDirName+"/"+i+label+".txt";
					FileUtils.writeStringToFile(new File(fullPath), po);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public static void main(String[] args){
		TSNE_ImporterProcessor thisProc = new TSNE_ImporterProcessor();
		thisProc.importFileCreatClassifDirs();

		VerbNetProcessor p = VerbNetProcessor.
				getInstance("/Users/borisgalitsky/Documents/workspace/deepContentInspection/src/test/resources"); 

		TreeKernelBasedClassifierMultiplePara proc = new TreeKernelBasedClassifierMultiplePara();
		proc.setKernelPath("/Users/borisgalitsky/Documents/tree_kernel/");
		proc.trainClassifier(thisProc.resourceWorkDir+"/txt/Tele", 
				thisProc.resourceWorkDir+"/txt/Tels");
		//www.sciencedirect.com/science/article/pii/S095070511300138X
	}
}
