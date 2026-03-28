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

package opennlp.tools.disambiguator;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;

/**
 * This class is a stream filter which reads a sentence by line samples from
 * a {@link FilterObjectStream} and converts them into {@link WSDSample} objects.
 *
 * @see WSDSample
 * @see FilterObjectStream
 */
public class WSDSampleStream extends FilterObjectStream<String, WSDSample> {

  private static final Logger LOG = LoggerFactory.getLogger(WSDSampleStream.class.getName());

  /**
   * Initializes a {@link WSDSampleStream instance}.
   *
   * @param sentences A plain text {@link ObjectStream line stream}.
   */
  public WSDSampleStream(ObjectStream<String> sentences) {
    super(sentences);
  }

  /**
   * Parses the next sentence and return the next {@link WSDSample} object.
   * <p>
   *
   * @implNote If an error occurred an empty {@link WSDSample} object is returned and a
   * warning message is logged. Usually it does not matter if one of many
   * sentences is ignored.
   *
   * @return A {@link WSDSample} or {@code null} if nothing could be read.
   */
  @Override
  public WSDSample read() throws IOException {

    String sentence = samples.read();

    if (sentence != null) {
      WSDSample sample;
      try {
        sample = WSDSample.parse(sentence);
      } catch (InvalidFormatException e) {
        LOG.warn("Problem during parsing, ignoring sentence: {}", sentence);
        sample = null; // new WSDSample(new String[]{}, new String[]{},0);
      }

      return sample;
    } else {
      // sentences stream is exhausted
      return null;
    }
  }
}
