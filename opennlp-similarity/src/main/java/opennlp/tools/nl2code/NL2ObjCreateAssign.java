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
package opennlp.tools.nl2code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class NL2ObjCreateAssign extends NL2Obj {

	private boolean classBeingDefined = false;
	public static String[] declarationStatesList = new String[] {
		"create", "assign", "set", 
	};

	public static String[] dataTypesList = new String[] {
		"text", "double", "array", 
	};

	public static String[] arrayElementList = new String[] {
		"first", "second", "third", "fourth" 
	};

	public static String[] arrayElementListInsdex = new String[] {
		"0", "1", "2", "3" 
	};



	public NL2ObjCreateAssign() {
		super();
	}

	public NL2ObjCreateAssign(String path) {
		super(path);
	}

	@Override
	public ObjectPhraseListForSentence convertSentenceToControlObjectPhrase(String sentence){
		String expression = null;
		if (sentence.indexOf(":")>-1){
			expression = sentence.split(":")[1];
			sentence = sentence.split(":")[0]+".";
		}


		List<ObjectPhrase> oPhrases = new  ArrayList<ObjectPhrase>();
		parser = ParserChunker2MatcherProcessor.getInstance();
		List<List<ParseTreeChunk>> lingPhrases = 
				parser.formGroupedPhrasesFromChunksForSentence(sentence);

		ObjectControlOp op = extractControlPart(lingPhrases, prevOp);
		prevOp = op;

		//start with verb phrases
		List<ParseTreeChunk> actionWithObject =  lingPhrases.get(1);
		actionWithObject.addAll( lingPhrases.get(4));

		System.out.println("      === "+actionWithObject);

		for(ParseTreeChunk verbChunk: actionWithObject){
			List<String> lems = verbChunk.getLemmas();
			String declarativeAction = verbChunk.getLemmas().get(0).toLowerCase();
			if (declarativeAction.equals("define")){
				if (verbChunk.getLemmas().get(1).toLowerCase().equals("class") ||
						verbChunk.getLemmas().get(2).toLowerCase().equals("class")){
					// new class
					String className = verbChunk.getLemmas().get(verbChunk.getLemmas().size()-1).toLowerCase();
					className = className.substring(0, 1).toUpperCase()+className.substring(1, className.length());
					op.setOperatorIf("class "+className + "{");
					op.setOperatorFor("{");
					classBeingDefined = true;
					break;
				}
				String dataType = verbChunk.getLemmas().get(1).toLowerCase();

				if (classBeingDefined && Arrays.asList(dataTypesList).contains(dataType) && verbChunk.getLemmas().get(2).toLowerCase().equals("attribute")){
					op.setOperatorFor(dataType + " "+verbChunk.getLemmas().get(verbChunk.getLemmas().size()-1).toLowerCase());
					classBeingDefined = true;
					break;
				}
				if (Arrays.asList(dataTypesList).contains(dataType) && verbChunk.getLemmas().get(2).toLowerCase().equals("attribute")){
					op.setOperatorFor(dataType + " "+verbChunk.getLemmas().get(verbChunk.getLemmas().size()-1).toLowerCase());
					classBeingDefined = true;
					break;
				}
			} else if (declarativeAction.equals("create")){

				// now substituting array
				if (verbChunk.getLemmas().get(1).toLowerCase().equals("array")){

					if(lems.contains("class")){
						int indClass = lems.indexOf("class");
						int numElements = lems.indexOf("elements");
						if (numElements<0)
							numElements = lems.indexOf("objects");
						if (numElements<0)
							numElements = lems.indexOf("members");
						String arraySize = lems.get(numElements-1);
						op.setOperatorFor(lems.get(indClass+1)+"[] "+verbChunk.getLemmas().get(verbChunk.getLemmas().size()-1).toLowerCase() 
								+" = new "+lems.get(indClass+1)+"["+arraySize+"]");
						classBeingDefined = false;
						break;
					}
				}    
			} else if (declarativeAction.equals("assign")){
				int numElements = lems.indexOf("element");
				if (numElements<0)
					numElements = lems.indexOf("object");
				if (numElements<0)
					numElements = lems.indexOf("member");
				if (Arrays.asList(arrayElementList).contains(lems.get(numElements-1))){
					int arrIndex = Arrays.asList(arrayElementList).indexOf(lems.get(numElements-1));
					String indexValue = arrayElementListInsdex[arrIndex]; 

					String arrayName = lems.get(lems.size()-1);
					if (expression!=null)
						op.setOperatorFor(arrayName+"["+indexValue+"]."+ expression);
					break;
				} 
			} else if (declarativeAction.equals("set")){
				int indQuantifier = lems.indexOf("all");
				if (indQuantifier>-1 && 
						(lems.get(indQuantifier+1).equals("elements") || lems.get(indQuantifier+1).equals("members") )){

					String arrayName = lems.get(lems.size()-1);
					if (expression!=null)
						op.setOperatorFor("for(int i=0; i<"+ arrayName+".size(); i++) "+
								arrayName+"[i]."+ expression);
					break;
				} 
			}
			/*    
        else {
          List<String> paramValues = verbChunk.getLemmas(), paramPOSs = verbChunk.getPOSs();

          paramValues.remove(0); paramPOSs.remove(0);
          // the subject of a verb refers to the object
          try {
            String objectCandidatePOS = paramPOSs.get(paramValues.size()-1);
            if (objectCandidatePOS.startsWith("NN")){
              oPhrase.setObjectName(paramValues.get(paramValues.size()-1));
              paramValues.remove(paramValues.size()-1);
              paramPOSs.remove(paramPOSs.size()-1);
            } else if (paramPOSs.get(paramValues.size()-2).startsWith("NN")){
              oPhrase.setObjectName(paramValues.get(paramValues.size()-2));
              paramValues.remove(paramValues.size()-2);
              paramPOSs.remove(paramPOSs.size()-2);
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
          oPhrase.setParamValues(paramValues);
          oPhrase.setParamChunk(paramValues, paramPOSs);

          // object name/instance
          if (oPhrase.getObjectName() == null){
            List<ParseTreeChunk> objectName =  lingPhrases.get(0);
            ParseTreeChunk objNameChunk =   objectName.get(0);
            if ( objNameChunk.getPOSs().get(0).equals("NN")){
              oPhrase.setObjectName( objNameChunk.getLemmas().get(0));
            }
          }
        } else { // verb = 'verify' attribute  prep prep object

          List<String> paramValues = verbChunk.getLemmas(), paramPOSs = verbChunk.getPOSs();
          paramValues.remove(0); paramPOSs.remove(0); // we dont need 'verify'

          // start with getting right-most noun as object
          String objectCandidatePOS = paramPOSs.get(paramValues.size()-1);
          if (objectCandidatePOS.startsWith("NN")){
            oPhrase.setObjectName(paramValues.get(paramValues.size()-1));
            paramValues.remove(paramValues.size()-1);
            paramPOSs.remove(paramPOSs.size()-1);
          } else if (paramPOSs.get(paramValues.size()-2).startsWith("NN")){
            oPhrase.setObjectName(paramValues.get(paramValues.size()-2));
            paramValues.remove(paramValues.size()-2);
            paramPOSs.remove(paramPOSs.size()-2);
          }
          // attempt to find attribute
          for(int i = paramValues.size()-1; i>=0; i--){
            if (paramPOSs.get(i).equals("IN") || paramPOSs.get(i).equals("DT"))
              continue;
            else if (paramPOSs.get(i).startsWith("NN")||paramPOSs.get(i).startsWith("JJ")||paramPOSs.get(i).startsWith("CD")){
              oPhrase.setMethod(paramValues.get(i));
              paramValues = paramValues.subList(0, i-1);
              paramPOSs = paramPOSs.subList(0, i-1);
              oPhrase.setParamValues(paramValues);
              oPhrase.setParamChunk(paramValues, paramPOSs);
              break;
            }
          }
        }
        oPhrase.setOrigPhrase(verbChunk);
        oPhrase.cleanArgs();
        //if (oPhrase.getMethod()!=null || oPhrase.getObjectName()!=null)
        oPhrases.add(oPhrase);      

      } */
		}

		ObjectPhraseListForSentence oplfs =  new ObjectPhraseListForSentence( oPhrases, op);
		oplfs.cleanMethodNamesIsAre();
		oplfs.substituteNullObjectIntoEmptyArg();

		return oplfs;
	}

	public static void main(String[] args){

		String[] text = new String[]{
				"Define a class and name it Employee. ",
				"Define text attribute and name it m_name. ",
				"Define double attribute and name it m_salary.",
				"Create array of objects of class Employee for 10 elements, name the object as workforce.",
				"Assign the first element in array workforce: m_name=\"Boss\"",
				"Assign the second element in array workforce: m_name=\"His wife\"",
				//  "Comment: We just started our small business company and expect to hire 8 more people soon.",
				"Set for all elements in array workforce: m_salary=0 ",
				"Print the list of all m_name attributes for workforce."

		};

		NL2Obj compiler = new NL2ObjCreateAssign();
		for(String sent:text){
			ObjectPhraseListForSentence opls=null;
			try {
				opls = compiler.convertSentenceToControlObjectPhrase(sent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(sent+"\n"+opls+"\n");
		}

	}
}
