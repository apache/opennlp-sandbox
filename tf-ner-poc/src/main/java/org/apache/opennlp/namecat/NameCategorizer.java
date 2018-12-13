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

package org.apache.opennlp.namecat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.ModelUtil;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

public class NameCategorizer {

  private final Session session;
  private final Map<Character, Integer> charMap = new HashMap<>();
  private final Map<Integer, String> labelMap;

  public NameCategorizer(InputStream modelZipPackage) throws IOException {

    Path tmpModelPath = ModelUtil.writeModelToTmpDir(modelZipPackage);

    try (BufferedReader in = Files.newBufferedReader(
            tmpModelPath.resolve("char_dict.txt"), StandardCharsets.UTF_8)) {
      in.lines().forEach(ch -> charMap.put(ch.charAt(0), charMap.size()));
    }

    labelMap = new HashMap<>();
    try (BufferedReader in = Files.newBufferedReader(
            tmpModelPath.resolve("label_dict.txt"), StandardCharsets.UTF_8)) {
      in.lines().forEach(label -> labelMap.put(labelMap.size(), label));
    }

    SavedModelBundle model = SavedModelBundle.load(tmpModelPath.toString(), "serve");
    session = model.session();
  }

  private static int argmax(float[] x) {
    if (x == null || x.length == 0) {
      throw new IllegalArgumentException("Vector x is null or empty");
    }

    int maxIdx = 0;
    for (int i = 1; i < x.length; i++) {
      if (x[maxIdx] < x[i])
        maxIdx = i;
    }
    return maxIdx;
  }

  public String[] categorize(String[] names) {
    if (names.length == 0) {
      return new String[0];
    }

    int maxLength = Arrays.stream(names).mapToInt(String::length).max().getAsInt();

    int charIds[][] = new int[names.length][maxLength];
    int nameLengths[] = new int[names.length];

    for (int nameIndex = 0; nameIndex < names.length; nameIndex++) {
      for (int charIndex = 0; charIndex < names[nameIndex].length(); charIndex++) {
        charIds[nameIndex][charIndex] = charMap.get(names[nameIndex].charAt(charIndex));
      }
      nameLengths[nameIndex] = names[nameIndex].length();
    }

    try (Tensor<?> dropout = Tensor.create(1f, Float.class);
         Tensor<?> charTensor = Tensor.create(charIds);
         Tensor<?> nameLength = Tensor.create(nameLengths)) {
      List<Tensor<?>> result = session.runner()
          .feed("dropout_keep_prop", dropout)
          .feed("char_ids", charTensor)
          .feed("name_lengths", nameLength)
          .fetch("norm_probs", 0).run();

      try (Tensor<?> probTensor = result.get(0)) {
        float[][] probs = probTensor.copyTo(new float[names.length][labelMap.size()]);
        return Arrays.stream(probs).map(prob -> labelMap.get(argmax(prob))).toArray(String[]::new);
      }
    }
  }
}
