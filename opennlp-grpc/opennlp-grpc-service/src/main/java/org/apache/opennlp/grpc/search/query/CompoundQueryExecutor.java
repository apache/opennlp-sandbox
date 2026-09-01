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
 * KIND, either express or implied.  See the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.search.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.search.SearchResult;
import org.apache.opennlp.grpc.search.query.QueryTermAnalyzer.Term;
import org.apache.opennlp.grpc.v1.BoostClause;
import org.apache.opennlp.grpc.v1.CelScore;
import org.apache.opennlp.grpc.v1.JoinClause;
import org.apache.opennlp.grpc.v1.JoinFusion;
import org.apache.opennlp.grpc.v1.JoinOperator;
import org.apache.opennlp.grpc.v1.MatchedSpan;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.QueryNode;
import org.apache.opennlp.grpc.v1.ScoreNormalization;
import org.apache.opennlp.grpc.v1.TermMatchMode;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;

/**
 * Executes a validated compound query tree over one index's retained candidates under
 * the normative score algebra of {@code opennlp_query.proto}: leaf scores in [0, 1],
 * semantic scores as {@code (cosine + 1) / 2}, term and phrase relevance under per-query
 * max normalization, mean for AND, maximum for OR, gate-only exclusions and filters,
 * reciprocal-rank fusion as {@code 1 / (60 + rank)} with the summed value normalized by
 * the query's top value so the node still yields scores in [0, 1], and boosts that
 * multiply and clamp without changing membership. Final ranking orders by root score
 * descending with ties broken by chunk id then document id.
 */
public final class CompoundQueryExecutor {

  /** Rank offset of reciprocal-rank fusion, per the wire contract. */
  static final int RRF_OFFSET = 60;

  private final CelQueryEvaluator celEvaluator;

  /**
   * Creates an executor.
   *
   * @param celEvaluator Installed CEL evaluator, or {@code null} when none is available;
   *     queries containing CEL clauses then report UNIMPLEMENTED.
   */
  public CompoundQueryExecutor(CelQueryEvaluator celEvaluator) {
    this.celEvaluator = celEvaluator;
  }

  /** Embeds one semantic clause document through the index's pinned embedding route. */
  public interface QueryEmbedder {

    /**
     * Embeds one query document.
     *
     * @param queryDocument Semantic clause document with non-blank raw text.
     * @return Query vector matching the index dimension.
     * @throws AnalysisException If embedding or route resolution fails.
     */
    float[] embed(OpenNlpDocument queryDocument);
  }

  /** Executes one semantic leaf through the index provider selected by the descriptor. */
  @FunctionalInterface
  public interface SemanticSearcher {

    /**
     * Searches the provider's vector component.
     *
     * @param queryVector Embedded semantic query.
     * @param topK Maximum returned results.
     * @return Ranked provider results.
     */
    List<SearchResult> search(float[] queryVector, int topK);
  }

  /**
   * One ranked compound query result.
   *
   * @param candidate The matched candidate.
   * @param score Root algebra score in [0, 1].
   * @param matchedSpans Keyword and phrase matches in the candidate's indexed text,
   *     ordered by start offset.
   */
  public record QueryHit(QueryCandidate candidate, double score, List<MatchedSpan> matchedSpans) {
  }

  /**
   * Executes one validated compound query.
   *
   * @param root Root query node.
   * @param candidates Index candidates in stable index order.
   * @param embedder Embedder for semantic clauses.
   * @param topK Maximum hits to return, at least one.
   * @return Ranked hits, largest score first.
   * @throws AnalysisException If the tree is invalid, CEL support is missing, or a
   *     clause fails to execute.
   */
  public List<QueryHit> execute(
      QueryNode root, List<QueryCandidate> candidates, QueryEmbedder embedder, int topK) {
    return execute(root, candidates, embedder, (queryVector, limit) -> candidates.stream()
        .map(candidate -> new SearchResult(candidate.record(),
            cosine(queryVector, candidate.requireVector())))
        .sorted(java.util.Comparator.comparingDouble(SearchResult::score).reversed())
        .limit(limit)
        .toList(), new TermsKeywordQueryIndex(candidates), topK);
  }

