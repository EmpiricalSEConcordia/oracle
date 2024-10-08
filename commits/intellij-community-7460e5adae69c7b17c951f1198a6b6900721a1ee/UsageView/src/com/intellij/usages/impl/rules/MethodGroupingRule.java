package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 20, 2004
 * Time: 11:06:30 AM
 * To change this template use File | Settings | File Templates.
 */
public class MethodGroupingRule implements UsageGroupingRule {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.rules.MethodGroupingRule");
  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof PsiElementUsage) {
      PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      if (psiElement.getContainingFile() instanceof PsiJavaFile) {
        PsiElement containingMethod = psiElement;
        do {
          containingMethod = PsiTreeUtil.getParentOfType(containingMethod, PsiMethod.class, true);
          if (containingMethod == null || ((PsiMethod)containingMethod).getContainingClass().getQualifiedName() != null) break;
        }
        while (true);

        if (containingMethod != null) {
          return new MethodUsageGroup((PsiMethod)containingMethod);
        }
      }
    }
    return null;
  }
  
  private static class MethodUsageGroup implements UsageGroup, DataProvider {
    private SmartPsiElementPointer myMethodPointer;
    private String myName;
    private Icon myIcon;

    public MethodUsageGroup(PsiMethod psiMethod) {
      myName = PsiFormatUtil.formatMethod(
          psiMethod,
          PsiSubstitutor.EMPTY,
          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
          PsiFormatUtil.SHOW_TYPE
        );

      myIcon = getIconImpl(psiMethod);
      myMethodPointer = SmartPointerManager.getInstance(psiMethod.getProject()).createLazyPointer(psiMethod);
    }

    private Icon getIconImpl(PsiMethod psiMethod) {
      return psiMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }

    public int hashCode() {
      return myName.hashCode();
    }

    public boolean equals(Object object) {
      return Comparing.equal(myName, ((MethodUsageGroup)object).myName);
    }

    public Icon getIcon(boolean isOpen) {
      return isValid() ? getIconImpl(getMethod()) : myIcon;
    }

    private PsiMethod getMethod() {
      return (PsiMethod)myMethodPointer.getElement();
    }

    public String getText(UsageView view) {
      return myName;
    }

    public FileStatus getFileStatus() {
      return isValid() ? getMethod().getFileStatus() : null;
    }

    public boolean isValid() {
      return getMethod() != null;
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
      if (canNavigate()) {
          getMethod().navigate(focus);
      }
    }

    public boolean canNavigate() {
      return isValid();
    }

    public int compareTo(UsageGroup usageGroup) {
      if (!(usageGroup instanceof MethodUsageGroup)) {
        LOG.error("MethodUsageGroup expected but " + usageGroup.getClass() + " found");
      }

      return myName.compareTo(((MethodUsageGroup)usageGroup).myName);
    }

    public Object getData(String dataId) {
      if (dataId.equals(DataConstants.PSI_ELEMENT)) {
        return getMethod();
      }

      return null;
    }
  }
}
