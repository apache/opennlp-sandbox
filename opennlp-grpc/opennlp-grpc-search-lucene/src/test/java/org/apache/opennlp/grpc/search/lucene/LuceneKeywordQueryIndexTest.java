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
package org.apache.opennlp.grpc.search.lucene;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.spi.search.SearchRecord;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.MatchedSpan;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.TermMatchMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Lucene keyword component: BM25 relevance ordering, score
 * normalization, and matched spans extracted from indexed offsets.
 */
class LuceneKeywordQueryIndexTest {

  /** Builds one keyword-only candidate over the given indexed text. */
  private static QueryCandidate candidate(String documentId, String text) {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setDocId(documentId)
        .setRawText(text)
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .build();
    final AnnotationSpan span = AnnotationSpan.newBuilder()
        .setStart(0)
        .setEnd(text.length())
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
    return new QueryCandidate(
        new SearchRecord(documentId, documentId + ":0", document, span, text), null);
  }

  private static Map<String, Double> scoresByDocument(List<KeywordQueryIndex.Hit> hits) {
    return hits.stream().collect(Collectors.toMap(
        hit -> hit.record().documentId(), KeywordQueryIndex.Hit::score));
  }

  @Test
  void scoresRepeatedTermsHigherAndNormalizesToTheBestHit() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "alpha alpha alpha"),
        candidate("doc-2", "alpha beta"),
        candidate("doc-3", "gamma delta")));

    final List<KeywordQueryIndex.Hit> hits =
        index.term("alpha", TermMatchMode.TERM_MATCH_MODE_UNSPECIFIED);

    assertEquals(2, hits.size());
    final Map<String, Double> scores = scoresByDocument(hits);
    assertEquals(1.0, scores.get("doc-1"));
    assertTrue(scores.get("doc-2") > 0 && scores.get("doc-2") < 1.0,
        "the weaker hit must score in (0, 1): " + scores);
  }

  @Test
  void allModeRequiresEveryQueryTerm() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "alpha beta gamma"),
        candidate("doc-2", "alpha delta")));

    final List<KeywordQueryIndex.Hit> hits =
        index.term("alpha beta", TermMatchMode.TERM_MATCH_MODE_ALL);

    assertEquals(List.of("doc-1"),
        hits.stream().map(hit -> hit.record().documentId()).toList());
  }

  @Test
  void anyModeMatchesAnyQueryTerm() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "alpha beta gamma"),
        candidate("doc-2", "alpha delta"),
        candidate("doc-3", "epsilon")));

    final List<KeywordQueryIndex.Hit> hits =
        index.term("beta delta", TermMatchMode.TERM_MATCH_MODE_ANY);

    assertEquals(2, hits.size());
  }

  @Test
  void termSpansCarryIndexedTextOffsetsAndTheMatchedTerm() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "Alpha beta gamma")));

    final List<KeywordQueryIndex.Hit> hits =
        index.term("beta", TermMatchMode.TERM_MATCH_MODE_ANY);

    assertEquals(1, hits.size());
    final MatchedSpan span = hits.getFirst().matchedSpans().getFirst();
    assertEquals(6, span.getStart());
    assertEquals(10, span.getEnd());
    assertEquals("beta", span.getTerm());
  }

  @Test
  void matchingIsCaseInsensitiveThroughTheStandardAnalyzer() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "Grüße from the Café")));

    assertEquals(1, index.term("grüße", TermMatchMode.TERM_MATCH_MODE_ANY).size());
    assertEquals(1, index.term("CAFÉ", TermMatchMode.TERM_MATCH_MODE_ANY).size());
  }

  @Test
  void phraseMatchesInOrderAndSpansTheWholeOccurrence() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "the quick brown fox"),
        candidate("doc-2", "the brown quick fox")));

    final List<KeywordQueryIndex.Hit> hits = index.phrase("quick brown", 0);

    assertEquals(List.of("doc-1"),
        hits.stream().map(hit -> hit.record().documentId()).toList());
    final MatchedSpan span = hits.getFirst().matchedSpans().getFirst();
    assertEquals(4, span.getStart());
    assertEquals(15, span.getEnd());
    assertEquals("quick brown", span.getTerm());
  }

  @Test
  void phraseSlopAdmitsBoundedGaps() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "quick brown lazy fox")));

    assertEquals(0, index.phrase("quick fox", 1).size());
    assertEquals(1, index.phrase("quick fox", 2).size());
  }

  @Test
  void unmatchedQueriesReturnNoHits() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "alpha beta")));

    assertEquals(0, index.term("zeta", TermMatchMode.TERM_MATCH_MODE_ANY).size());
    assertEquals(0, index.phrase("beta alpha", 0).size());
  }

  @Test
  void queriesWithoutAnalyzableTermsFailLoud() {
    final LuceneKeywordQueryIndex index = new LuceneKeywordQueryIndex(List.of(
        candidate("doc-1", "alpha")));

    final AnalysisException term = assertThrows(AnalysisException.class,
        () -> index.term("  !!  ", TermMatchMode.TERM_MATCH_MODE_ANY));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, term.getFailureType());
    final AnalysisException phrase = assertThrows(AnalysisException.class,
        () -> index.phrase("...", 0));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, phrase.getFailureType());
  }

  @Test
  void rejectsDuplicateChunkIds() {
    final QueryCandidate first = candidate("doc-1", "alpha");
    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> new LuceneKeywordQueryIndex(List.of(first, candidate("doc-1", "beta"))));
    assertTrue(failure.getMessage().contains("doc-1:0"));
  }

  @Test
  void rejectsNullCandidates() {
    assertThrows(IllegalArgumentException.class, () -> new LuceneKeywordQueryIndex(null));
  }
}
