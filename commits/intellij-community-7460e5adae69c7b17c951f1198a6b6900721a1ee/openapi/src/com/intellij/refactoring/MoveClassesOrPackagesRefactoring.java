/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author dsl
 */
public interface MoveClassesOrPackagesRefactoring extends Refactoring {
  List<PsiElement> getElements();

  PackageWrapper getTargetPackage();

  void setSearchInComments(boolean value);

  void setSearchInNonJavaFiles(boolean value);

  boolean isSearchInComments();

  boolean isSearchInNonJavaFiles();
}
