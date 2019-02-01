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

package org.apache.opennlp.normalizer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.opennlp.ModelUtil;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

public class Normalizer {

  private static final char END_MARKER = 3;

  private final Session session;
  private final Map<Character, Integer> sourceCharMap;
  private final Map<Integer, Character> targetCharMap;

  public Normalizer(InputStream modelZipPackage) throws IOException {

    Path tmpModelPath = ModelUtil.writeModelToTmpDir(modelZipPackage);
    try(InputStream sourceCharMapIn = new FileInputStream(
        tmpModelPath.resolve("source_char_dict.txt").toFile())) {
      sourceCharMap = loadCharMap(sourceCharMapIn).entrySet()
          .stream()
          .collect(Collectors.toMap(Map.Entry::getValue, c -> c.getKey()));
    }

    try(InputStream targetCharMapIn = new FileInputStream(
        tmpModelPath.resolve("target_char_dict.txt").toFile())) {
      targetCharMap = loadCharMap(targetCharMapIn);
    }

    SavedModelBundle model = SavedModelBundle.load(tmpModelPath.toString(), "serve");
    session = model.session();
  }

  private static Map<Integer, Character> loadCharMap(InputStream in) throws IOException {
    try(BufferedReader reader = new BufferedReader(
        new InputStreamReader(in, StandardCharsets.UTF_8))) {
      Map<Integer, Character> characterMap = new HashMap<>();

      String tag;
      while ((tag = reader.readLine()) != null) {
        characterMap.put(characterMap.size(), tag.charAt(0));
      }

      return Collections.unmodifiableMap(characterMap);
    }
  }

  public String[] normalize(String[] texts) {

    if (texts.length == 0) {
      return new String[0];
    }

    int textLengths[] = Arrays.stream(texts).mapToInt(String::length).toArray();
    int maxLength = Arrays.stream(textLengths).max().getAsInt();

    int charIds[][] = new int[texts.length][maxLength];

    for (int textIndex = 0; textIndex < texts.length; textIndex++) {
      for (int charIndex = 0; charIndex < texts[textIndex].length(); charIndex++) {
        charIds[textIndex][charIndex] =
                sourceCharMap.getOrDefault(texts[textIndex].charAt(charIndex), 0);
      }

      textLengths[textIndex] = texts[textIndex].length();
    }

    try (Tensor<?> charTensor = Tensor.create(charIds);
         Tensor<?> textLength = Tensor.create(textLengths);
         Tensor<?> batchSize = Tensor.create(texts.length)) {

      List<Tensor<?>> result = session.runner()
          .feed("encoder_char_ids", charTensor)
          .feed("encoder_lengths", textLength)
          .feed("batch_size", batchSize)
          .fetch("decode", 0).run();

      try (Tensor<?> translationTensor = result.get(0)) {
        int[][] translations =
            translationTensor.copyTo(new int[texts.length][(int) translationTensor.shape()[1]]);

        List<String> normalizedTexts = new ArrayList<>();

        for (int ti = 0; ti < translations.length; ti++) {
          StringBuilder normalizedText = new StringBuilder();
          for (int ci = 0; ci < translations[ti].length; ci++) {
            normalizedText.append(targetCharMap.get(translations[ti][ci]));
          }

          // Remove the end marker from the translated string
          for (int ci = normalizedText.length() - 1; ci >= 0; ci--) {
            if (END_MARKER == normalizedText.charAt(ci)) {
              normalizedText.setLength(ci);
            }
          }

          normalizedTexts.add(normalizedText.toString());
        }

        return normalizedTexts.toArray(new String[normalizedTexts.size()]);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    Normalizer normalizer = new Normalizer(new FileInputStream(
            "/home/blue/dev/opennlp-sandbox/tf-ner-poc/src/main/python/normalizer/normalizer.zip"));

    String[] result = normalizer.normalize(new String[] {
        "18 Mars 2012"
    });

    System.out.println(result[0]);
  }
}
