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


import static  org.apache.opennlp.caseditor.OpenNLPPreferenceConstants.*;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;

public class ConfirmedNameDetectionFieldEditor extends FieldEditor {

  private Button forceDetectionButton;

  private Composite optionButtons;

  private Button ignoreShortTokensButton;
  private Button onlyConsiderAllLetterTokensButton;
  private Button onlyConsiderInitialCapitalTokensButton;

  public ConfirmedNameDetectionFieldEditor(Composite parent) {
    super("", "", parent);
  }

  @Override
  protected void adjustForNumColumns(int numColumns) {
  }

  private void checkState() {
    ignoreShortTokensButton.setEnabled(forceDetectionButton.getSelection());
    onlyConsiderAllLetterTokensButton.setEnabled(forceDetectionButton.getSelection());
    onlyConsiderInitialCapitalTokensButton.setEnabled(forceDetectionButton.getSelection());
  }

  @Override
  protected void doFillIntoGrid(Composite parent, int numColumns) {

    Group buttonGroup = new Group(parent, SWT.NONE);
    buttonGroup.setText("Forced name detection");
    buttonGroup.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
    buttonGroup.setLayout(new GridLayout());

    forceDetectionButton = new Button(buttonGroup, SWT.CHECK);
    forceDetectionButton.setText("Force the detection of existing names");
    forceDetectionButton.addListener(SWT.Selection, new Listener(){

      @Override
      public void handleEvent(Event event) {
        checkState();
      }});

    optionButtons = new Composite(buttonGroup, SWT.NONE);
    optionButtons.setLayout(new GridLayout());

    // Ignore short tokens
    ignoreShortTokensButton = new Button(optionButtons, SWT.CHECK);
    ignoreShortTokensButton.setText("Ignore short tokens");

    // Only consider all letter tokens
    onlyConsiderAllLetterTokensButton = new Button(optionButtons, SWT.CHECK);
    onlyConsiderAllLetterTokensButton.setText("Only consider all letter tokens");

    onlyConsiderInitialCapitalTokensButton = new Button(optionButtons, SWT.CHECK);
    onlyConsiderInitialCapitalTokensButton.setText("Only consider initial capital tokens");

    // TODO:
    // Ignore ambiguous tokens
  }

  @Override
  protected void doLoad() {
    if (forceDetectionButton != null) {

      forceDetectionButton.setSelection(
          getPreferenceStore().getBoolean(ENABLE_CONFIRMED_NAME_DETECTION));

      ignoreShortTokensButton.setSelection(
          getPreferenceStore().getBoolean(IGNORE_SHORT_TOKENS));

      onlyConsiderAllLetterTokensButton.setSelection(
          getPreferenceStore().getBoolean(ONLY_CONSIDER_ALL_LETTER_TOKENS));

      onlyConsiderInitialCapitalTokensButton.setSelection(
          getPreferenceStore().getBoolean(ONLY_CONSIDER_INITIAL_CAPITAL_TOKENS));

      checkState();
    }
  }

  @Override
  protected void doLoadDefault() {
  }

  @Override
  protected void doStore() {
    getPreferenceStore().setValue(
        ENABLE_CONFIRMED_NAME_DETECTION, forceDetectionButton.getSelection());

    getPreferenceStore().setValue(
        IGNORE_SHORT_TOKENS, ignoreShortTokensButton.getSelection());

    getPreferenceStore().setValue(ONLY_CONSIDER_ALL_LETTER_TOKENS,
        onlyConsiderAllLetterTokensButton.getSelection());

    getPreferenceStore().setValue( ONLY_CONSIDER_INITIAL_CAPITAL_TOKENS,
        onlyConsiderInitialCapitalTokensButton.getSelection());
  }

  @Override
  public int getNumberOfControls() {
    return 1;
  }
}
