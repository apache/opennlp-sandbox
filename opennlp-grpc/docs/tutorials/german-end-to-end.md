<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# German end to end: install a language pack, analyze, and search

This tutorial takes a stock English server to a working German pipeline with
German semantic search, without editing a configuration file. Everything
happens through the model catalog and the standard APIs; the browser
workbench can drive every step, and the equivalent API calls are shown so the
flow scripts cleanly. Start with the server and webapp running as in the
[quickstart](../../QUICKSTART.md).

## 1. Install the German language pack

On the **Models & data** tab, install the four German UD models: the sentence
detector, tokenizer, POS tagger, and lemmatizer with model id `de-ud-gsd`.
Each is downloaded from the Apache OpenNLP model distribution and
checksum-verified before it is published. From code, one `InstallModel` call
per entry (or the gateway):

```bash
for id in de-ud-gsd-sentence de-ud-gsd-tokens de-ud-gsd-pos de-ud-gsd-lemmas; do
  curl -s -X POST -H 'Content-Type: application/json' \
    http://127.0.0.1:7072/api/v1/install-model \
    -d "{\"catalogId\":\"$id\",\"revision\":\"ud-models-1.3-2.5.4\",
         \"licenseName\":\"Apache-2.0\",\"licenseAcknowledged\":true}"
done
```

Every install reports "restart required". Restart the server: on startup it
verifies the installed bytes against the catalog and logs
`Loaded classic pipeline for language 'de'`. The pack occupies the `de`
pipeline beside the bundled English default, so English requests are
unaffected, and installing French or Spanish beside it works the same way.

## 2. Install a multilingual embedding model

German semantic search needs an embedding model that understands German. The
catalog's `potion-multilingual-128m` entry is a ready-to-serve static table
covering 100+ languages; it serves immediately, no restart. Install it from
the same catalog panel or with the same `install-model` call.

## 3. Analyze German text

Paste German on the **Analyze** tab, leave the Language pipeline selector on
Automatic, and analyze. The language summary reports the detection and the
routing, and the output is genuinely German:

```text
detected: deu    Classic pipeline 'de' routed by detected language 'deu'
Die/die Katze/katze schlief/schlafen ...   (lemmas from the German UD model)
```

The pipeline can also be forced per request with
`AnalysisProfile.pipeline_language: "de"`, which does not require language
detection.

## 4. Index and search in German

Select `potion-multilingual-128m` as the embedding model, analyze each German
document with sentence chunks, and press **Add to server workspace**; then
query the workspace in German. From code this is the same analyze, index, and
search flow as the Python, Node.js, Java, and Go quickstarts. A three-document
contract corpus asked

> Wie kann ich meinen Mietvertrag beenden?

returns the termination and rescission sentences on top and the unrelated
sentence far below:

```text
1. +0.507  kauf:  Ein Rücktritt vom Kaufvertrag bleibt möglich.
2. +0.480  miete: Der Mieter kann den Vertrag mit einer Frist von drei Monaten kündigen.
3. +0.241  miete: Die Kündigung muss schriftlich erfolgen.
...        reise: Die Katze schlief den ganzen Nachmittag ... (near zero)
```

## 5. Where this leads

- Batch German documents stream through **Batch analyze** on the Analyze tab
  (`POST /api/v1/analyze-stream`), with per-document errors isolated.
- A German vocabulary can be learned on the **Trainer** tab and distilled
  against the catalog's multilingual teacher
  (`paraphrase-multilingual-minilm-l12-v2-teacher`) into your own German
  static model, which then serves for indexing and search like any other.
- The whole flow works identically for French and Spanish with their catalog
  packs, and any other language whose models you configure through
  `model.pipeline.<lang>.*`.
