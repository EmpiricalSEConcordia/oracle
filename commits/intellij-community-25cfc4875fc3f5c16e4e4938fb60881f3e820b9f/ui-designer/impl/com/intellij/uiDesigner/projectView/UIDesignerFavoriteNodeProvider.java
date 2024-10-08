/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.projectView;

import com.intellij.ide.favoritesTreeView.FavoriteNodeProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.compiler.Utils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class UIDesignerFavoriteNodeProvider implements FavoriteNodeProvider {
  @Nullable
  public Collection<AbstractTreeNode> getFavoriteNodes(DataContext context, final ViewSettings viewSettings) {
    Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) return null;
    Form[] forms = Form.DATA_KEY.getData(context);
    if (forms != null) {
      Collection<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      Set<PsiClass> bindClasses = new HashSet<PsiClass>();
      for (Form form: forms) {
        final PsiClass classToBind = form.getClassToBind();
        if (classToBind != null) {
          if (bindClasses.contains(classToBind)) continue;
          bindClasses.add(classToBind);
          result.add(FormNode.constructFormNode(classToBind, project, viewSettings));
        }
      }
      if (!result.isEmpty()) {
        return result;
      }
    }

    VirtualFile vFile = PlatformDataKeys.VIRTUAL_FILE.getData(context);
    if (vFile != null) {
      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(vFile);
      if (fileType.equals(StdFileTypes.GUI_DESIGNER_FORM)) {
        final PsiFile formFile = PsiManager.getInstance(project).findFile(vFile);
        if (formFile == null) return null;
        String text = formFile.getText();
        String className;
        try {
          className = Utils.getBoundClassName(text);
        }
        catch (Exception e) {
          return null;
        }
        if (className == null) return null;
        final PsiClass classToBind = PsiManager.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
        if (classToBind != null) {
          Form form = new Form(classToBind);
          final AbstractTreeNode node = new FormNode(project, form, viewSettings);
          return Collections.singletonList(node);
        }
      }
    }

    return null;
  }

  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    if (element instanceof Form){
      Form form = (Form) element;
      return form.containsFile(vFile);
    }
    return false;
  }

  public int getElementWeight(final Object element) {
    if (element instanceof Form) return 9;
    return -1;
  }

  @Nullable
  public String getElementLocation(final Object element) {
    if (element instanceof Form) {
      final PsiFile[] psiFiles = ((Form)element).getFormFiles();
      VirtualFile vFile = null;
      if (psiFiles.length > 0) {
        vFile = psiFiles [0].getVirtualFile();
      }
      if (vFile != null) {
        return vFile.getPresentableUrl();
      }
    }
    return null;
  }

  public boolean isInvalidElement(Object element) {
    if (element instanceof Form) {
      return !((Form) element).isValid();
    }
    return false;
  }

  @NotNull @NonNls
  public String getFavoriteTypeId() {
    return "form";
  }

  @Nullable @NonNls
  public String getElementUrl(Object element) {
    if (element instanceof Form) {
      Form form = (Form)element;
      return form.getClassToBind().getQualifiedName();
    }
    return null;
  }

  public String getElementModuleName(final Object element) {
    if (element instanceof Form) {
      Form form = (Form)element;
      final Module module = ModuleUtil.findModuleForPsiElement(form.getClassToBind());
      return module != null ? module.getName() : null;
    }
    return null;
  }

  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiClass classToBind = psiManager.findClass(url, GlobalSearchScope.allScope(project));
    if (classToBind == null) return null;
    return new Object[] { new Form(classToBind) };
  }
}
