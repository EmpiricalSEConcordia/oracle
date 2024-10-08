package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.refactoring.introduceField.LocalToFieldHandler;

/**
 * @author ven
 */
public class MockLocalToFieldHandler extends LocalToFieldHandler {
  private boolean myMakeEnumConstant;
  public MockLocalToFieldHandler(Project project, boolean isConstant, final boolean makeEnumConstant) {
    super(project, isConstant);
    myMakeEnumConstant = makeEnumConstant;
  }

  protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(PsiClass aClass, PsiLocalVariable local, PsiExpression[] occurences,
                                                                        boolean isStatic) {
    return new BaseExpressionToFieldHandler.Settings("xxx", true, isStatic, true, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
                                                     PsiModifier.PRIVATE, local, null, false, aClass, true, myMakeEnumConstant);
  }
}