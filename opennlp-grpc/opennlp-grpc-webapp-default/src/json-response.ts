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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

const MAX_INLINE_TEXT_LENGTH = 100_000;
const MAX_INLINE_ANNOTATIONS = 100_000;
/** Shown in place of the JSON when the response is past the inline threshold. */
export const LARGE_RESPONSE_MESSAGE = "This response is too large to format safely in the browser. "
  + "Download JSON writes it to a file without formatting it.";

/** Why Copy JSON refuses a response past the threshold. */
export const LARGE_COPY_MESSAGE = "This response is too large to copy through the clipboard; "
  + "use Download JSON instead.";

/**
 * Why Download .pb refuses a response past the threshold when it was opened from a file: the
 * browser would re-upload the whole JSON to the gateway for transcoding, the gateway caps
 * request bodies, and there is no request to re-run on the server.
 */
export const LARGE_PB_MESSAGE = "This response is too large to transcode through the browser, "
  + "and it was opened from a file, so there is no request the server could re-run for the .pb.";

/** Shown while the server re-runs an analysis whose reply is too large to transcode locally. */
export const SERVER_PB_MESSAGE = "The reply is too large to transcode in the browser, so the "
  + "server is re-running the analysis and streaming the .pb directly…";

export interface JsonPresentation {
  inline: boolean;
  text: string;
}

/** Chooses whether formatting a protobuf JSON response is safe for the interactive tab. */
export function jsonPresentation(
  response: unknown,
  textLength: number,
  annotationCount: number,
): JsonPresentation {
  if (textLength > MAX_INLINE_TEXT_LENGTH || annotationCount > MAX_INLINE_ANNOTATIONS) {
    return { inline: false, text: LARGE_RESPONSE_MESSAGE };
  }
  return { inline: true, text: JSON.stringify(response, null, 2) };
}
