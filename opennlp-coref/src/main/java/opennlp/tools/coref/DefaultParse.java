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

package opennlp.tools.coref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.Span;

/**
 * This class is a wrapper for {@link Parse} mapping
 * it to the API specified in {@link opennlp.tools.coref.mention.Parse}.
 * This allows coreference to be done on the output of the parser.
 */
public class DefaultParse extends AbstractParse {

  public static final String[] NAME_TYPES = {"person", "organization", "location", "date",
      "time", "percentage", "money"};
  
  private final Parse parse;
  private final int sentenceNumber;
  private static final Set<String> ENTITY_SET = new HashSet<>(Arrays.asList(NAME_TYPES));
  
  /**
   * Initializes a {@link DefaultParse} with the specified parameters.
   *
   * @param parse The {@link Parse} instance. Must not be {@code null}.
   * @param sentenceNumber The number of sentences. Must be larger than {@code zero}.
   */
  public DefaultParse(Parse parse, int sentenceNumber) {
    this.parse = parse;
    this.sentenceNumber = sentenceNumber;
    
    // Should we just maintain a parse id map !?
  }

  @Override
  public int getSentenceNumber() {
    return sentenceNumber;
  }

  @Override
  public List<opennlp.tools.coref.mention.Parse> getNamedEntities() {
    List<Parse> names = new ArrayList<>();
    List<Parse> children = new LinkedList<>(Arrays.asList(parse.getChildren()));
    while (!children.isEmpty()) {
      Parse p = children.remove(0);
      if (ENTITY_SET.contains(p.getType())) {
        names.add(p);
      }
      else {
        children.addAll(Arrays.asList(p.getChildren()));
      }
    }
    return createParses(names.toArray(new Parse[0]));
  }

  @Override
  public List<opennlp.tools.coref.mention.Parse> getChildren() {
    return createParses(parse.getChildren());
  }

  @Override
  public List<opennlp.tools.coref.mention.Parse> getSyntacticChildren() {
    List<Parse> kids = new ArrayList<>(Arrays.asList(parse.getChildren()));
    for (int ci = 0; ci < kids.size(); ci++) {
      Parse kid = kids.get(ci);
      if (ENTITY_SET.contains(kid.getType())) {
        kids.remove(ci);
        kids.addAll(ci, Arrays.asList(kid.getChildren()));
        ci--;
      }
    }
    return createParses(kids.toArray(new Parse[0]));
  }

  @Override
  public List<opennlp.tools.coref.mention.Parse> getTokens() {
    List<Parse> tokens = new ArrayList<>();
    List<Parse> children = new LinkedList<>(Arrays.asList(parse.getChildren()));
    while (!children.isEmpty()) {
      Parse p = children.remove(0);
      if (p.isPosTag()) {
        tokens.add(p);
      }
      else {
        children.addAll(0,Arrays.asList(p.getChildren()));
      }
    }
    return createParses(tokens.toArray(new Parse[0]));
  }

  @Override
  public String getSyntacticType() {
    if (ENTITY_SET.contains(parse.getType())) {
      return null;
    }
    else if (parse.getType().contains("#")) {
      return parse.getType().substring(0, parse.getType().indexOf('#'));
    }
    else {
      return parse.getType();
    }
  }

  private List<opennlp.tools.coref.mention.Parse> createParses(Parse[] parses) {
    List<opennlp.tools.coref.mention.Parse> newParses = new ArrayList<>(parses.length);

    for (Parse pars : parses) {
      newParses.add(new DefaultParse(pars, sentenceNumber));
    }

    return newParses;
  }

  @Override
  public String getEntityType() {
    if (ENTITY_SET.contains(parse.getType())) {
      return parse.getType();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isParentNAC() {
    Parse parent = parse.getParent();
    while (parent != null) {
      if (parent.getType().equals("NAC")) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  @Override
  public opennlp.tools.coref.mention.Parse getParent() {
    Parse parent = parse.getParent();
    if (parent == null) {
      return null;
    }
    else {
      return new DefaultParse(parent,sentenceNumber);
    }
  }

  @Override
  public boolean isNamedEntity() {
    
    // TODO: We should use here a special tag to, where
    // the type can be extracted from. Then it just depends
    // on the training data and not the values inside NAME_TYPES.

    return ENTITY_SET.contains(parse.getType());
  }

  @Override
  public boolean isNounPhrase() {
    return parse.getType().equals("NP") || parse.getType().startsWith("NP#");
  }

  @Override
  public boolean isSentence() {
    return parse.getType().equals(Parser.TOP_NODE);
  }

  @Override
  public boolean isToken() {
    return parse.isPosTag();
  }

  @Override
  public int getEntityId() {
    
    String type = parse.getType();
    
    if (type.contains("#")) {
      String numberString = type.substring(type.indexOf('#') + 1);
      return Integer.parseInt(numberString);
    }
    else {
      return -1;
    }
  }

  @Override
  public Span getSpan() {
    return parse.getSpan();
  }

  @Override
  public int compareTo(opennlp.tools.coref.mention.Parse p) {

    if (p == this) {
      return 0;
    }
    if (getSentenceNumber() < p.getSentenceNumber()) {
      return -1;
    }
    else if (getSentenceNumber() > p.getSentenceNumber()) {
      return 1;
    }
    else {
      
      if (parse.getSpan().getStart() == p.getSpan().getStart() &&
          parse.getSpan().getEnd() == p.getSpan().getEnd()) {

        System.out.println("Maybe incorrect measurement!");
        
        // get parent and update distance
        // if match return distance
        // if not match do it again
      }
      
      return parse.getSpan().compareTo(p.getSpan());
    }
  }
  
  @Override
  public String toString() {
    return parse.getCoveredText();
  }


  @Override
  public opennlp.tools.coref.mention.Parse getPreviousToken() {
    Parse parent = parse.getParent();
    Parse node = parse;
    int index = -1;
    //find parent with previous children
    while (parent != null && index < 0) {
      index = parent.indexOf(node) - 1;
      if (index < 0) {
        node = parent;
        parent = parent.getParent();
      }
    }
    //find right-most child which is a token
    if (index < 0) {
      return null;
    }
    else {
      Parse p = parent.getChildren()[index];
      while (!p.isPosTag()) {
        Parse[] kids = p.getChildren();
        p = kids[kids.length - 1];
      }
      return new DefaultParse(p,sentenceNumber);
    }
  }

  @Override
  public opennlp.tools.coref.mention.Parse getNextToken() {
    Parse parent = parse.getParent();
    Parse node = parse;
    int index = -1;
    //find parent with subsequent children
    while (parent != null) {
      index = parent.indexOf(node) + 1;
      if (index == parent.getChildCount()) {
        node = parent;
        parent = parent.getParent();
      }
      else {
        break;
      }
    }
    //find left-most child which is a token
    if (parent == null) {
      return null;
    }
    else {
      Parse p = parent.getChildren()[index];
      while (!p.isPosTag()) {
        p = p.getChildren()[0];
      }
      return new DefaultParse(p,sentenceNumber);
    }
  }

  @Override
  public boolean equals(Object o) {

    boolean result;

    if (o == this) {
      result = true;
    }
    else if (o instanceof DefaultParse) {
      result = parse == ((DefaultParse) o).parse;
    }
    else {
      result = false;
    }

    return result;
  }

  @Override
  public int hashCode() {
    return parse.hashCode();
  }

  /**
   * Retrieves the {@link Parse}.
   *
   * @return the {@link Parse}
   */
  public Parse getParse() {
    return parse;
  }
}
