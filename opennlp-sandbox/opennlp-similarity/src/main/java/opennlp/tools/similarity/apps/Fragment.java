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

package opennlp.tools.similarity.apps;

import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;

public class Fragment {

  public String resultText; // result

  public double score;

  public String fragment; // original

  public String sourceURL;

  public Fragment(String text, double score) {
    this.resultText = text;
    this.score = score;
  }

  public String getResultText() {
    return resultText;
  }

  public void setResultText(String resultText) {
    this.resultText = resultText;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public String getFragment() {
    return fragment;
  }

  public void setFragment(String fragment) {
    this.fragment = fragment;
  }

  public String getSourceURL() {
    return sourceURL;
  }

  public void setSourceURL(String sourceURL) {
    this.sourceURL = sourceURL;
  }

  public String toString() {
    return this.resultText;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    Fragment fragment = (Fragment) o;

    if (resultText == null && fragment.resultText == null) {
      return true;
    } else if ((resultText == null && fragment.resultText != null)
        || (resultText != null && fragment.resultText == null)) {
      return false;
    }

    StringDistanceMeasurer sdm = new StringDistanceMeasurer();
    return sdm.measureStringDistance(resultText, fragment.resultText) > 0.8;
  }

  @Override
  public int hashCode() {
    return resultText != null ? resultText.hashCode() : 0;
  }
}