  /**
   * Executes one validated compound query, delegating semantic leaves to the selected
   * index provider.
   *
   * @param root Root query node.
   * @param candidates Index candidates in stable index order.
   * @param embedder Embedder for semantic clauses.
   * @param semanticSearcher Provider vector search for semantic clauses.
   * @param topK Maximum hits to return, at least one.
   * @return Ranked hits, largest score first.
   * @throws AnalysisException If the tree is invalid, CEL support is missing, or a
   *     clause fails to execute.
   */
  public List<QueryHit> execute(QueryNode root, List<QueryCandidate> candidates,
      QueryEmbedder embedder, SemanticSearcher semanticSearcher, int topK) {
    return execute(root, candidates, embedder, semanticSearcher,
        new TermsKeywordQueryIndex(candidates), topK);
  }

  /**
   * Executes one validated compound query with both semantic and keyword leaves
   * delegated to their selected provider instances.
   *
   * @param root Root query node.
   * @param candidates Retained candidate metadata in stable order.
   * @param embedder Semantic query embedder.
   * @param semanticSearcher Vector-component provider.
   * @param keywordIndex Keyword-component provider.
   * @param topK Maximum returned hits.
   * @return Ranked compound hits.
   */
  public List<QueryHit> execute(QueryNode root, List<QueryCandidate> candidates,
      QueryEmbedder embedder, SemanticSearcher semanticSearcher,
      KeywordQueryIndex keywordIndex, int topK) {
    CompoundQueryValidator.validate(root);
    if (candidates == null || embedder == null || semanticSearcher == null) {
      throw new IllegalArgumentException(
          "compound query dependencies must not be null");
    }
    if (keywordIndex == null && CompoundQueryValidator.containsKeywordClause(root)) {
      throw AnalysisException.unimplemented(
          "The selected index has no configured keyword query provider");
    }
    if (topK < 1) {
      throw AnalysisException.invalidArgument("top_k must be positive, was " + topK);
    }
    if (celEvaluator == null && CompoundQueryValidator.containsCelClause(root)) {
      throw AnalysisException.unimplemented("No CEL evaluator is installed; cel_filter and "
          + "cel_calculator clauses are unavailable on this server");
    }
    if (candidates.isEmpty()) {
      return List.of();
    }
    final Context context = new Context(candidates, embedder, semanticSearcher, keywordIndex);
    final NodeResult result = evaluate(root, context);
    final List<String> ranked = new ArrayList<>(result.scores().keySet());
    ranked.sort(Comparator
        .comparingDouble((String chunkId) -> result.scores().get(chunkId)).reversed()
        .thenComparing(chunkId -> chunkId)
        .thenComparing(chunkId -> context.candidate(chunkId).record().documentId()));
    final List<QueryHit> hits = new ArrayList<>(Math.min(topK, ranked.size()));
    for (String chunkId : ranked.subList(0, Math.min(topK, ranked.size()))) {
      final List<MatchedSpan> spans =
          new ArrayList<>(result.spans().getOrDefault(chunkId, List.of()));
      spans.sort(Comparator.comparingInt(MatchedSpan::getStart)
          .thenComparingInt(MatchedSpan::getEnd));
      hits.add(new QueryHit(context.candidate(chunkId), result.scores().get(chunkId),
          List.copyOf(spans)));
    }
    return List.copyOf(hits);
  }

  /** Shared per-execution state for provider-delegated query clauses. */
  private static final class Context {
    private final List<QueryCandidate> candidates;
    private final Map<String, QueryCandidate> byChunkId = new LinkedHashMap<>();
    private final QueryEmbedder embedder;
    private final SemanticSearcher semanticSearcher;
    private final KeywordQueryIndex keywordIndex;

    Context(List<QueryCandidate> candidates, QueryEmbedder embedder,
        SemanticSearcher semanticSearcher, KeywordQueryIndex keywordIndex) {
      this.candidates = candidates;
      this.embedder = embedder;
      this.semanticSearcher = semanticSearcher;
      this.keywordIndex = keywordIndex;
      for (QueryCandidate candidate : candidates) {
        byChunkId.put(candidate.record().chunkId(), candidate);
      }
    }

    QueryCandidate candidate(String chunkId) {
      return byChunkId.get(chunkId);
    }
  }

  /**
   * Membership, scores, and spans of one evaluated node. Score map iteration follows
   * candidate order; membership is the key set.
   *
   * @param scores Score in [0, 1] per matched chunk id.
   * @param spans Matched spans per chunk id; keyword and phrase leaves contribute.
   */
  private record NodeResult(Map<String, Double> scores, Map<String, List<MatchedSpan>> spans) {

    static NodeResult empty() {
      return new NodeResult(new LinkedHashMap<>(), new HashMap<>());
    }
  }

