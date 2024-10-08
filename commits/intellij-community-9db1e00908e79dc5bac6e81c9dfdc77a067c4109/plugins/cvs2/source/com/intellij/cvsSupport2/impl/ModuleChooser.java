package com.intellij.cvsSupport2.impl;

import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsBrowser.CvsFile;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.SelectCVSConfigurationStep;
import com.intellij.cvsSupport2.ui.experts.SelectCvsElementStep;
import com.intellij.openapi.cvsIntegration.CvsModule;
import com.intellij.openapi.cvsIntegration.CvsRepository;
import com.intellij.openapi.project.Project;

import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;

/**
 * author: lesya
 */
public class ModuleChooser extends CvsWizard {
  private final SelectCVSConfigurationStep mySelectCVSConfigurationStep;
  private final SelectCvsElementStep mySelectCvsElementStep;

  public ModuleChooser(Project project, 
                       boolean allowFileSelection,
                       boolean allowMultiplySelection,
                       boolean allowRootSelection,
                       String expertTitle,
                       String selectModulePageTitle) {
    super(expertTitle, project);
    mySelectCVSConfigurationStep = new SelectCVSConfigurationStep(project, this);
    mySelectCvsElementStep = new SelectCvsElementStep(selectModulePageTitle,
                                                      this,
                                                      project,
                                                      mySelectCVSConfigurationStep,
                                                      allowFileSelection, allowMultiplySelection ?
                                                                          TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION :
                                                                          TreeSelectionModel.SINGLE_TREE_SELECTION,
                                                      allowRootSelection, true);

    addStep(mySelectCVSConfigurationStep);
    addStep(mySelectCvsElementStep);

    init();
  }

  private CvsRepository getSelectedRepository() {
    return mySelectCVSConfigurationStep.getSelectedConfiguration().createCvsRepository();
  }

  public CvsModule[] getSelectedModules() {
    CvsRepository repository = getSelectedRepository();
    CvsElement[] selectedCvsElement = mySelectCvsElementStep.getSelectedCvsElements();
    ArrayList<CvsModule> result = new ArrayList<CvsModule>();
    for (int i = 0; i < selectedCvsElement.length; i++) {
      CvsElement cvsElement = selectedCvsElement[i];
      result.add(new CvsModule(repository, cvsElement.getElementPath(), cvsElement instanceof CvsFile));
    }
    return result.toArray(new CvsModule[result.size()]);
  }

}
