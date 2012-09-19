package opennlp.tools.nl2code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.textsimilarity.ParseTreeChunk;

public class ObjectPhrase {
  private String objectName;
  private String objectInstance;
  private String epistemicAction;
  private String controlAction;
  private String method;
  private List<String> paramType;
  private List<String> paramValues;
  private ParseTreeChunk origPhrase;
  
  private ParseTreeChunk paramChunk;
 
  public ParseTreeChunk getParamChunk() {
    return paramChunk;
  }
  public void setParamChunk(ParseTreeChunk paramChunk) {
    this.paramChunk = paramChunk;
  }
  
  public void setParamChunk(List<String> lemmas,  List<String> pOSs) {
    this.paramChunk = new ParseTreeChunk("", lemmas, pOSs);
  }
  
  public ObjectPhrase(){
    paramType = new ArrayList<String>();
    paramValues = new ArrayList<String>();
  }
  public String getObjectName() {
    return objectName;
  }
  public void setObjectName(String objectName) {
    this.objectName = objectName;
  }
  public String getObjectInstance() {
    return objectInstance;
  }
  public void setObjectInstance(String objectInstance) {
    this.objectInstance = objectInstance;
  }
  public String getEpistemicAction() {
    return epistemicAction;
  }
  public void setEpistemicAction(String epistemicAction) {
    this.epistemicAction = epistemicAction;
  }
  public String getControlAction() {
    return controlAction;
  }
  public void setControlAction(String controlAction) {
    this.controlAction = controlAction;
  }
  
  public String getMethod() {
    return method;
  }
  public void setMethod(String method) {
    this.method = method;
  }
  
  public List<String> getParamType() {
    return paramType;
  }
  public void setParamType(List<String> paramType) {
    this.paramType = paramType;
  }
  public List<String> getParamValues() {
    return paramValues;
  }
  public void setParamValues(List<String> paramValues) {
    this.paramValues = paramValues;
  }
  
  public String toString(){
    return objectName+"."+method+"("+paramValues.toString()+")";
  }
  public ParseTreeChunk getOrigPhrase() {
    return origPhrase;
  }
  public void setOrigPhrase(ParseTreeChunk origPhrase) {
    this.origPhrase = origPhrase;
  }
  
  public void cleanArgs(){
    List<String> comparativePrpList = Arrays.asList(new String[]{"below", "more", "less", "above"}); 
    if (this.getParamChunk()==null)
      return;
    List<String> pOSs = this.getParamChunk().getPOSs(), lemmas = this.getParamChunk().getLemmas(), cleanedArgs = new ArrayList<String>();
    
    for(int i=0; i<pOSs.size(); i++){
      String pos = pOSs.get(i);
     
      if (pos.startsWith("NN")|| pos.startsWith("JJ")|| pos.startsWith("CD")|| pos.startsWith("RB ")
        || comparativePrpList.contains(lemmas.get(i)))
        cleanedArgs.add(lemmas.get(i));
        
    }
    this.paramValues = cleanedArgs;
  }
  
}
