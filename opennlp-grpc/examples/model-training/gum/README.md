<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to you under the Apache License, Version 2.0.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an AS IS BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Training the GUM parser and chunker demo models

This example trains a new English constituency parser with the current OpenNLP Java API and
extracts its syntactic chunker. It does not use Python, cTAKES, or an older published OpenNLP
model.

The build uses GUM commit `22fdf87f9c71c96bcc771461d06e689b1f90020d`. Only the academic and
court genres are selected. GUM declares both source-text groups and all annotations as CC BY
4.0. The script excludes every genre with a different license.

The official GUM train, development, and test document assignments are preserved. The selected
subset contains 1,199 training trees, 149 development trees, and 162 test trees. The catalog
artifacts were trained with OpenNLP helper commit
`8fd8501ec47647b1aa756d5279012f4737106d06` and the standard 100-iteration, cutoff-5 maximum
entropy settings.

Run the trainer from this directory and point it at a current OpenNLP source checkout:

```bash
OPENNLP_SOURCE=/path/to/opennlp ./train.sh /path/to/output
```

The output directory receives:

* `en-gum-cc-by-4-parser.bin`
* `en-gum-cc-by-4-chunker.bin`
* `TRAINING-PROVENANCE.txt`
* `SHA256SUMS`
* `LICENSE-GUM.md`

The catalog v1 artifacts measured constituent F1 0.6750 and phrase F1 0.9051 on the selected
held-out test documents. These are compact demonstration models for exercising the complete
service. They are not presented as general English state-of-the-art models.
