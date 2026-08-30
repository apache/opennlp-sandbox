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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.v1.MatchedSpan;
import org.apache.opennlp.grpc.v1.TermMatchMode;

/**
 * Immutable keyword component over one candidate snapshot, backed by an in-memory
 * Lucene index: BM25 relevance from the index searcher, matched spans from the Lucene
 * matches API over indexed offsets. Offsets are UTF-16 code unit positions in the
 * retained indexed text, matching the coordinate space of the built-in term component.
 */
final class LuceneKeywordQueryIndex implements KeywordQueryIndex {

  private static final String FIELD_TEXT = "text";

  private final List<QueryCandidate> candidates;
  private final Analyzer analyzer;
  private final IndexSearcher searcher;

  /**
   * Indexes one immutable candidate snapshot.
   *
   * @param candidates Candidate records in index order. Must not be {@code null} and
   *     must not contain duplicate chunk ids.
   * @throws IllegalArgumentException If {@code candidates} is {@code null} or holds a
   *     duplicate chunk id.
   */
  LuceneKeywordQueryIndex(List<QueryCandidate> candidates) {
    if (candidates == null) {
      throw new IllegalArgumentException("candidates must not be null");
    }
    this.candidates = List.copyOf(candidates);
    final Set<String> chunkIds = new LinkedHashSet<>();
    for (QueryCandidate candidate : this.candidates) {
      if (!chunkIds.add(candidate.record().chunkId())) {
        throw new IllegalArgumentException("candidates contain duplicate chunk id '"
            + candidate.record().chunkId() + "'");
      }
    }
    this.analyzer = new StandardAnalyzer();
    try {
      final ByteBuffersDirectory directory = new ByteBuffersDirectory();
      try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
        final FieldType type = new FieldType();
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        type.setTokenized(true);
        type.freeze();
        for (QueryCandidate candidate : this.candidates) {
          final Document document = new Document();
          document.add(new Field(FIELD_TEXT, candidate.record().indexedText(), type));
          writer.addDocument(document);
        }
        writer.commit();
      }
      // The reader holds only heap segments of the in-memory directory; the component
      // lives exactly as long as its snapshot, so there is nothing external to close.
      this.searcher = new IndexSearcher(DirectoryReader.open(directory));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to build the in-memory keyword index", e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<Hit> term(String text, TermMatchMode mode) {
    final List<String> terms = distinctTerms(text, "term.text");
    final BooleanQuery.Builder query = new BooleanQuery.Builder();
    final BooleanClause.Occur occur = mode == TermMatchMode.TERM_MATCH_MODE_ALL
        ? BooleanClause.Occur.MUST : BooleanClause.Occur.SHOULD;
    for (String term : terms) {
      query.add(new TermQuery(new Term(FIELD_TEXT, term)), occur);
    }
    return execute(query.build(), String.join(" ", terms));
  }

  /** {@inheritDoc} */
  @Override
  public List<Hit> phrase(String text, int slop) {
    final List<String> terms = analyzedTerms(text, "phrase.text");
    final PhraseQuery.Builder query = new PhraseQuery.Builder().setSlop(slop);
    for (String term : terms) {
      query.add(new Term(FIELD_TEXT, term));
    }
    return execute(query.build(), String.join(" ", terms));
  }

  /**
   * Runs one leaf query and converts its BM25 results into normalized provider hits.
   *
   * @param query The rewritable leaf query.
   * @param fallbackLabel The span label when a match does not name its own term.
   * @return Hits with scores normalized by the best BM25 score.
   */
  private List<Hit> execute(Query query, String fallbackLabel) {
    try {
      final TopDocs top = searcher.search(query, Math.max(1, candidates.size()));
      if (top.scoreDocs.length == 0) {
        return List.of();
      }
      final Weight weight = searcher.createWeight(
          searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
      double maximum = 0;
      for (ScoreDoc hit : top.scoreDocs) {
        maximum = Math.max(maximum, hit.score);
      }
      final List<Hit> hits = new ArrayList<>(top.scoreDocs.length);
      for (ScoreDoc hit : top.scoreDocs) {
        hits.add(new Hit(candidates.get(hit.doc).record(), hit.score / maximum,
            matchedSpans(weight, hit.doc, fallbackLabel)));
      }
      return List.copyOf(hits);
    } catch (IOException e) {
      throw new UncheckedIOException("Keyword query failed on the in-memory index", e);
    }
  }

  /**
   * Extracts the matched offsets of one hit through the Lucene matches API.
   *
   * @param weight The weight of the executed query.
   * @param doc The global hit document id.
   * @param fallbackLabel The span label when a match does not name its own term.
   * @return The matched spans in indexed-text UTF-16 offsets.
   */
  private List<MatchedSpan> matchedSpans(Weight weight, int doc, String fallbackLabel)
      throws IOException {
    final List<MatchedSpan> spans = new ArrayList<>();
    for (LeafReaderContext leaf : searcher.getIndexReader().leaves()) {
      if (doc < leaf.docBase || doc >= leaf.docBase + leaf.reader().maxDoc()) {
        continue;
      }
      final Matches matches = weight.matches(leaf, doc - leaf.docBase);
      if (matches == null) {
        continue;
      }
      final MatchesIterator iterator = matches.getMatches(FIELD_TEXT);
      if (iterator == null) {
        continue;
      }
      while (iterator.next()) {
        spans.add(MatchedSpan.newBuilder()
            .setStart(iterator.startOffset())
            .setEnd(iterator.endOffset())
            .setTerm(label(iterator.getQuery(), fallbackLabel))
            .build());
      }
    }
    return spans;
  }

  /** Labels one match by its leaf term when the match query is a plain term query. */
  private static String label(Query query, String fallbackLabel) {
    if (query instanceof TermQuery termQuery) {
      return termQuery.getTerm().text();
    }
    return fallbackLabel;
  }

  /**
   * Analyzes query text into its distinct terms, preserving first-seen order.
   *
   * @param text The query text.
   * @param what The query field, for the error message.
   * @return The distinct analyzed terms.
   * @throws AnalysisException {@code INVALID_ARGUMENT} if no analyzable term remains.
   */
  private List<String> distinctTerms(String text, String what) {
    return List.copyOf(new LinkedHashSet<>(analyzedTerms(text, what)));
  }

  /**
   * Analyzes query text into its terms, in order and with repetitions.
   *
   * @param text The query text.
   * @param what The query field, for the error message.
   * @return The analyzed terms.
   * @throws AnalysisException {@code INVALID_ARGUMENT} if no analyzable term remains.
   */
  private List<String> analyzedTerms(String text, String what) {
    final List<String> terms = new ArrayList<>();
    try (TokenStream stream = analyzer.tokenStream(FIELD_TEXT, text == null ? "" : text)) {
      final CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
      stream.reset();
      while (stream.incrementToken()) {
        terms.add(term.toString());
      }
      stream.end();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to analyze query text", e);
    }
    if (terms.isEmpty()) {
      throw AnalysisException.invalidArgument(
          what + " contains no analyzable terms: '" + text + "'");
    }
    return terms;
  }
}