  /**
   * Evaluates one scoring node.
   *
   * @param node Query node; never a bare filter or calculator, which only joins handle.
   * @param context Execution state.
   * @return The node's membership, scores, and spans.
   */
  private NodeResult evaluate(QueryNode node, Context context) {
    return switch (node.getKindCase()) {
      case SEMANTIC -> evaluateSemantic(node.getSemantic().getDocument(), context);
      case TERM -> evaluateTerm(node.getTerm().getText(), node.getTerm().getMode(), context);
      case PHRASE -> evaluatePhrase(
          node.getPhrase().getText(), node.getPhrase().getSlop(), context);
      case JOIN -> evaluateJoin(node.getJoin(), context);
      case BOOST -> evaluateBoost(node.getBoost(), context);
      case CEL_FILTER, CEL_CALCULATOR, KIND_NOT_SET -> throw new IllegalStateException(
          "Validation admits " + node.getKindCase() + " only inside a join");
    };
  }

  /**
   * Scores every candidate by embedded similarity.
   *
   * @param document Semantic clause document.
   * @param context Execution state.
   * @return Scores of {@code (cosine + 1) / 2} for every candidate.
   */
  private NodeResult evaluateSemantic(OpenNlpDocument document, Context context) {
    final float[] query = context.embedder.embed(document);
    if (query == null) {
      throw AnalysisException.failedPrecondition(
          "Semantic clause embedding provider returned a null vector");
    }
    final NodeResult result = NodeResult.empty();
    final List<SearchResult> searched =
        context.semanticSearcher.search(query, context.candidates.size());
    if (searched == null || searched.size() > context.candidates.size()) {
      throw new IllegalStateException(
          "Semantic search provider returned an invalid result count");
    }
    for (SearchResult searchedResult : searched) {
      if (searchedResult == null || !Double.isFinite(searchedResult.score())
          || searchedResult.score() < -1 || searchedResult.score() > 1) {
        throw new IllegalStateException(
            "Semantic search provider returned an invalid result");
      }
      final String chunkId = searchedResult.record().chunkId();
      final QueryCandidate candidate = context.candidate(chunkId);
      if (candidate == null || !candidate.record().equals(searchedResult.record())
          || result.scores().containsKey(chunkId)) {
        throw new IllegalStateException(
            "Semantic search provider returned an unknown or duplicate candidate");
      }
      result.scores().put(chunkId,
          Math.min(1, Math.max(0, (searchedResult.score() + 1) / 2)));
    }
    return result;
  }

  /**
   * Matches analyzed keyword terms with per-query max normalization.
   *
   * @param text Term clause text.
   * @param mode ANY or ALL combination; UNSPECIFIED defaults to ANY.
   * @param context Execution state.
   * @return Matched candidates with normalized scores and occurrence spans.
   */
  private NodeResult evaluateTerm(String text, TermMatchMode mode, Context context) {
    return keywordResults(context.keywordIndex.term(text, mode), context);
  }

  /**
   * Matches analyzed terms in order within a slop bound.
   *
   * @param text Phrase clause text.
   * @param slop Extra positions tolerated between adjacent terms.
   * @param context Execution state.
   * @return Matched candidates with normalized occurrence scores and phrase spans.
   */
  private NodeResult evaluatePhrase(String text, int slop, Context context) {
    return keywordResults(context.keywordIndex.phrase(text, slop), context);
  }

  /** Validates provider-owned keyword results and converts them into node algebra. */
  private static NodeResult keywordResults(
      List<KeywordQueryIndex.Hit> hits, Context context) {
    if (hits == null || hits.size() > context.candidates.size()) {
      throw new IllegalStateException("Keyword search provider returned an invalid result count");
    }
    final NodeResult result = NodeResult.empty();
    for (KeywordQueryIndex.Hit hit : hits) {
      if (hit == null || !Double.isFinite(hit.score())
          || hit.score() < 0 || hit.score() > 1) {
        throw new IllegalStateException("Keyword search provider returned an invalid result");
      }
      final String chunkId = hit.record().chunkId();
      final QueryCandidate candidate = context.candidate(chunkId);
      if (candidate == null || !candidate.record().equals(hit.record())
          || result.scores().putIfAbsent(chunkId, hit.score()) != null) {
        throw new IllegalStateException(
            "Keyword search provider returned an unknown or duplicate candidate");
      }
      result.spans().put(chunkId, hit.matchedSpans());
    }
    return result;
  }

