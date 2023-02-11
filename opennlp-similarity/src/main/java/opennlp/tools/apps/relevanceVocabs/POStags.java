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

package opennlp.tools.apps.relevanceVocabs;

public interface POStags {
	// added new POS types for infinitive phrase and participle phrase
	String TYPE_STP = "STP"; // infinitive phrase
	String TYPE_SGP = "SGP"; // present participle phrase
	String TYPE_SNP = "SNP"; // past participle phrase

	// below are the standard POS types,
	// http://bulba.sdsu.edu/jeanette/thesis/PennTags.html
	String TYPE_ADJP = "ADJP";
	String TYPE_ADVP = "ADVP";
	String TYPE_CC = "CC";
	String TYPE_CD = "CD";
	String TYPE_CONJP = "CONJP";
	String TYPE_DT = "DT";
	String TYPE_EX = "EX";
	String TYPE_FRAG = "FRAG";
	String TYPE_FW = "FW";
	String TYPE_IN = "IN";
	String TYPE_INTJ = "INTJ";
	String TYPE_JJ = "JJ";
	String TYPE_JJR = "JJR";
	String TYPE_JJS = "JJS";
	String TYPE_LS = "LS";
	String TYPE_LST = "LST";
	String TYPE_MD = "MD";
	String TYPE_NAC = "NAC";
	String TYPE_NN = "NN";
	String TYPE_NNS = "NNS";
	String TYPE_NNP = "NNP";
	String TYPE_NNPS = "NNPS";
	String TYPE_NP = "NP";
	String TYPE_NX = "NX";
	String TYPE_PDT = "PDT";
	String TYPE_POS = "POS";
	String TYPE_PP = "PP";
	String TYPE_PRN = "PRN";
	String TYPE_PRP = "PRP";
	String TYPE_PRP$ = "PRP$";
	String TYPE_PRT = "PRT";
	String TYPE_QP = "QP";
	String TYPE_RB = "RB";
	String TYPE_RBR = "RBR";
	String TYPE_RBS = "RBS";
	String TYPE_RP = "RP";
	String TYPE_RRC = "RRC";
	String TYPE_S = "S";
	String TYPE_SBAR = "SBAR";
	String TYPE_SBARQ = "SBARQ";
	String TYPE_SINV = "SINV";
	String TYPE_SQ = "SQ";
	String TYPE_SYM = "SYM";
	String TYPE_TO = "TO";
	String TYPE_TOP = "TOP";
	String TYPE_UCP = "UCP";
	String TYPE_UH = "UH";
	String TYPE_VB = "VB";
	String TYPE_VBD = "VBD";
	String TYPE_VBG = "VBG";
	String TYPE_VBN = "VBN";
	String TYPE_VBP = "VBP";
	String TYPE_VBZ = "VBZ";
	String TYPE_VP = "VP";
	String TYPE_WDT = "WDT";
	String TYPE_WHADJP = "WHADJP";
	String TYPE_WHADVP = "WHADVP";
	String TYPE_WHNP = "WHNP";
	String TYPE_WHPP = "WHPP";
	String TYPE_WP = "WP";
	String TYPE_WP$ = "WP$";
	String TYPE_WRB = "WRB";
	String TYPE_X = "X";
}
