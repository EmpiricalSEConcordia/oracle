package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.ui.ListScrollingUtil;

class PageDownHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public PageDownHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = editor.getUserData(LookupImpl.LOOKUP_IN_EDITOR_KEY);
    if (lookup == null){
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    ListScrollingUtil.movePageDown(lookup.getList());
  }
}
