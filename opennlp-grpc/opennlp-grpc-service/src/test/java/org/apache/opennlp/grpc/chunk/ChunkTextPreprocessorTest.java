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
package org.apache.opennlp.grpc.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkTextPreprocessorTest {

  @Test
  void cleanTextCollapsesWhitespace() {
    assertEquals("a b c", ChunkTextPreprocessor.clean("  a   b\tc  ", false));
  }

  @Test
  void preserveUrlsKeepsUrlIntactWhileCleaningSurroundingWhitespace() {
    assertEquals("see https://example.com now",
        ChunkTextPreprocessor.clean("see   https://example.com   now", true));
  }

  @Test
  void preserveUrlsMatchesWwwWithoutScheme() {
    assertEquals("visit www.example.com today",
        ChunkTextPreprocessor.clean("visit   www.example.com   today", true));
  }

  @Test
  void preserveUrlsMatchesSchemeCaseInsensitively() {
    assertEquals("GO HTTP://EXAMPLE.COM/PATH NOW",
        ChunkTextPreprocessor.clean("GO   HTTP://EXAMPLE.COM/PATH   NOW", true));
    assertEquals("go HttPs://Example.COM now",
        ChunkTextPreprocessor.clean("go   HttPs://Example.COM   now", true));
  }

  @Test
  void preserveUrlsKeepsPortsPathsAndTrailingPunctuationInTheUrl() {
    // The URL run is greedy over non-whitespace, so the comma stays glued to it.
    assertEquals("see https://example.com:8080/a/b?x=1, then",
        ChunkTextPreprocessor.clean("see   https://example.com:8080/a/b?x=1,   then", true));
  }

  @Test
  void preserveUrlsRejectsBarePrefixesWithoutAFollowingCharacter() {
    // A prefix alone (nothing or only whitespace after it) is not a URL.
    assertEquals("http:// www. end",
        ChunkTextPreprocessor.clean("http://  www.  end", true));
    assertEquals("www.", ChunkTextPreprocessor.clean("www.", true));
  }

  @Test
  void preserveUrlsMatchesPrefixesEmbeddedInAWord() {
    // Leftmost scanning finds the prefix anywhere, splitting the word. Pinned quirk:
    // the match runs to the end of the text, so trim() eats the placeholder's
    // trailing NUL and the restore misses, leaking the placeholder.
    assertEquals("ab \0URL1", ChunkTextPreprocessor.clean("abhttps://x.y", true));
  }

  @Test
  void preserveUrlsTreatsNonAsciiWhitespaceAsUrlCharacters() {
    // No-break space is not regex whitespace, so it stays inside the URL run.
    assertEquals("go http://x\u00A0y done",
        ChunkTextPreprocessor.clean("go   http://x\u00A0y   done", true));
  }

  @Test
  void preserveUrlsEndsUrlsAtAsciiWhitespace() {
    // Tab and vertical tab are regex whitespace and end the URL run. Pinned quirk:
    // with the match at the very start, trim() eats the placeholder's leading NUL
    // and the restore misses, leaking the placeholder.
    assertEquals("URL1\0 b", ChunkTextPreprocessor.clean("http://a\tb", true));
    assertEquals("URL1\0 b", ChunkTextPreprocessor.clean("http://a\u000Bb", true));
  }

  @Test
  void preserveUrlsKeepsMultipleUrls() {
    assertEquals("a http://x.y b www.z c",
        ChunkTextPreprocessor.clean("a   http://x.y   b   www.z   c", true));
  }

  @Test
  void preserveUrlsLeavesNonUrlsAlone() {
    assertEquals("ftp://example.com x",
        ChunkTextPreprocessor.clean("ftp://example.com  x", true));
    assertEquals("", ChunkTextPreprocessor.clean("", true));
  }
}
