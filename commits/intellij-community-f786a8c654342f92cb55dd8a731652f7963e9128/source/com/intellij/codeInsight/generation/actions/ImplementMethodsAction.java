package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.impl.analysis.ClassUtil;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.ImplementMethodsHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.HierarchicalMethodSignature;

import java.util.Set;
import java.util.Collection;

/**
 *
 */
public class ImplementMethodsAction extends BaseCodeInsightAction {

  protected CodeInsightActionHandler getHandler() {
    return new ImplementMethodsHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    if (!file.canContainJavaCode()) {
      return false;
    }

    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (aClass == null) {
      return false;
    }
    final Collection<HierarchicalMethodSignature> allMethods = aClass.getVisibleSignatures();
    return ClassUtil.getAnyMethodToImplement(aClass, allMethods) != null;
  }
}