  /**
   * Attempts one in-order phrase match starting at a term position.
   *
   * @param terms Analyzed candidate terms.
   * @param start Position of the tentative first phrase term.
   * @param phrase Analyzed phrase term texts.
   * @param slop Extra positions tolerated between adjacent terms.
   * @return Position of the final matched term, or -1 when no match starts here.
   */
  private static int matchPhraseAt(List<Term> terms, int start, List<String> phrase, int slop) {
    if (!terms.get(start).text().equals(phrase.getFirst())) {
      return -1;
    }
    int previous = start;
    for (int phraseIndex = 1; phraseIndex < phrase.size(); phraseIndex++) {
      int found = -1;
      final int limit = Math.min(terms.size() - 1, previous + 1 + slop);
      for (int position = previous + 1; position <= limit; position++) {
        if (terms.get(position).text().equals(phrase.get(phraseIndex))) {
          found = position;
          break;
        }
      }
      if (found < 0) {
        return -1;
      }
      previous = found;
    }
    return previous;
  }

  /**
   * Evaluates a join: scoring operands, filter gates, calculators over the admitted
   * set, exclusions, and the declared fusion.
   *
   * @param join Join clause.
   * @param context Execution state.
   * @return Joined membership and scores.
   */
  private NodeResult evaluateJoin(JoinClause join, Context context) {
    final List<QueryNode> filters = new ArrayList<>();
    final List<CelScore> calculators = new ArrayList<>();
    final List<NodeResult> scored = new ArrayList<>();
    for (QueryNode operand : join.getOperandsList()) {
      switch (operand.getKindCase()) {
        case CEL_FILTER -> filters.add(operand);
        case CEL_CALCULATOR -> calculators.add(operand.getCelCalculator().getScore());
        default -> scored.add(evaluate(operand, context));
      }
    }
    final boolean and = join.getOperator() == JoinOperator.JOIN_OPERATOR_AND;
    final Set<String> membership = new LinkedHashSet<>();
    if (and) {
      membership.addAll(scored.getFirst().scores().keySet());
      for (int index = 1; index < scored.size(); index++) {
        membership.retainAll(scored.get(index).scores().keySet());
      }
    } else {
      for (NodeResult result : scored) {
        membership.addAll(result.scores().keySet());
      }
    }
    for (QueryNode filter : filters) {
      final CelQueryEvaluator.CompiledFilter predicate =
          compileFilter(filter.getCelFilter().getExpression());
      membership.removeIf(chunkId -> !testFilter(predicate, context.candidate(chunkId)));
    }
    for (QueryNode exclusion : join.getExclusionsList()) {
      membership.removeAll(excludedChunkIds(exclusion, membership, context));
    }
    final List<NodeResult> scoring = new ArrayList<>(scored);
    for (CelScore calculator : calculators) {
      scoring.add(evaluateCalculator(calculator, membership, context));
    }
    final NodeResult result = NodeResult.empty();
    if (join.getFusion() == JoinFusion.JOIN_FUSION_RECIPROCAL_RANK) {
      fuseReciprocalRank(scoring, membership, result.scores());
    } else {
      for (String chunkId : membership) {
        double sum = 0;
        double max = 0;
        int contributions = 0;
        for (NodeResult operand : scoring) {
          final Double score = operand.scores().get(chunkId);
          if (score != null) {
            sum += score;
            max = Math.max(max, score);
            contributions++;
          }
        }
        result.scores().put(chunkId, and && contributions > 0 ? sum / scoring.size() : max);
      }
    }
    for (NodeResult operand : scored) {
      operand.spans().forEach((chunkId, spans) -> {
        if (result.scores().containsKey(chunkId)) {
          result.spans().computeIfAbsent(chunkId, key -> new ArrayList<>()).addAll(spans);
        }
      });
    }
    return result;
  }

  /**
   * Resolves the chunk ids one exclusion removes from a membership set.
   *
   * @param exclusion Exclusion node: term, phrase, join, or filter.
   * @param membership Current join membership.
   * @param context Execution state.
   * @return Chunk ids to remove.
   */
  private Set<String> excludedChunkIds(
      QueryNode exclusion, Set<String> membership, Context context) {
    if (exclusion.getKindCase() == QueryNode.KindCase.CEL_FILTER) {
      final CelQueryEvaluator.CompiledFilter predicate =
          compileFilter(exclusion.getCelFilter().getExpression());
      final Set<String> excluded = new LinkedHashSet<>();
      for (String chunkId : membership) {
        if (testFilter(predicate, context.candidate(chunkId))) {
          excluded.add(chunkId);
        }
      }
      return excluded;
    }
    return evaluate(exclusion, context).scores().keySet();
  }

