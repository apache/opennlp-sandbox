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
import opennlp.tools.parser.Parser;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.ParseNode;
import org.apache.opennlp.grpc.v1.ParseNodeKind;
import org.apache.opennlp.grpc.v1.ParseTree;

/**
 * Converts an OpenNLP {@link Parse} into the gRPC {@link ParseTree} views. Kept separate from
 * {@link ClassicStepRunner} so the conversion is unit-testable from a hand-built parse, with no
 * parser model required.
 *
 * <p>The structured tree links terminals back to the sentence's tokens by index (rather than
 * repeating token text), and computes nonterminal spans from their descendants — both in the
 * document UTF-16 coordinate space the final offset-encoding pass expects. The bracketed view is
 * OpenNLP's own {@link Parse#show(StringBuffer)} output (Penn-Treebank style).</p>
 */
final class ParseTreeConverter {

  private ParseTreeConverter() {
  }

  /**
   * Builds the requested parse representations for one sentence.
   *
   * @param parse The sentence's parse, as returned by the parser.
   * @param sentence The sentence whose tokens terminals are linked to (by left-to-right order).
   * @param structured Whether to populate the nested {@link ParseNode} tree.
   * @param bracketed Whether to populate the Penn-Treebank string.
   *
   * @return A {@link ParseTree} carrying exactly the requested views.
   */
  static ParseTree toParseTree(
      Parse parse, AnnotatedSentence sentence, boolean structured, boolean bracketed) {
    final ParseTree.Builder tree = ParseTree.newBuilder();
    if (structured) {
      tree.setRoot(toParseNode(parse, sentence, new int[] {0}));
    }
    if (bracketed) {
      final StringBuffer sb = new StringBuffer();
      parse.show(sb);
      tree.setPennTreebank(sb.toString());
    }
    return tree.build();
  }

  /**
   * Converts a {@link Parse} node into a {@link ParseNode}. A preterminal (a POS node whose sole
   * child is the token leaf) becomes a TERMINAL carrying the POS label and a {@code token_index}
   * into the sentence; every other node becomes a NONTERMINAL whose span covers its children. The
   * token cursor advances left-to-right, matching the order tokens were parsed.
   */
  static ParseNode toParseNode(Parse parse, AnnotatedSentence sentence, int[] tokenCursor) {
    final Parse[] children = parse.getChildren();
    if (children.length == 1 && Parser.TOK_NODE.equals(children[0].getType())) {
      final int index = tokenCursor[0]++;
      final AnnotationSpan span = index < sentence.getTokensCount()
          ? sentence.getTokens(index).getAnnotationSpan()
          : AnnotationSpan.newBuilder()
              .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT).build();
      return ParseNode.newBuilder()
          .setKind(ParseNodeKind.PARSE_NODE_KIND_TERMINAL)
          .setLabel(parse.getType())
          .setSpan(span)
          .setTokenIndex(index)
          .setProbability(parse.getProb())
          .build();
    }
    final ParseNode.Builder node = ParseNode.newBuilder()
        .setKind(ParseNodeKind.PARSE_NODE_KIND_NONTERMINAL)
        .setLabel(parse.getType())
        .setProbability(parse.getProb());
    int start = Integer.MAX_VALUE;
    int end = Integer.MIN_VALUE;
    for (Parse child : children) {
      if (Parser.TOK_NODE.equals(child.getType())) {
        // A bare token leaf without a POS parent (not expected in well-formed parses); advance
        // the cursor so later token indices stay aligned.
        tokenCursor[0]++;
        continue;
      }
      final ParseNode childNode = toParseNode(child, sentence, tokenCursor);
      node.addChildren(childNode);
      start = Math.min(start, childNode.getSpan().getStart());
      end = Math.max(end, childNode.getSpan().getEnd());
    }
    if (start == Integer.MAX_VALUE) {
      start = 0;
      end = 0;
    }
    return node.setSpan(AnnotationSpan.newBuilder()
        .setStart(start)
        .setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build()).build();
  }
}
