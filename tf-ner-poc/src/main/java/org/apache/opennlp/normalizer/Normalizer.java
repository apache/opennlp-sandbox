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

  private final Session session;
  private final Map<Character, Integer> sourceCharMap;
  private final Map<Integer, Character> targetCharMap;

  Normalizer(InputStream sourceCharMapIn, InputStream targetCharMapIn,
             InputStream modelZipPackage) throws IOException {

    Path tmpModelPath = ModelUtil.writeModelToTmpDir(modelZipPackage);

    SavedModelBundle model = SavedModelBundle.load(tmpModelPath.toString(), "serve");
    session = model.session();

    sourceCharMap = loadCharMap(sourceCharMapIn).entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getValue, c -> c.getKey()));

    targetCharMap = loadCharMap(targetCharMapIn);
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

    // TODO: Batch size is hard coded in the graph, make it dynamic or at padding here

    int textLengths[] = Arrays.stream(texts).mapToInt(String::length).toArray();
    int maxLength = Arrays.stream(textLengths).max().getAsInt();

    int charIds[][] = new int[texts.length][maxLength];

    for (int textIndex = 0; textIndex < texts.length; textIndex++) {
      for (int charIndex = 0; charIndex < texts[textIndex].length(); charIndex++) {
        charIds[textIndex][charIndex] = sourceCharMap.get(texts[textIndex].charAt(charIndex));
      }

      textLengths[textIndex] = texts[textIndex].length();
    }

    try (Tensor<?> charTensor = Tensor.create(charIds);
         Tensor<?> textLength = Tensor.create(textLengths)) {

      List<Tensor<?>> result = session.runner()
          .feed("encoder_char_ids", charTensor)
          .feed("encoder_lengths", textLength)
          .fetch("decode", 0).run();

      try (Tensor<?> translationTensor = result.get(0)) {
        // TODO: This can't be hard coded ... normalized form doesn't need to have static length
        int[][] translations =
            translationTensor.copyTo(new int[texts.length][9]); // shape is (20, 9) in eval py code

        List<String> normalizedTexts = new ArrayList<>();

        for (int ti = 0; ti < translations.length; ti++) {
          StringBuilder normalizedText = new StringBuilder();
          for (int ci = 0; ci < translations[ti].length; ci++) {
            normalizedText.append(targetCharMap.get(translations[ti][ci]));
          }

          normalizedTexts.add(normalizedText.toString());
        }

        return normalizedTexts.toArray(new String[normalizedTexts.size()]);
      }
    }
  }
}
