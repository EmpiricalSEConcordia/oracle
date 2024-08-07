package com.intellij.ide.actions;

import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.jsp.JspFile;

public class ExternalJavaDocAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }

    PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if (element == null) {
      Messages.showMessageDialog(
        project,
        "Please select the element for which you wish to view the documentation",
        "No Element Selected",
        Messages.getErrorIcon()
      );
      return;
    }


    PsiFile context = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    PsiElement originalElement = (context!=null && editor!=null)? context.findElementAt(editor.getCaretModel().getOffset()):null;
    try {
      element.putUserData(JavaDocManager.ORIGINAL_ELEMENT_KEY,originalElement);
    } catch(RuntimeException ex) {
      // some UserDataHolder does not support putUserData, e.g. PsiPackage
      // tolerate it
    }

    JavaDocManager.getInstance(project).openJavaDoc(element);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null) {
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      boolean enabled = file instanceof PsiJavaFile ||
                        file instanceof JspFile ||
                        file!=null && JavaDocManager.getInstance(project).getProvider(file.getFileType())!=null;
      presentation.setEnabled(enabled);
      presentation.setVisible(enabled);
    }
    else{
      PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
      presentation.setEnabled(element != null);
      presentation.setVisible(true);
    }
  }
}