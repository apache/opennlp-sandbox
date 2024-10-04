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

import opennlp.tools.coref.mention.Parse;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides default implementation of many of the methods in the {@link Parse} interface.
 */
public abstract class AbstractParse implements Parse {

  @Override
  public boolean isCoordinatedNounPhrase() {
    List<Parse> parts = getSyntacticChildren();
    if (parts.size() >= 2) {
      for (int pi = 1; pi < parts.size(); pi++) {
        Parse child = parts.get(pi);
        String ctype = child.getSyntacticType();
        if (ctype != null && ctype.equals("CC") && !child.toString().equals("&")) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public List<Parse> getNounPhrases() {
    List<Parse> parts = getSyntacticChildren();
    List<Parse> nps = new ArrayList<>();
    while (!parts.isEmpty()) {
      List<Parse> newParts = new ArrayList<>();
      for (Parse cp : parts) {
        //System.err.println("AbstractParse.getNounPhrases "+parts.get(pi).getClass());
        if (cp.isNounPhrase()) {
          nps.add(cp);
        }
        if (!cp.isToken()) {
          newParts.addAll(cp.getSyntacticChildren());
        }
      }
      parts = newParts;
    }
    return nps;
  }
}
