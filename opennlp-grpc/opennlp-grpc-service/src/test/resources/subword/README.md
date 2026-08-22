<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# SentencePiece test model provenance

`tiny-unigram-bytefb.model` is a project-generated test fixture, not a
third-party pretrained model. It is a byte-for-byte copy of the fixture added
to Apache OpenNLP by commit `6a71703c78d76ca32dff68649dec13c33e1b2330` at:

```text
opennlp-extensions/opennlp-subword/src/test/resources/
opennlp/subword/sentencepiece/tiny-unigram-bytefb.model
```

Both copies have this SHA-256 digest:

```text
53e87baa187177bdfdd50c0183e69160454cb10e7ff439320f248daf3f807a08
```

The OpenNLP fixture was trained with the Apache-2.0-licensed SentencePiece
Python package, version 0.2.1. Its `gen_fixtures.py` script trains the model
from the adjacent project-authored `corpus.txt` and its in-script multilingual
sentences. The corpus states explicitly that none of its text is quoted from
external work.

To inspect or regenerate an equivalent fixture, use the `corpus.txt` and
`gen_fixtures.py` files beside the original OpenNLP model:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install sentencepiece==0.2.1
python gen_fixtures.py corpus.txt output-directory
```

SentencePiece training is not guaranteed to reproduce identical serialized
bytes. Validate the model's behavior with the OpenNLP SentencePiece parity
tests and update the digest above whenever the checked-in binary changes.
