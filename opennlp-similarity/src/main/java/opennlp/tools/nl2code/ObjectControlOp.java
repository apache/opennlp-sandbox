package opennlp.tools.nl2code;

public class ObjectControlOp {
  String operatorFor;
  String operatorIf;
  String linkUp;
  String linkDown;
  
  
  public ObjectControlOp() {
    operatorFor="";
    operatorIf="";
  }
  public String getOperatorFor() {
    return operatorFor;
  }
  public void setOperatorFor(String operatorFor) {
    this.operatorFor = operatorFor;
  }
  public String getOperatorIf() {
    return operatorIf;
  }
  public void setOperatorIf(String operatorIf) {
    this.operatorIf = operatorIf;
  }
  public String getLinkUp() {
    return linkUp;
  }
  public void setLinkUp(String linkUp) {
    this.linkUp = linkUp;
  }
  public String getLinkDown() {
    return linkDown;
  }
  public void setLinkDown(String linkDown) {
    this.linkDown = linkDown;
  }
  
  public String toString(){
    return operatorFor+ "(" + operatorIf;
  }
  
}