  /**
   * Scores one calculator component over an admitted candidate set.
   *
   * @param calculator Calculator declaration.
   * @param membership Sibling-admitted chunk ids.
   * @param context Execution state.
   * @return Normalized scores for every admitted candidate.
   */
  private NodeResult evaluateCalculator(
      CelScore calculator, Set<String> membership, Context context) {
    final CelQueryEvaluator.CompiledCalculator compiled =
        compileCalculator(calculator.getExpression());
    final Map<String, Double> raw = new LinkedHashMap<>();
    for (String chunkId : membership) {
      raw.put(chunkId, calculate(compiled, context.candidate(chunkId)));
    }
    final NodeResult result = NodeResult.empty();
    normalize(calculator.getNormalization(), raw, result.scores());
    return result;
  }

  /**
   * Applies a boost factor to its operand's scores without changing membership.
   *
   * @param boost Boost clause.
   * @param context Execution state.
   * @return Boosted scores clamped to [0, 1].
   */
  private NodeResult evaluateBoost(BoostClause boost, Context context) {
    final NodeResult operand = evaluate(boost.getOperand(), context);
    final NodeResult result = new NodeResult(new LinkedHashMap<>(), operand.spans());
    if (boost.getFactorCase() == BoostClause.FactorCase.WEIGHT) {
      final double weight = boost.getWeight();
      operand.scores().forEach((chunkId, score) ->
          result.scores().put(chunkId, Math.min(1, Math.max(0, score * weight))));
      return result;
    }
    final Map<String, Double> raw = new LinkedHashMap<>();
    final CelQueryEvaluator.CompiledCalculator compiled =
        compileCalculator(boost.getCalculator().getExpression());
    for (String chunkId : operand.scores().keySet()) {
      raw.put(chunkId, calculate(compiled, context.candidate(chunkId)));
    }
    final Map<String, Double> factors = new LinkedHashMap<>();
    normalize(boost.getCalculator().getNormalization(), raw, factors);
    operand.scores().forEach((chunkId, score) ->
        result.scores().put(chunkId,
            Math.min(1, Math.max(0, score * factors.get(chunkId)))));
    return result;
  }

  /**
   * Ranks by summed reciprocal rank across operands, then normalizes by the query's top
   * value so the joined node still yields scores in [0, 1].
   *
   * @param scoring Scoring operand results.
   * @param membership Joined membership.
   * @param scores Destination score map.
   */
  private static void fuseReciprocalRank(
      List<NodeResult> scoring, Set<String> membership, Map<String, Double> scores) {
    final Map<String, Double> summed = new LinkedHashMap<>();
    for (String chunkId : membership) {
      summed.put(chunkId, 0d);
    }
    for (NodeResult operand : scoring) {
      final List<String> ranked = operand.scores().keySet().stream()
          .filter(membership::contains)
          .sorted(Comparator
              .comparingDouble((String chunkId) -> operand.scores().get(chunkId)).reversed()
              .thenComparing(chunkId -> chunkId))
          .toList();
      for (int rank = 0; rank < ranked.size(); rank++) {
        summed.merge(ranked.get(rank), 1d / (RRF_OFFSET + rank + 1), Double::sum);
      }
    }
    normalizeByMax(summed, scores);
  }

  /**
   * Divides raw component scores by the query's top score within the component.
   *
   * @param raw Raw scores per chunk id.
   * @param scores Destination map of scores in [0, 1].
   */
  private static void normalizeByMax(Map<String, Double> raw, Map<String, Double> scores) {
    double max = 0;
    for (double value : raw.values()) {
      max = Math.max(max, value);
    }
    for (Map.Entry<String, Double> entry : raw.entrySet()) {
      scores.put(entry.getKey(), max > 0 ? entry.getValue() / max : 0);
    }
  }

