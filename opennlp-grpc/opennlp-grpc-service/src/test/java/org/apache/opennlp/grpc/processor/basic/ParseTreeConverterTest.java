/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.processor.basic;

import opennlp.tools.parser.Parse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.ParseNode;
import org.apache.opennlp.grpc.v1.ParseNodeKind;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ParseTreeConverter}. The input parse is built from a tree-bank string via
 * {@link Parse#parseParse(String)}, which produces the same {@code TOK_NODE}-leaf structure the
 * real parser emits, so these tests need no parser model and are fully offline.
 */
class ParseTreeConverterTest {

  // (TOP (S (NP (DT The) (NN dog)) (VP (VBD barked))))
  private static final String TREEBANK = "(TOP (S (NP (DT The) (NN dog)) (VP (VBD barked))))";

  // A sentence whose token spans the terminals must adopt (document offsets of "The dog barked").
  private static AnnotatedSentence sentence() {
    return AnnotatedSentence.newBuilder()
        .addTokens(token("The", 0, 3))
        .addTokens(token("dog", 4, 7))
        .addTokens(token("barked", 8, 14))
        .build();
  }

  private static Token token(String text, int start, int end) {
    return Token.newBuilder()
        .setText(text)
        .setAnnotationSpan(AnnotationSpan.newBuilder()
            .setStart(start)
            .setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
            .build())
        .build();
  }

  @Test
  void buildsTypedStructuredTreeLinkedToTokens() {
    final ParseTree tree =
        ParseTreeConverter.toParseTree(Parse.parseParse(TREEBANK), sentence(), true, false);

    final ParseNode root = tree.getRoot();
    assertEquals("TOP", root.getLabel());
    assertEquals(ParseNodeKind.PARSE_NODE_KIND_NONTERMINAL, root.getKind());
    assertFalse(tree.hasPennTreebank());

    final ParseNode s = root.getChildren(0);
    assertEquals("S", s.getLabel());
    final ParseNode np = s.getChildren(0);
    final ParseNode vp = s.getChildren(1);
    assertEquals("NP", np.getLabel());
    assertEquals("VP", vp.getLabel());

    // Terminals carry the POS label, link to the token by index, and adopt the token's span.
    final ParseNode the = np.getChildren(0);
    final ParseNode dog = np.getChildren(1);
    final ParseNode barked = vp.getChildren(0);
    assertEquals(ParseNodeKind.PARSE_NODE_KIND_TERMINAL, the.getKind());
    assertEquals("DT", the.getLabel());
    assertEquals(0, the.getTokenIndex());
    assertEquals(0, the.getSpan().getStart());
    assertEquals(3, the.getSpan().getEnd());
    assertEquals("NN", dog.getLabel());
    assertEquals(1, dog.getTokenIndex());
    assertEquals("VBD", barked.getLabel());
    assertEquals(2, barked.getTokenIndex());
    assertEquals(0, the.getChildrenCount());
  }

  @Test
  void nonterminalSpansCoverTheirDescendants() {
    final ParseNode root =
        ParseTreeConverter.toParseTree(Parse.parseParse(TREEBANK), sentence(), true, false)
            .getRoot();
    final ParseNode s = root.getChildren(0);
    final ParseNode np = s.getChildren(0);
    final ParseNode vp = s.getChildren(1);

    assertEquals(0, np.getSpan().getStart());   // "The dog"
    assertEquals(7, np.getSpan().getEnd());
    assertEquals(8, vp.getSpan().getStart());   // "barked"
    assertEquals(14, vp.getSpan().getEnd());
    assertEquals(0, s.getSpan().getStart());    // whole sentence
    assertEquals(14, s.getSpan().getEnd());
    assertEquals(0, root.getSpan().getStart());
    assertEquals(14, root.getSpan().getEnd());
  }

  @Test
  void bracketedViewIsStandardTreebankString() {
    final ParseTree tree =
        ParseTreeConverter.toParseTree(Parse.parseParse(TREEBANK), sentence(), false, true);

    assertFalse(tree.hasRoot());
    final String ptb = tree.getPennTreebank();
    assertTrue(ptb.startsWith("(TOP"), ptb);
    assertTrue(ptb.contains("(NP"));
    assertTrue(ptb.contains("(VP"));
    assertTrue(ptb.contains("(DT The)"));
    assertTrue(ptb.contains("barked"));
  }

  @Test
  void populatesBothViewsWhenRequested() {
    final ParseTree tree =
        ParseTreeConverter.toParseTree(Parse.parseParse(TREEBANK), sentence(), true, true);
    assertTrue(tree.hasRoot());
    assertTrue(tree.hasPennTreebank());
  }
}
