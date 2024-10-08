package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class InheritanceToDelegationUtil {
  public static boolean isInnerClassNeeded(PsiClass aClass, PsiClass baseClass) {
    if(baseClass.isInterface()) return true;
    if(baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
    PsiMethod[] methods = aClass.getMethods();

    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];

      if(method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      PsiMethod baseMethod = baseClass.findMethodBySignature(method, true);
      if(baseMethod != null) {
        PsiClass containingClass = baseMethod.getContainingClass();
        String qName = containingClass.getQualifiedName();
        if(qName == null || !"java.lang.Object".equals(qName)) return true;
      }
    }
    return false;
  }
}
