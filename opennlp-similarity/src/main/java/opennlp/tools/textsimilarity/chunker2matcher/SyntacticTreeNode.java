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

package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;

public abstract class SyntacticTreeNode {
  // the POS type
  private String type;

  // parent node, it is null for the root node
  private PhraseNode parentNode;

  public abstract List<SyntacticTreeNode> getChildren();

  public abstract String getText();

  public abstract String getLemma(boolean removeStopWord);

  public abstract String toStringIndented(int numTabs);

  public abstract List<String> getOrderedPOSList();

  public abstract List<String> getOrderedLemmaList();

  public SyntacticTreeNode(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getLemma() {
    return getLemma(false);
  }

  public PhraseNode getParentNode() {
    return parentNode;
  }

  public void setParentNode(PhraseNode parentNode) {
    this.parentNode = parentNode;
  }

  public int getChildrenCount() {
    List<SyntacticTreeNode> childrenList = getChildren();
    if (childrenList == null)
      return 0;

    return childrenList.size();
  }

  public String toString() {
    return toStringIndented(0);
  }

  public static String getIndent(int numTabs) {
    if (numTabs <= 0)
      return "";

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < numTabs; i++) {
      builder.append("\t");
    }

    return builder.toString();
  }

  public static boolean isJunkType(String type, Parse parse) {
    if (type == null)
      return true;

    // the token node is useless
    if (type.equals(AbstractBottomUpParser.TOK_NODE))
      return true;

    // the punctuation nodes are not useful, '.', '.', '?', '!', ';', etc
    if ((type.equals(",") || type.equals(".") || type.equals("?")
        || type.equals("!") || type.equals(";"))
    // TODO : Parser gives type = '.' instead of VB
    // && ( parse.getHead().toString().length()<2
    )
      return true;

    return false;
  }

  public static void replaceNode(SyntacticTreeNode nodeToReplace,
      SyntacticTreeNode newNode) {
    List<SyntacticTreeNode> newNodeList = null;
    if (newNode != null) {
      newNodeList = new ArrayList<SyntacticTreeNode>(1);
      newNodeList.add(newNode);
    }

    replaceNode(nodeToReplace, newNodeList);
  }

  public static void replaceNode(SyntacticTreeNode nodeToReplace,
      List<SyntacticTreeNode> newNodeList) {
    if (nodeToReplace == null)
      throw new NullPointerException("The node to replace cannot be null");

    PhraseNode parentNode = nodeToReplace.getParentNode();

    if (parentNode == null) {
      // the node to replace is the root node
      // clear all children of the existing root node and use it as the
      // new root node
      if (nodeToReplace instanceof PhraseNode)
        ((PhraseNode) nodeToReplace).setChildren(newNodeList);
      return;
    }

    List<SyntacticTreeNode> childrenNodes = parentNode.getChildren();
    int index = childrenNodes.indexOf(nodeToReplace);
    if (index >= 0) {
      // remove the old node
      childrenNodes.remove(index);

      // put the new node list at the place of the old node if there are
      // any
      if (newNodeList != null && newNodeList.size() > 0) {
        childrenNodes.addAll(index, newNodeList);

        // set the parent node of the new children
        for (SyntacticTreeNode newNode : newNodeList) {
          newNode.setParentNode(parentNode);
        }
      }
    }
  }

}
