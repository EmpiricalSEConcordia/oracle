package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.RefactoringMessageUtil;

/**
 * @author ven
 */
public class InlineConstantFieldHandler {
  private static final String REFACTORING_NAME = "Inline field";

  public void invoke(Project project, Editor editor, PsiField field) {
    if (!field.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, field);
      return;
    }

    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      String message = REFACTORING_NAME + " refactoring is supported only for final fields";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_FIELD, project);
      return;
    }

    if (!field.hasInitializer()) {
      String message = "No initializer present for the field";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_FIELD, project);
      return;
    }

    PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
    final PsiReference[] refs = searchHelper.findReferences(field, GlobalSearchScope.projectScope(project), false);

    if (refs.length == 0){
      String message = "Field " + field.getName() + " is never used";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
      return;
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null && !field.equals(reference.resolve())) {
      reference = null;
    }

    final boolean invokedOnReference = (reference != null);
    if (!invokedOnReference && !field.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, field);
      return;
    }
    PsiReferenceExpression element = reference != null ? (PsiReferenceExpression)reference.getElement() : null;
    final InlineConstantFieldProcessor processor = new InlineConstantFieldProcessor(field, project, element, editor);
    InlineFieldDialog dialog = new InlineFieldDialog(project, field, invokedOnReference, processor);
    dialog.show();
  }
}