  /**
   * Maps raw calculator values into [0, 1] under a declared normalization.
   *
   * @param normalization Declared normalization.
   * @param raw Raw values per chunk id.
   * @param scores Destination score map.
   */
  private static void normalize(
      ScoreNormalization normalization, Map<String, Double> raw, Map<String, Double> scores) {
    switch (normalization) {
      case SCORE_NORMALIZATION_CLAMP -> raw.forEach((chunkId, value) ->
          scores.put(chunkId, Math.min(1, Math.max(0, value))));
      case SCORE_NORMALIZATION_LOGISTIC -> raw.forEach((chunkId, value) ->
          scores.put(chunkId, 1 / (1 + Math.exp(-value))));
      case SCORE_NORMALIZATION_MINMAX -> {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : raw.values()) {
          min = Math.min(min, value);
          max = Math.max(max, value);
        }
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
          scores.put(entry.getKey(),
              max > min ? (entry.getValue() - min) / (max - min) : 0);
        }
      }
      case SCORE_NORMALIZATION_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException(
          "Validation admits only CLAMP, MINMAX, and LOGISTIC");
    }
  }

  /**
   * Compiles a filter expression, mapping type-check failures to the client.
   *
   * @param expression CEL expression source.
   * @return The compiled predicate.
   */
  private CelQueryEvaluator.CompiledFilter compileFilter(String expression) {
    try {
      return celEvaluator.compileFilter(expression);
    } catch (IllegalArgumentException e) {
      throw AnalysisException.invalidArgument(
          "cel_filter expression does not type-check to bool: " + e.getMessage());
    }
  }

  /**
   * Compiles a calculator expression, mapping type-check failures to the client.
   *
   * @param expression CEL expression source.
   * @return The compiled calculator.
   */
  private CelQueryEvaluator.CompiledCalculator compileCalculator(String expression) {
    try {
      return celEvaluator.compileCalculator(expression);
    } catch (IllegalArgumentException e) {
      throw AnalysisException.invalidArgument(
          "CEL calculator expression does not type-check to a number: " + e.getMessage());
    }
  }

  /**
   * Evaluates a compiled filter against one candidate's metadata, failing loud on
   * evaluation errors so unguarded expressions surface instead of silently dropping
   * candidates.
   *
   * @param predicate Compiled predicate.
   * @param candidate Candidate under test.
   * @return The predicate result.
   */
  private static boolean testFilter(
      CelQueryEvaluator.CompiledFilter predicate, QueryCandidate candidate) {
    try {
      return predicate.test(candidate.record().sourceDocument().getMetadata());
    } catch (IllegalArgumentException e) {
      throw AnalysisException.invalidArgument("cel_filter evaluation failed for chunk '"
          + candidate.record().chunkId() + "': " + e.getMessage());
    }
  }

  /**
   * Evaluates a compiled calculator against one candidate's metadata.
   *
   * @param calculator Compiled calculator.
   * @param candidate Candidate under evaluation.
   * @return The finite raw value.
   */
  private static double calculate(
      CelQueryEvaluator.CompiledCalculator calculator, QueryCandidate candidate) {
    final double value;
    try {
      value = calculator.calculate(candidate.record().sourceDocument().getMetadata());
    } catch (IllegalArgumentException e) {
      throw AnalysisException.invalidArgument("CEL calculator evaluation failed for chunk '"
          + candidate.record().chunkId() + "': " + e.getMessage());
    }
    if (!Double.isFinite(value)) {
      throw AnalysisException.invalidArgument("CEL calculator returned a non-finite value "
          + "for chunk '" + candidate.record().chunkId() + "'");
    }
    return value;
  }

  /**
   * Builds one matched span.
   *
   * @param start Inclusive start offset in UTF-16 code units of the indexed text.
   * @param end Exclusive end offset in UTF-16 code units of the indexed text.
   * @param term Matched analyzed term or phrase.
   * @return The wire span.
   */
  private static MatchedSpan span(int start, int end, String term) {
    return MatchedSpan.newBuilder().setStart(start).setEnd(end).setTerm(term).build();
  }

  /**
   * Computes cosine similarity for two nonzero vectors of one dimension.
   *
   * @param left Query vector.
   * @param right Candidate vector.
   * @return Cosine similarity.
   */
  private static double cosine(float[] left, float[] right) {
    double dot = 0;
    double leftNorm = 0;
    double rightNorm = 0;
    for (int index = 0; index < left.length; index++) {
      dot += (double) left[index] * right[index];
      leftNorm += (double) left[index] * left[index];
      rightNorm += (double) right[index] * right[index];
    }
    if (leftNorm == 0 || rightNorm == 0) {
      throw AnalysisException.failedPrecondition("Semantic similarity requires nonzero vectors");
    }
    return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
  }
}
