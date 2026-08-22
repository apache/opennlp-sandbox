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

import org.apache.opennlp.grpc.v1.AlignmentRun;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkGroupAnnotationList;
import org.apache.opennlp.grpc.v1.ChunkSource;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DocumentLayers;
import org.apache.opennlp.grpc.v1.DocumentWordType;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.EntityAnnotationList;
import org.apache.opennlp.grpc.v1.EntitySource;
import org.apache.opennlp.grpc.v1.LexicalExpansionAnnotation;
import org.apache.opennlp.grpc.v1.LexicalExpansionAnnotationList;
import org.apache.opennlp.grpc.v1.LexicalExpansionKind;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.NormalizationAnnotationList;
import org.apache.opennlp.grpc.v1.NormalizationResult;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ParseNode;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.StemAnnotation;
import org.apache.opennlp.grpc.v1.StemAnnotationList;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.SyntacticChunkAnnotationList;
import org.apache.opennlp.grpc.v1.TreeAnnotation;
import org.apache.opennlp.grpc.v1.TreeAnnotationList;
import org.apache.opennlp.grpc.v1.WordTypeAnnotation;
import org.apache.opennlp.grpc.v1.WordTypeAnnotationList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies offset conversion for the strongly typed document-layer value cases. */
class DocumentOffsetEncoderLayerTest {

  private static final String TEXT = "café John";

  @Test
  void remapsEveryTypedLayerSpanAndNestedProvenanceToUtf8() {
    final AnnotationSpan sourceSpan = span(5, 9);
    final ParseTree primary = tree(sourceSpan, "primary");
    final ParseTree alternative = tree(sourceSpan, "alternative");
    final EmbeddingResult embedding = EmbeddingResult.newBuilder()
        .setModelId("test-model")
        .addVector(1f)
        .setSourceSpan(sourceSpan)
        .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL)
        .build();
    final ChunkEmbeddingGroup group = ChunkEmbeddingGroup.newBuilder()
        .setGroupId("group")
        .addChunks(Chunk.newBuilder()
            .setAnnotationSpan(sourceSpan)
            .addEmbeddings(embedding))
        .addCentroids(embedding.toBuilder()
            .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_GROUP_CENTROID))
        .build();

    final DocumentLayers layers = DocumentLayers.newBuilder()
        .addLayers(positional("word-types").setWordTypeValues(
            WordTypeAnnotationList.newBuilder().addAnnotations(
                WordTypeAnnotation.newBuilder().setSpan(sourceSpan)
                    .setType(DocumentWordType.DOCUMENT_WORD_TYPE_ALPHANUMERIC))))
        .addLayers(positional("entities").setEntityValues(
            EntityAnnotationList.newBuilder().addAnnotations(
                NamedEntity.newBuilder().setAnnotationSpan(sourceSpan).setEntityType("person")
                    .addSources(EntitySource.newBuilder().setRecognizerId("r").setEngine("e")
                        .setAnnotationSpan(sourceSpan)))))
        .addLayers(positional("chunks").setSyntacticChunkValues(
            SyntacticChunkAnnotationList.newBuilder().addAnnotations(
                ChunkSpan.newBuilder().setAnnotationSpan(sourceSpan).setChunkTag("NP")
                    .addSources(ChunkSource.newBuilder().setChunkerId("c").setEngine("e")
                        .setAnnotationSpan(sourceSpan)))))
        .addLayers(positional("stems").setStemValues(
            StemAnnotationList.newBuilder().addAnnotations(
                StemAnnotation.newBuilder().setSpan(sourceSpan).setStem("john")
                    .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_PORTER))))
        .addLayers(positional("expansions").setLexicalExpansionValues(
            LexicalExpansionAnnotationList.newBuilder().addAnnotations(
                LexicalExpansionAnnotation.newBuilder().setSpan(sourceSpan).setTerm("person")
                    .setKind(LexicalExpansionKind.LEXICAL_EXPANSION_KIND_SYNONYM)
                    .setWeight(1d).setLexiconId("wordnet"))))
        .addLayers(positional("parses").setTreeValues(
            TreeAnnotationList.newBuilder().addAnnotations(
                TreeAnnotation.newBuilder().setSpan(sourceSpan).setTree(primary)
                    .addAlternatives(alternative))))
        .addLayers(document("normalization").setNormalizationValues(
            NormalizationAnnotationList.newBuilder().addAnnotations(
                NormalizationResult.newBuilder().setNormalizedText("cafe John")
                    .addAlignment(AlignmentRun.newBuilder()
                        .setOriginalUnits(9).setNormalizedUnits(9)))))
        .addLayers(document("chunk-groups").setChunkGroupValues(
            ChunkGroupAnnotationList.newBuilder().addAnnotations(group)))
        .build();
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder()
        .setRawText(TEXT)
        .setLayers(layers);

    DocumentOffsetEncoder.apply(document, TEXT, OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);

    final DocumentLayers remapped = document.getLayers();
    assertSpan(remapped.getLayers(0).getWordTypeValues().getAnnotations(0).getSpan());
    final NamedEntity entity = remapped.getLayers(1).getEntityValues().getAnnotations(0);
    assertSpan(entity.getAnnotationSpan());
    assertSpan(entity.getSources(0).getAnnotationSpan());
    final ChunkSpan syntacticChunk =
        remapped.getLayers(2).getSyntacticChunkValues().getAnnotations(0);
    assertSpan(syntacticChunk.getAnnotationSpan());
    assertSpan(syntacticChunk.getSources(0).getAnnotationSpan());
    assertSpan(remapped.getLayers(3).getStemValues().getAnnotations(0).getSpan());
    assertSpan(remapped.getLayers(4).getLexicalExpansionValues().getAnnotations(0).getSpan());
    final TreeAnnotation parse = remapped.getLayers(5).getTreeValues().getAnnotations(0);
    assertSpan(parse.getSpan());
    assertSpan(parse.getTree().getRoot().getSpan());
    assertSpan(parse.getAlternatives(0).getRoot().getSpan());
    final NormalizationResult normalization =
        remapped.getLayers(6).getNormalizationValues().getAnnotations(0);
    assertEquals(10, normalization.getAlignment(0).getOriginalUnits());
    assertEquals(9, normalization.getAlignment(0).getNormalizedUnits());
    final ChunkEmbeddingGroup remappedGroup =
        remapped.getLayers(7).getChunkGroupValues().getAnnotations(0);
    assertSpan(remappedGroup.getChunks(0).getAnnotationSpan());
    assertSpan(remappedGroup.getChunks(0).getEmbeddings(0).getSourceSpan());
    assertSpan(remappedGroup.getCentroids(0).getSourceSpan());
  }

  private static AnnotationLayer.Builder positional(String id) {
    return AnnotationLayer.newBuilder().setId(id).setScope(LayerScope.LAYER_SCOPE_POSITIONAL);
  }

  private static AnnotationLayer.Builder document(String id) {
    return AnnotationLayer.newBuilder().setId(id).setScope(LayerScope.LAYER_SCOPE_DOCUMENT);
  }

  private static ParseTree tree(AnnotationSpan sourceSpan, String parserId) {
    return ParseTree.newBuilder()
        .setParserId(parserId)
        .setRoot(ParseNode.newBuilder().setLabel("NP").setSpan(sourceSpan))
        .build();
  }

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder()
        .setStart(start)
        .setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  private static void assertSpan(AnnotationSpan span) {
    assertEquals(6, span.getStart());
    assertEquals(10, span.getEnd());
  }
}
