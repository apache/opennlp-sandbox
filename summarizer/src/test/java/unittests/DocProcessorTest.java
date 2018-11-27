/* 
    * Licensed to the Apache Software Foundation (ASF) under one or more
 	* contributor license agreements. See the NOTICE file distributed with
 	* this work for additional information regarding copyright ownership.
 	* The ASF licenses this file to You under the Apache License, Version 2.0
 	* (the "License"); you may not use this file except in compliance with
 	* the License. You may obtain a copy of the License at
 	*
 	* http://www.apache.org/licenses/LICENSE-2.0
 	*
 	* Unless required by applicable law or agreed to in writing, software
 	* distributed under the License is distributed on an "AS IS" BASIS,
 	* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 	* See the License for the specific language governing permissions and
 	* limitations under the License.
*/


package unittests;

import static org.junit.Assert.*;

import org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.util.List;

import opennlp.summarization.Sentence;
import opennlp.summarization.preprocess.DefaultDocProcessor;

import org.junit.BeforeClass;
import org.junit.Test;

public class DocProcessorTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Test
	public void testGetSentencesFromStr() {
		String sentFragModel = "resources/en-sent.bin";
		DefaultDocProcessor dp =new DefaultDocProcessor(sentFragModel);
		String sent="This is a sentence, with some punctuations; to test if the sentence breaker can handle it! Is every thing working OK ? Yes.";
		List<Sentence> doc = dp.getSentencesFromStr(sent);//dp.docToString(fileName);//
		assertEquals(doc.size(),3);
	}

}
