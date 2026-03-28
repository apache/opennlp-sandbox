# Protocol Documentation
<a name="top"></a>

## Table of Contents

- [opennlp.proto](#opennlp-proto)
    - [AvailableModels](#opennlp-AvailableModels)
    - [Empty](#opennlp-Empty)
    - [Model](#opennlp-Model)
    - [SentDetectPosRequest](#opennlp-SentDetectPosRequest)
    - [SentDetectRequest](#opennlp-SentDetectRequest)
    - [Span](#opennlp-Span)
    - [SpanList](#opennlp-SpanList)
    - [StringList](#opennlp-StringList)
    - [TagRequest](#opennlp-TagRequest)
    - [TagWithContextRequest](#opennlp-TagWithContextRequest)
    - [TokenizePosRequest](#opennlp-TokenizePosRequest)
    - [TokenizeRequest](#opennlp-TokenizeRequest)
  
    - [POSTagFormat](#opennlp-POSTagFormat)
  
    - [PosTaggerService](#opennlp-PosTaggerService)
    - [SentenceDetectorService](#opennlp-SentenceDetectorService)
    - [TokenizerTaggerService](#opennlp-TokenizerTaggerService)
  
- [Scalar Value Types](#scalar-value-types)



<a name="opennlp-proto"></a>
<p align="right"><a href="#top">Top</a></p>

## opennlp.proto
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
&#34;License&#34;); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
&#34;AS IS&#34; BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.


<a name="opennlp-AvailableModels"></a>

### AvailableModels



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| models | [Model](#opennlp-Model) | repeated |  |






<a name="opennlp-Empty"></a>

### Empty







<a name="opennlp-Model"></a>

### Model



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| hash | [string](#string) |  |  |
| name | [string](#string) |  |  |
| locale | [string](#string) |  |  |






<a name="opennlp-SentDetectPosRequest"></a>

### SentDetectPosRequest



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| sentence | [string](#string) |  |  |
| model_hash | [string](#string) |  |  |






<a name="opennlp-SentDetectRequest"></a>

### SentDetectRequest



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| sentence | [string](#string) |  |  |
| model_hash | [string](#string) |  |  |






<a name="opennlp-Span"></a>

### Span



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| start | [int32](#int32) |  |  |
| end | [int32](#int32) |  |  |
| prob | [double](#double) |  |  |
| type | [string](#string) |  |  |






<a name="opennlp-SpanList"></a>

### SpanList



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| values | [Span](#opennlp-Span) | repeated |  |






<a name="opennlp-StringList"></a>

### StringList



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| values | [string](#string) | repeated |  |






<a name="opennlp-TagRequest"></a>

### TagRequest



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| sentence | [string](#string) | repeated |  |
| format | [POSTagFormat](#opennlp-POSTagFormat) |  |  |
| model_hash | [string](#string) |  |  |






<a name="opennlp-TagWithContextRequest"></a>

### TagWithContextRequest



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| sentence | [string](#string) | repeated |  |
| additional_context | [string](#string) | repeated |  |
| format | [POSTagFormat](#opennlp-POSTagFormat) |  |  |
| model_hash | [string](#string) |  |  |






<a name="opennlp-TokenizePosRequest"></a>

### TokenizePosRequest



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| sentence | [string](#string) |  |  |
| model_hash | [string](#string) |  |  |






<a name="opennlp-TokenizeRequest"></a>

### TokenizeRequest



| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| sentence | [string](#string) |  |  |
| model_hash | [string](#string) |  |  |





 


<a name="opennlp-POSTagFormat"></a>

### POSTagFormat


| Name | Number | Description |
| ---- | ------ | ----------- |
| UD | 0 | Universal Dependencies format (current opennlp-models) |
| PENN | 1 | Penn Treebank format (deprecated). Use UD instead. Converting from UD to PENN isn&#39;t a lossless operation. |
| UNKNOWN | 2 | Unknown tag format |
| CUSTOM | 3 | Custom-defined tag format |


 

 


<a name="opennlp-PosTaggerService"></a>

### PosTaggerService


| Method Name | Request Type | Response Type | Description |
| ----------- | ------------ | ------------- | ------------|
| Tag | [TagRequest](#opennlp-TagRequest) | [StringList](#opennlp-StringList) | Assigns the sentence of tokens POS tags. |
| TagWithContext | [TagWithContextRequest](#opennlp-TagWithContextRequest) | [StringList](#opennlp-StringList) | Assigns the sentence of tokens POS tags with additional (string-based) context. |
| GetAvailableModels | [Empty](#opennlp-Empty) | [AvailableModels](#opennlp-AvailableModels) | Returns the available models which can be used for POS tagging. |


<a name="opennlp-SentenceDetectorService"></a>

### SentenceDetectorService


| Method Name | Request Type | Response Type | Description |
| ----------- | ------------ | ------------- | ------------|
| sentDetect | [SentDetectRequest](#opennlp-SentDetectRequest) | [StringList](#opennlp-StringList) | Detects sentences in a character sequence. |
| sentPosDetect | [SentDetectPosRequest](#opennlp-SentDetectPosRequest) | [SpanList](#opennlp-SpanList) | Detects sentences in a character sequence. |
| GetAvailableModels | [Empty](#opennlp-Empty) | [AvailableModels](#opennlp-AvailableModels) | Returns the available models which can be used for sentence detection. |


<a name="opennlp-TokenizerTaggerService"></a>

### TokenizerTaggerService


| Method Name | Request Type | Response Type | Description |
| ----------- | ------------ | ------------- | ------------|
| Tokenize | [TokenizeRequest](#opennlp-TokenizeRequest) | [StringList](#opennlp-StringList) | Splits a sentence into its atomic parts. |
| TokenizePos | [TokenizePosRequest](#opennlp-TokenizePosRequest) | [SpanList](#opennlp-SpanList) | Finds the boundaries of atomic parts in a string. |
| GetAvailableModels | [Empty](#opennlp-Empty) | [AvailableModels](#opennlp-AvailableModels) | Returns the available models which can be used for tokenization tagging. |

 



## Scalar Value Types

| .proto Type | Notes | C++ | Java | Python | Go | C# | PHP | Ruby |
| ----------- | ----- | --- | ---- | ------ | -- | -- | --- | ---- |
| <a name="double" /> double |  | double | double | float | float64 | double | float | Float |
| <a name="float" /> float |  | float | float | float | float32 | float | float | Float |
| <a name="int32" /> int32 | Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to have negative values, use sint32 instead. | int32 | int | int | int32 | int | integer | Bignum or Fixnum (as required) |
| <a name="int64" /> int64 | Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to have negative values, use sint64 instead. | int64 | long | int/long | int64 | long | integer/string | Bignum |
| <a name="uint32" /> uint32 | Uses variable-length encoding. | uint32 | int | int/long | uint32 | uint | integer | Bignum or Fixnum (as required) |
| <a name="uint64" /> uint64 | Uses variable-length encoding. | uint64 | long | int/long | uint64 | ulong | integer/string | Bignum or Fixnum (as required) |
| <a name="sint32" /> sint32 | Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than regular int32s. | int32 | int | int | int32 | int | integer | Bignum or Fixnum (as required) |
| <a name="sint64" /> sint64 | Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than regular int64s. | int64 | long | int/long | int64 | long | integer/string | Bignum |
| <a name="fixed32" /> fixed32 | Always four bytes. More efficient than uint32 if values are often greater than 2^28. | uint32 | int | int | uint32 | uint | integer | Bignum or Fixnum (as required) |
| <a name="fixed64" /> fixed64 | Always eight bytes. More efficient than uint64 if values are often greater than 2^56. | uint64 | long | int/long | uint64 | ulong | integer/string | Bignum |
| <a name="sfixed32" /> sfixed32 | Always four bytes. | int32 | int | int | int32 | int | integer | Bignum or Fixnum (as required) |
| <a name="sfixed64" /> sfixed64 | Always eight bytes. | int64 | long | int/long | int64 | long | integer/string | Bignum |
| <a name="bool" /> bool |  | bool | boolean | boolean | bool | bool | boolean | TrueClass/FalseClass |
| <a name="string" /> string | A string must always contain UTF-8 encoded or 7-bit ASCII text. | string | String | str/unicode | string | string | string | String (UTF-8) |
| <a name="bytes" /> bytes | May contain any arbitrary sequence of bytes. | string | ByteString | str | []byte | ByteString | string | String (ASCII-8BIT) |

