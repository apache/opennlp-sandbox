# Apache OpenNLP published models

Sources fetched 2026-08-28:

- https://opennlp.apache.org/models.html
- https://opennlp.sourceforge.net/models-1.5/
- https://opennlp.apache.org/docs/2.5.5/manual/opennlp.html (the manual does not describe
  the manifest, so the manifest keys below were read from the OpenNLP source instead)
- OpenNLP source `opennlp-core/opennlp-runtime/src/main/java/opennlp/tools/util/model/BaseModel.java`
  and the per-component model classes
- https://search.maven.org/solrsearch/select?q=g:%22org.apache.opennlp%22 (Maven Central
  search API)

## 1. What models.html lists

Two generations, plus a standalone language detector.

Language detector: `langdetect-183.bin`, "Detects 103 languages in ISO 693-3 standard.
Works well with longer texts that have at least 2 sentences or more from the same
language." Compatible with OpenNLP versions >= 1.8.3. Download base:

```
https://www.apache.org/dyn/closer.cgi/opennlp/models/langdetect/1.8.3/langdetect-183.bin
```

UD models (current generation), 36 languages covering sentence detection, tokenization,
lemmatization and part of speech tagging. Table columns are ISO code, language, the
OpenNLP version the model was trained with (2.5.4 for the current set), the Universal
Dependencies version (2.16), the model file name, and the signature files.

File name pattern:

```
opennlp-[iso]-ud-[treebank]-[task]-[models-version]-[opennlp-version].bin

opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin
opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin
opennlp-en-ud-ewt-lemmas-1.3-2.5.4.bin
opennlp-en-ud-ewt-pos-1.3-2.5.4.bin
```

Task segment values seen: `sentence`, `tokens`, `lemmas`, `pos`. Download base:

```
https://www.apache.org/dyn/closer.cgi/opennlp/models/ud-models-1.3/<filename>
```

Legacy 1.5 models: "The models on Sourceforge for 1.5.0 are found here. They are fully
compatible with Apache OpenNLP 2.5.11." Hosted at https://opennlp.sourceforge.net/models-1.5/
with columns Language, Component, Description, Download and a flat
`[iso]-[component].bin` naming, for example:

```
en-sent.bin, en-token.bin, en-pos-maxent.bin, en-pos-perceptron.bin, en-chunker.bin,
en-parser-chunking.bin, en-ner-person.bin, en-ner-location.bin, en-ner-organization.bin,
en-ner-date.bin, en-ner-money.bin, en-ner-percentage.bin, en-ner-time.bin,
de-token.bin, nl-ner-misc.bin, pt-pos-perceptron.bin, se-sent.bin, es-ner-misc.bin
```

So the naming moved from `[lang]-[component].bin` in models-1.5 to
`opennlp-[lang]-ud-[treebank]-[task]-[modelver]-[opennlpver].bin` in the 2.x UD set: the
newer form pins the training corpus and both version numbers into the file name itself.

## 2. Checksums and signatures

models.html states: "The sha512, sha1, md5, and asc files are signature files and can be
used to verify the integrity of the downloaded distribution package." and "It might be
necessary to import the KEYS file to verify the integrity of the asc files. That can
easily be done with: `gpg --import KEYS`". The UD tables link `sha512` and `asc` next to
each model file; `sha256` appears alongside for some artifacts. Each checksum is a
sidecar file next to the `.bin`, not an entry inside it.

## 3. Maven coordinates

groupId `org.apache.opennlp`. models.html says the models "are also bundled in JAR files
and distributed via Maven Central" under the headings Sentence-Detector, Tokenization,
Lemmatizer and POS Tagging, but does not print the coordinates. From Maven Central search,
the artifact families are:

```
opennlp-models-sentdetect-<iso>     e.g. opennlp-models-sentdetect-fr:1.3.0
opennlp-models-tokenizer-<iso>      e.g. opennlp-models-tokenizer-en:1.3.0
opennlp-models-lemmatizer-<iso>     e.g. opennlp-models-lemmatizer-es:1.3.0
opennlp-models-pos-<iso>            e.g. opennlp-models-pos-de:1.3.0
opennlp-models-langdetect           1.3.0
opennlp-models-training, opennlp-models-training-ud, opennlp-models-test
opennlp-models (aggregator)
```

The ISO segment enumerates the same 36 UD languages, for example the tokenizer family
carries `af bg ca cs da de el en es et eu fa fi fr ga hr hy id is it ka kk ko lv nl no pl
pt ro ru sk sl sr sv tr uk`. The models jar version line (1.3.0) is independent of the
opennlp-tools line (2.5.x, 3.0.0-M3 milestones). A model jar simply carries the `.bin` on
the classpath, so the coordinate is the index key and the file name inside carries the
provenance.

## 4. What a .bin OpenNLP model contains

A `.bin` is a zip. `BaseModel` writes one entry per artifact plus the manifest:

```java
protected static final String MANIFEST_ENTRY = "manifest.properties";
protected static final String FACTORY_NAME   = "factory";
```

manifest.properties keys, with the constants that produce them:

| Property | Constant | Written by |
| --- | --- | --- |
| `Manifest-Version` | MANIFEST_VERSION_PROPERTY | always, value `"1.0"` |
| `Language` | LANGUAGE_PROPERTY | always, the language code |
| `OpenNLP-Version` | VERSION_PROPERTY | always, `Version.currentVersion()` |
| `Timestamp` | TIMESTAMP_PROPERTY | always, `System.currentTimeMillis()` as a string |
| `Component-Name` | COMPONENT_NAME_PROPERTY | always, the component class name |
| `Training-Cutoff` | TRAINING_CUTOFF_PROPERTY | training code, public constant |
| `Training-Iterations` | TRAINING_ITERATIONS_PROPERTY | training code, public constant |
| `Training-Eventhash` | TRAINING_EVENTHASH_PROPERTY | training code, public constant |
| `serializer-class-<name>` | SERIALIZER_CLASS_NAME_PREFIX | per custom artifact serializer |
| `parser-type` | ParserModel.PARSER_TYPE | ParserModel only, value is a `ParserType` name |

Validation on load throws `InvalidFormatException` when `manifest.properties` is missing,
when `OpenNLP-Version`, `Component-Name` or `Language` is absent, or when `Component-Name`
does not match the component doing the loading. That makes `Component-Name` plus
`Language` the minimal identity pair readable from any model file without knowing its
type in advance, and `OpenNLP-Version` plus `Timestamp` the provenance pair.

Zip entry names per component (the payload beside the manifest):

```
sent.model                 SentenceModel
token.model                TokenizerModel
abbreviations.dictionary   TokenizerModel / SentenceModel
pos.model                  POSModel
tags.tagdict               POSModel
ngram.dictionary           POSModel
lemmatizer.model           LemmatizerModel
chunker.model              ChunkerModel
doccat.model               DoccatModel
sentiment.model            sentiment component
langdetect.model           LanguageDetectorModel
nameFinder.model           TokenNameFinderModel
generator.featuregen       TokenNameFinderModel, ChunkerModel
bpe.merges                 BPE tokenizer
build.model, check.model, attach.model, parsertager.postagger,
parserchunker.chunker, head-rules.headrules    ParserModel
factory                    the tool factory descriptor
```

The entry name is therefore a reliable secondary signal for the model kind, and a single
`.bin` can carry several artifacts (a parser model holds five plus its head rules).
