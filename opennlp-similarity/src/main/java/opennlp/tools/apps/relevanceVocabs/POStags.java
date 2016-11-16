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
	public static final String TYPE_STP = "STP"; // infinitive phrase
	public static final String TYPE_SGP = "SGP"; // present participle phrase
	public static final String TYPE_SNP = "SNP"; // past participle phrase

	// below are the standard POS types,
	// http://bulba.sdsu.edu/jeanette/thesis/PennTags.html
	public static final String TYPE_ADJP = "ADJP";
	public static final String TYPE_ADVP = "ADVP";
	public static final String TYPE_CC = "CC";
	public static final String TYPE_CD = "CD";
	public static final String TYPE_CONJP = "CONJP";
	public static final String TYPE_DT = "DT";
	public static final String TYPE_EX = "EX";
	public static final String TYPE_FRAG = "FRAG";
	public static final String TYPE_FW = "FW";
	public static final String TYPE_IN = "IN";
	public static final String TYPE_INTJ = "INTJ";
	public static final String TYPE_JJ = "JJ";
	public static final String TYPE_JJR = "JJR";
	public static final String TYPE_JJS = "JJS";
	public static final String TYPE_LS = "LS";
	public static final String TYPE_LST = "LST";
	public static final String TYPE_MD = "MD";
	public static final String TYPE_NAC = "NAC";
	public static final String TYPE_NN = "NN";
	public static final String TYPE_NNS = "NNS";
	public static final String TYPE_NNP = "NNP";
	public static final String TYPE_NNPS = "NNPS";
	public static final String TYPE_NP = "NP";
	public static final String TYPE_NX = "NX";
	public static final String TYPE_PDT = "PDT";
	public static final String TYPE_POS = "POS";
	public static final String TYPE_PP = "PP";
	public static final String TYPE_PRN = "PRN";
	public static final String TYPE_PRP = "PRP";
	public static final String TYPE_PRP$ = "PRP$";
	public static final String TYPE_PRT = "PRT";
	public static final String TYPE_QP = "QP";
	public static final String TYPE_RB = "RB";
	public static final String TYPE_RBR = "RBR";
	public static final String TYPE_RBS = "RBS";
	public static final String TYPE_RP = "RP";
	public static final String TYPE_RRC = "RRC";
	public static final String TYPE_S = "S";
	public static final String TYPE_SBAR = "SBAR";
	public static final String TYPE_SBARQ = "SBARQ";
	public static final String TYPE_SINV = "SINV";
	public static final String TYPE_SQ = "SQ";
	public static final String TYPE_SYM = "SYM";
	public static final String TYPE_TO = "TO";
	public static final String TYPE_TOP = "TOP";
	public static final String TYPE_UCP = "UCP";
	public static final String TYPE_UH = "UH";
	public static final String TYPE_VB = "VB";
	public static final String TYPE_VBD = "VBD";
	public static final String TYPE_VBG = "VBG";
	public static final String TYPE_VBN = "VBN";
	public static final String TYPE_VBP = "VBP";
	public static final String TYPE_VBZ = "VBZ";
	public static final String TYPE_VP = "VP";
	public static final String TYPE_WDT = "WDT";
	public static final String TYPE_WHADJP = "WHADJP";
	public static final String TYPE_WHADVP = "WHADVP";
	public static final String TYPE_WHNP = "WHNP";
	public static final String TYPE_WHPP = "WHPP";
	public static final String TYPE_WP = "WP";
	public static final String TYPE_WP$ = "WP$";
	public static final String TYPE_WRB = "WRB";
	public static final String TYPE_X = "X";
}
