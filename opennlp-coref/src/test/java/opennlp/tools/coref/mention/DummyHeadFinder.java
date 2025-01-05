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

package opennlp.tools.coref.mention;

// just a stub implementation
class DummyHeadFinder implements HeadFinder {

  @Override
  public opennlp.tools.coref.mention.Parse getHead(opennlp.tools.coref.mention.Parse parse) {
    return null;
  }

  @Override
  public int getHeadIndex(opennlp.tools.coref.mention.Parse parse) {
    return 0;
  }

  @Override
  public opennlp.tools.coref.mention.Parse getLastHead(opennlp.tools.coref.mention.Parse p) {
    return null;
  }

  @Override
  public opennlp.tools.coref.mention.Parse getHeadToken(opennlp.tools.coref.mention.Parse np) {
    return null;
  }
}
