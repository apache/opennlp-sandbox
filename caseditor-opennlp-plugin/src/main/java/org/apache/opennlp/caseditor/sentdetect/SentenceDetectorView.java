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

package org.apache.opennlp.caseditor.sentdetect;

import org.apache.uima.caseditor.editor.CasEditorView;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.ui.part.IPageBookViewPage;

public class SentenceDetectorView extends CasEditorView {

  public SentenceDetectorView() {
    super("The Sentence Detector View is currently not available.");
  }

  protected PageRec doCreatePages(ICasEditor editor) {
    PageRec result = null;

    ICasDocument document = editor.getDocument();

    if (document != null) {

      SentenceDetectorViewPage page = new SentenceDetectorViewPage(this, editor);
      initPage(page);
      page.createControl(getPageBook());

      result = new PageRec(editor, page);
    }

    return result;
  }

  @Override
  protected IPageBookViewPage doCreatePage(ICasEditor editor) {
    ICasDocument document = editor.getDocument();

    if (document != null) {
      return new SentenceDetectorViewPage(this, editor);
    }

    return null;
  }
  
}
