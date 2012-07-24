//   Licensed to the Apache Software Foundation (ASF) under one or more
//   contributor license agreements.  See the NOTICE file distributed with
//   this work for additional information regarding copyright ownership.
//   The ASF licenses this file to You under the Apache License, Version 2.0
//   (the "License"); you may not use this file except in compliance with
//   the License.  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.


function Annotation(type, id, begin, end) {
  this.type = type;
  this.id = id;
  this.begin = begin;
  this.end = end;
};

Annotation.prototype.length = function() {
  return this.end - this.begin;
};

function AnnotationEditor(parentElement, tokens, annotations) {
  this.parentElement = parentElement;
  this.annotations = new Array();
  this.tokens = tokens;
  
  // Render tokens
  for (var i = 0, token; token = this.tokens[i++];) {
    this.parentElement.append("<span class=token id=token" + 
        (i - 1) + ">" + token + "</span>");
  }
  
  // Insert annotation after annotation 
  for (var i = 0, annotation; annotation = annotations[i++];) {
    this.addAnnotation(annotation);
  }
};

// Annotation Model:
// - All annotation indexes are token indexes
// - Annotations can contain other annotations.
// - Annotations which cross each other or intersect with each other are not allowed
//   for example A <START_1> B <START_2> C <END_1> D E <END_2>.

AnnotationEditor.prototype.addAnnotation = function(annotation) {
  
  // TODO: Do crossing annotation detection and fail if they do! (return false or fail harder!)
  
  // Note:
  // If an annotation span with the same bounds already exist the new annotation
  // span is placed inside the existing one
  $(".token").slice(annotation.begin, annotation.end).wrapAll("<span class=" + annotation.type +
      " id=" + annotation.type + annotation.id + ">");
  
  this.annotations.push(annotation);
};

AnnotationEditor.prototype.removeAnnotation = function(annotationId) {
  $("#" + annotationId).unwrap();
};


// Selection handling and navigation depends highly
// on the annotation task. There is no default way of
// doing it which fits all tasks.
// Therefore we should implement a few default tools which
// can be registered if required.

// Registers a previous and forward key shortcuts to navigate based on span types.
// Multiple span types can be passed in and optionally a function to choose a
// span to select in case it is ambiguous.
AnnotationEditor.prototype.setNavigationBySpanTypeKeys = function(spanType, previousKey, nextKey) {
};

// Registers a previous and forward key shortcuts to navigate with the help of a span locator function.
AnnotationEditor.prototype.setNavigationByFunctionKeys = function(previousKey, nextKey, spanLocator){
};


// If a user clicks with a mouse on something he expects a reaction
// It should be possible to register a function which helps dealing with
// such a selection in a default way

// e.g. user wants to select a few tokens
// e.g. user wants to select a named entity annotation
// He likely wants to do both in the same AnnotationEditor instance.
// Span type priority could help with this (also for navigation)

