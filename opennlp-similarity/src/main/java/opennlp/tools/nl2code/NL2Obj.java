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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.TextSimilarityBagOfWords;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class NL2Obj {
  ObjectControlOp prevOp;

  public NL2Obj(String path) {
    prevOp = new ObjectControlOp();
    prevOp.setOperatorIf("");
    prevOp.setOperatorFor("");
    parser = ParserChunker2MatcherProcessor.getInstance(path);
  }
  
  public NL2Obj() {
	    prevOp = new ObjectControlOp();
	    prevOp.setOperatorIf("");
	    prevOp.setOperatorFor("");
	    parser = ParserChunker2MatcherProcessor.getInstance();
	  }

  public static String[] epistemicStatesList = new String[] {
    "select", "verify", "find", "start", "stop", "go", "check"
  };

  public static String[] instantiatedStatesList = new String[] {
    "get", "set"
  };
  public static String[] propStatesList = new String[] {
    "otherwise",
  };

  protected ParserChunker2MatcherProcessor parser;
  private TextSimilarityBagOfWords parserBOW = new TextSimilarityBagOfWords();
  private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();

  public ObjectPhraseListForSentence convertSentenceToControlObjectPhrase(String sentence){

    List<ObjectPhrase> oPhrases = new  ArrayList<ObjectPhrase>();
    parser = ParserChunker2MatcherProcessor.getInstance();
    List<List<ParseTreeChunk>> lingPhrases = 
      parser.formGroupedPhrasesFromChunksForSentence(sentence);

    ObjectControlOp op = extractControlPart(lingPhrases, prevOp);
    prevOp = op;

    //start with verb phrases
    List<ParseTreeChunk> actionWithObject =  lingPhrases.get(1);

    actionWithObject = applyWhichRuleOnVP(actionWithObject);
    System.out.println("=== "+actionWithObject+" \n extracted op = "+op);

    for(ParseTreeChunk verbChunk: actionWithObject){

      if (verbChunk.getPOSs().get(0).startsWith("VB")){
        ObjectPhrase oPhrase = new ObjectPhrase();
        String methodOrControlOp = verbChunk.getLemmas().get(0).toLowerCase();
        if (!isControlOp(methodOrControlOp)){
          oPhrase.setMethod(methodOrControlOp);
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
      }
    }

    ObjectPhraseListForSentence oplfs =  new ObjectPhraseListForSentence( oPhrases, op);
    oplfs.cleanMethodNamesIsAre();
    oplfs.substituteNullObjectIntoEmptyArg();
    oplfs.substituteIntoEmptyArgs(); //area.size([]), threshold.([below])
    oplfs.clearInvalidObject(); //[null.2([])]
    return oplfs;
  }

  private boolean isControlOp(String methodOrControlOp) {
    return Arrays.asList(epistemicStatesList).contains(methodOrControlOp);
  }

  protected List<ParseTreeChunk> applyWhichRuleOnVP(List<ParseTreeChunk> actionWithObject) {
    for(String connector: new String[]{ "which", "so"}){
      for(ParseTreeChunk ch1: actionWithObject)
        for(ParseTreeChunk ch2: actionWithObject){
          if ((ch1.getLemmas().get(ch1.getLemmas().size()-1).equals(connector)) &&
              !ch2.getLemmas().get(ch2.getLemmas().size()-1).equals(connector)){
            actionWithObject.remove(ch2);
            actionWithObject.remove(ch1);

            List<String> ch1Tmp = ch1.getLemmas();
            ch1Tmp.addAll(ch2.getLemmas());
            ch1.setLemmas(ch1Tmp);

            ch1Tmp = ch1.getPOSs();
            ch1Tmp.addAll(ch2.getPOSs());
            ch1.setPOSs(ch1Tmp);
            actionWithObject.add(ch1);
            return actionWithObject;

          }
        }
    }   
    return actionWithObject;
  }

  public ObjectControlOp extractControlPart(List<List<ParseTreeChunk>> lingPhrases, ObjectControlOp prevOp){
    ObjectControlOp op = new ObjectControlOp();
    List<ParseTreeChunk> parsedSent = lingPhrases.get(4);
    List<String> lems = parsedSent.get(0).getLemmas(); 
    boolean bIfSet=false, bForSet=false;
    for(int i=0; i<lems.size(); i++){
      String c=lems.get(i).toLowerCase();
      if (!bForSet){
        if ((c.equals("all") || c.equals("each") || c.equals("exists")) && i<lems.size()-1){
          String loopSubject = lems.get(i+1).toLowerCase();
          String iterator = "_iterator_";
          if (loopSubject.endsWith("s"))
            iterator = loopSubject.substring(0,loopSubject.length()-1 );
          op.setOperatorFor("for(_data_type "+iterator + ": "+loopSubject+")");
          bForSet=true;
        }
        
        if (c.equals("then") && prevOp.getOperatorIf()!=null && prevOp.getOperatorIf().equals("if")){
          op.setOperatorFor("then");
          op.setLinkUp(" if");
          bForSet=true;
        }
       
        if (c.equals("stop")){
          op.setOperatorFor("return");
          bForSet=true;
        }
      }

      if (!bIfSet){
        if (c.equals("check") || c.equals("verify") ||  c.equals("sure") ){
          op.setOperatorIf("if");
          bIfSet=true;
        }
        if (c.equals("otherwise")  && prevOp.getOperatorIf()!=null && prevOp.getOperatorIf().equals("if")){
          op.setOperatorIf("else");
          op.setLinkUp("if");         
          bIfSet=true;
        }

      
        if ((c.equals("so")||c.equals("such")) && lems.get(i+1).toLowerCase().equals("that")){
          op.setOperatorIf("if");
          bIfSet=true;
        }
        
        if((c.equals("go") && lems.get(i+1).toLowerCase().equals("to")) || c.equals("goto")  ){
          op.setOperatorIf(" break "+lems.get(i+1).toLowerCase()+" _label_ ");
          bIfSet=true;        
        }
      }
    }
    return op;
  }
  /*
  public ObjectPhrase convertSentenceToControlObjectPhraseSingleObj(String sentence){
    parser = ParserChunker2MatcherProcessor.getInstance();
    List<List<ParseTreeChunk>> lingPhrases = 
      parser.formGroupedPhrasesFromChunksForSentence(sentence);

    ObjectControlOp op = extractControlPart(lingPhrases, prevOp);
    prevOp = op;

    ObjectPhrase oPhrase = new ObjectPhrase();
    //start with verb phrases
    List<ParseTreeChunk> actionWithObject =  lingPhrases.get(1);
    ParseTreeChunk verbChunk =  actionWithObject.get(0);
    if (verbChunk.getPOSs().get(0).startsWith("VB")){
      oPhrase.setMethod(verbChunk.getLemmas().get(0));
      List<String> paramValues = verbChunk.getLemmas();
      paramValues.remove(0);
      // the subject of a verb refers to the object
      oPhrase.setObjectName(paramValues.get(paramValues.size()-1));
      paramValues.remove(paramValues.size()-1);
      oPhrase.setParamValues(paramValues);
      oPhrase.setOrigPhrase(verbChunk);
    }
    // object name/instance
    if (oPhrase.getObjectName() == null){
      List<ParseTreeChunk> objectName =  lingPhrases.get(0);
      ParseTreeChunk objNameChunk =   objectName.get(0);
      if ( objNameChunk.getPOSs().get(0).equals("NN")){
        oPhrase.setObjectName( objNameChunk.getLemmas().get(0));
      }
    }

    return oPhrase;

  }
   */


  public static void main(String[] args){
	  
	String cDir = new File(".").getAbsolutePath();
	
    String[] text = new String[]{
        "Randomly select a pixel at an image.",
        "Find a convex area this pixel belongs, so that all pixels are less than 128",      //area->REGION
        "Check that the border of the selected area has all pixels more than 128",
        "If the above verification succeeds, stop with positive result",
        "Otherwise, add all pixels which are less than 128 to the area", 
        "Check that  the size of area is below the threshold. ",
       "Then go to 2",
        "Otherwise, stop with negative result"
    };

    NL2Obj compiler = new NL2Obj();
    for(String sent:text){
      ObjectPhraseListForSentence opls = compiler.convertSentenceToControlObjectPhrase(sent);
      System.out.println(sent+"\n"+opls+"\n");
    }

  }
}