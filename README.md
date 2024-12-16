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

Welcome to Apache OpenNLP!
===========

[![Build Status](https://github.com/apache/opennlp-sandbox/workflows/Java%20CI/badge.svg)](https://github.com/apache/opennlp-sandbox/actions)
[![Contributors](https://img.shields.io/github/contributors/apache/opennlp-sandbox)](https://github.com/apache/opennlp-sandbox/graphs/contributors)
[![GitHub pull requests](https://img.shields.io/github/issues-pr-raw/apache/opennlp-sandbox.svg)](https://github.com/apache/opennlp-sandbox/pulls)
[![Stack Overflow](https://img.shields.io/badge/stack%20overflow-opennlp-f1eefe.svg)](https://stackoverflow.com/questions/tagged/opennlp)

The Apache OpenNLP library is a machine learning based toolkit for the processing of natural language text.

This sandbox of the toolkit is written mostly in Java and provides support for special NLP tasks, such as 
word sense disambiguation, coreference resolution, text summarization, and more!
These tasks are usually required to build text processing services.

The goal of the OpenNLP sandbox is to provide extra components, potentially in an experimental stage.

OpenNLP sandbox code can be used both programmatically through its Java API, some components even from a terminal through its CLI.

## Useful Links

For additional information, visit the [OpenNLP Home Page](http://opennlp.apache.org/)

You can use OpenNLP with any language, demo models are provided [here](https://downloads.apache.org/opennlp/models/).
The models are fully compatible with the latest release, they can be used for testing or getting started.

> [!NOTE]  
> Please train your own models for all other use cases.

Documentation, including JavaDocs, code usage and command-line interface examples are available [here](http://opennlp.apache.org/docs/)

You can also follow our [mailing lists](http://opennlp.apache.org/mailing-lists.html) for news and updates.

## Overview

Currently, the library has different packages:

* `caseeditor-corpus-server-plugin`: A set of Java classes for [Apache UIMA](https://uima.apache.org) as Eclipse plugin to integrate corpora.
* `caseeditor-opennlp-plugin`: An OpenNLP plugin for [Apache UIMA](https://uima.apache.org).
* `corpus-server`: A multi-module component to create, search, remove, and serve multiple corpora.
* `mahout-addon`: An addon for [Apache Mahout](https://mahout.apache.org).
* `mallet-addon`: An addon for [Mallet](https://mimno.github.io/Mallet/topics.html) targeting topic modelling techniques.
* `opennlp-coref`: A component to conduct co-reference resolution.
* `modelbuilder-addon`: A set of classes to build models.
* `nlp-utils`: A set of OpenNLP util classes.
* `opennlp-dl`: An adapter component for [deeplearning4j](https://deeplearning4j.konduit.ai).
* `opennlp-grpc`: An implementation of a gRPC backend for OpenNLP.
* `opennlp-similarity`: A set of components that solve a number of text processing and search tasks, see further details in this [README.md](opennlp-similarity/README.md).
* `opennlp-wsd`: A set of components that allow for word sense disambiguation.
* `summarizer`: A set of classes providing text summarization.
* `tagging-server`: A RESTful webservice to allow for NER, POS tagging, sentence detection and tokenization.
* `tf-ner-poc`: An adapter component for [Tensorflow](https://www.tensorflow.org), in an early proof-of-concept (poc) stage.
* `wikinews-importer`: A set of classes to process and annotate text formatted in [MediaWiki markup](https://www.mediawiki.org/wiki/Help:Formatting).

## Getting Started

You can import the core toolkit directly from Maven, SBT or Gradle after you have build it locally:

#### Maven

```
<dependency>
    <groupId>org.apache.opennlp</groupId>
    <artifactId>opennlp-sandbox</artifactId>
    <version>${opennlp.version}</version>
</dependency>
```

#### SBT

```
libraryDependencies += "org.apache.opennlp" % "opennlp-sandbox" % "${opennlp.version}"
```

#### Gradle

```
compile group: "org.apache.opennlp", name: "opennlp-sandbox", version: "${opennlp.version}"
```

For more details please check our [documentation](http://opennlp.apache.org/docs/)

## Building OpenNLP

At least JDK 21 and Maven 3.3.9 are required to build the sandbox components.

After cloning the repository go into the destination directory and run:

```
mvn install
```

## Contributing

The Apache OpenNLP project is developed by volunteers and is always looking for new contributors to work on all parts of the project. 
Every contribution is welcome and needed to make it better. 
A contribution can be anything from a small documentation typo fix to a new component.

If you would like to get involved please follow the instructions [here](https://github.com/apache/opennlp/blob/main/.github/CONTRIBUTING.md)