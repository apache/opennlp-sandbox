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

package opennlp.tools.textsimilarity;

import java.util.Comparator;

public class ParseTreeChunkComparable implements Comparator<ParseTreeChunk> {
  public int compare(ParseTreeChunk ch1, ParseTreeChunk ch2) {
    for (int i = 0; i < ch1.getLemmas().size() && i < ch2.getLemmas().size(); i++) {
      if (!(ch1.getLemmas().get(i).equals(ch2.getLemmas().get(i)) && ch1
          .getPOSs().get(i).equals(ch2.getPOSs().get(i))))
        return -1;
    }
    return 0;

  }
}
