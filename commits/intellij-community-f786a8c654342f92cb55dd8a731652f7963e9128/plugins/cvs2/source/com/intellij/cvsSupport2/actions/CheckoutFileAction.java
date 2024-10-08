package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.ui.CheckoutFileDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.CvsBundle;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * author: lesya
 */
public class CheckoutFileAction extends ActionOnSelectedElement {

  private Collection myModifiedFiles;

  public CheckoutFileAction() {
    super(true);
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnLocallyDeletedFile();
    visibility.addCondition(FILES_EXIST_IN_CVS);
  }

  protected String getTitle(VcsContext context) {
    return CvsBundle.getCheckoutOperationName();
  }

  public void update(AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isVisible()) {
      return;
    }
    Project project = CvsContextWrapper.createInstance(e).getProject();
    if (project == null) return;
    adjustName(CvsVcs2.getInstance(project).getCheckoutOptions().getValue(), e);
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    if (myModifiedFiles != null) {
      if (!myModifiedFiles.isEmpty()) {
        if (!new ReplaceFileConfirmationDialog(context.getProject(), CvsBundle.getCheckoutOperationName()).requestConfirmation(myModifiedFiles)) {
          return CvsHandler.NULL;
        }

      }
    }

    myModifiedFiles = null;

    Project project = context.getProject();
    FilePath[] filesArray = context.getSelectedFilePaths();
    List<FilePath> files = Arrays.asList(filesArray);
    if (CvsVcs2.getInstance(project).getCheckoutOptions().getValue()
        || OptionsDialog.shiftIsPressed(context.getModifiers())){
      CheckoutFileDialog checkoutFileDialog = new CheckoutFileDialog(project,
                                                                     files);
      checkoutFileDialog.show();
      if (!checkoutFileDialog.isOK()) return CvsHandler.NULL;
    }

    return CommandCvsHandler.createCheckoutFileHandler(filesArray, CvsConfiguration.getInstance(project));
  }

  protected void beforeActionPerformed(VcsContext context) {
    super.beforeActionPerformed(context);
    myModifiedFiles =
      new ReplaceFileConfirmationDialog(context.getProject(), CvsBundle.getCheckoutOperationName()).collectModifiedFiles(context.getSelectedFiles());
  }
}
