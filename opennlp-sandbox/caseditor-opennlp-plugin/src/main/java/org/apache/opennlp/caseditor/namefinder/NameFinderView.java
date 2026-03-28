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

package org.apache.opennlp.caseditor.namefinder;

import org.apache.uima.caseditor.editor.CasEditorView;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.ui.part.IPageBookViewPage;

public class NameFinderView extends CasEditorView {

  public static final String ID = "org.apache.opennlp.caseditor.NameFinderView";

  public NameFinderView() {
    super("The Name Finder View is currently not available.");
  }

  @Override
  protected IPageBookViewPage doCreatePage(ICasEditor editor) {
    ICasDocument document = editor.getDocument();

    if (document != null) {
      return new NameFinderViewPage(this, editor, document);
    }

    return null;
  }
}
