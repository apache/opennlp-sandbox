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
package opennlp.tools.similarity.apps.taxo_builder;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxonomyBuildMatchTest {

  @Test
  void testTaxonomySeedImport() {
    AriAdapter ad = new AriAdapter();
    ad.getChainsFromARIfile("src/test/resources/taxonomies/irs_dom.ari");
    assertTrue(ad.lemma_AssocWords.size() > 0);
  }

  @Test
  @Disabled // TODO Check if this test works as expected, and: why it takes so much time.
  void testTaxonomyBuild() {
    TaxonomyExtenderViaMebMining self = new TaxonomyExtenderViaMebMining();
    self.extendTaxonomy("src/test/resources/taxonomies/irs_dom.ari", "tax", "en");
    self.close();
    assertTrue(self.getAssocWords_ExtendedAssocWords().size() > 0);
  }

  @Test
  void testTaxonomyMatch() {
    TaxoQuerySnapshotMatcher matcher = new TaxoQuerySnapshotMatcher(
        "src/test/resources/taxonomies/irs_domTaxo.dat");
    int score = matcher.getTaxoScore(
        "Can Form 1040 EZ be used to claim the earned income credit.",
        "Can Form 1040EZ be used to claim the earned income credit? . " +
            "Must I be entitled to claim a child as a dependent to claim the earned income credit based on the child being ");

    assertTrue(score > 3);
    matcher.close();
  }
}
