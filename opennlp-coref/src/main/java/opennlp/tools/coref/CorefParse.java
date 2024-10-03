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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.Span;

/**
 * A container class encapsulating results of a co-reference parse operation.
 *
 * @see Parse
 * @see DiscourseEntity
 */
public class CorefParse {

  private final Map<Parse, Integer> parseMap;
  private final List<Parse> parses;

  public CorefParse(List<Parse> parses, DiscourseEntity[] entities) {
    this.parses = parses;
    parseMap = new HashMap<>();
    for (int ei = 0, en = entities.length; ei < en; ei++) {
      if (entities[ei].getNumMentions() > 1) {
        for (Iterator<MentionContext> mi = entities[ei].getMentions(); mi.hasNext();) {
          MentionContext mc = mi.next();
          Parse mentionParse = ((DefaultParse) mc.getParse()).getParse();
          parseMap.put(mentionParse, ei + 1);
          //System.err.println("CorefParse: "+mc.getParse().hashCode()+" -> "+ (ei+1));
        }
      }
    }
  }

  /**
   * @return Retrieves the resulting parse mapping.
   */
  public Map<Parse, Integer> getParseMap() {
    return parseMap;
  }

  /**
   * Prints all parses to std-out.
   */
  public void show() {
    for (Parse p : parses) {
      show(p);
      System.out.println();
    }
  }

  private void show(Parse p) {
    int start;
    start = p.getSpan().getStart();
    if (!p.getType().equals(Parser.TOK_NODE)) {
      System.out.print("(");
      System.out.print(p.getType());
      if (parseMap.containsKey(p)) {
        System.out.print("#" + parseMap.get(p));
      }
      //System.out.print(p.hashCode()+"-"+parseMap.containsKey(p));
      System.out.print(" ");
    }
    Parse[] children = p.getChildren();
    for (Parse c : children) {
      Span s = c.getSpan();
      if (start < s.getStart()) {
        System.out.print(p.getText().substring(start, s.getStart()));
      }
      show(c);
      start = s.getEnd();
    }
    System.out.print(p.getText().substring(start, p.getSpan().getEnd()));
    if (!p.getType().equals(Parser.TOK_NODE)) {
      System.out.print(")");
    }
  }
}
