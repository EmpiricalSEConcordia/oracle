package com.intellij.cvsSupport2.cvsoperations.cvsUpdate.ui;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnVirtualFiles;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui.DateOrRevisionOrTagSettings;
import com.intellij.cvsSupport2.ui.ChangeKeywordSubstitutionPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;

/**
 * author: lesya
 */
public class UpdateOptionsPanel {

  private JCheckBox myPruneEmptyDirectories;
  private TextFieldWithBrowseButton myBranch;
  private TextFieldWithBrowseButton myBranch2;
  private JCheckBox mySwitchToHeadRevision;
  private JCheckBox myCreateNewDirectories;
  private JCheckBox myCleanCopy;
  private JPanel myDateOrRevisionPanel;
  private ChangeKeywordSubstitutionPanel myChangeKeywordSubstitutionPanel;

  private DateOrRevisionOrTagSettings myDateOrRevisionOrTagSettings;

  private JPanel myPanel;
  private JPanel myKeywordSubstitutionPanel;
  private JRadioButton myDoNotMerge;
  private JRadioButton myMergeWithBranch;
  private JRadioButton myMergeTwoBranches;

  private final JRadioButton[] myMergingGroup;

  private final Project myProject;

  public UpdateOptionsPanel(Project project,
                            final Collection<FilePath> files) {
    myProject = project;
    myChangeKeywordSubstitutionPanel =
    new ChangeKeywordSubstitutionPanel(CvsConfiguration.getInstance(myProject).UPDATE_KEYWORD_SUBSTITUTION);
    CvsConfiguration.getInstance(myProject).CLEAN_COPY = false;
    CvsConfiguration.getInstance(myProject).RESET_STICKY = false;
    myMergingGroup = new JRadioButton[]{myDoNotMerge, myMergeWithBranch, myMergeTwoBranches};
    ButtonGroup mergingGroup = new ButtonGroup();
    mergingGroup.add(myDoNotMerge);
    mergingGroup.add(myMergeWithBranch);
    mergingGroup.add(myMergeTwoBranches);


    myKeywordSubstitutionPanel.setLayout(new BorderLayout());
    myKeywordSubstitutionPanel.add(myChangeKeywordSubstitutionPanel.getPanel(), BorderLayout.CENTER);
    myDateOrRevisionOrTagSettings = new DateOrRevisionOrTagSettings(new TagsProviderOnVirtualFiles(files),
                                                                    project, false);
    myDateOrRevisionOrTagSettings.setHeadCaption("Default");
    myDateOrRevisionPanel.setLayout(new BorderLayout());
    myDateOrRevisionPanel.add(myDateOrRevisionOrTagSettings.getPanel(), BorderLayout.CENTER);


    TagsHelper.addChooseBranchAction(myBranch, files, project, false);

    TagsHelper.addChooseBranchAction(myBranch2, files, project, false);

  }

  public void reset() {
    CvsConfiguration config = CvsConfiguration.getInstance(myProject);
    myPruneEmptyDirectories.setSelected(config.PRUNE_EMPTY_DIRECTORIES);
    myDoNotMerge.setSelected(true);

    myBranch.setText(config.MERGE_WITH_BRANCH1_NAME);
    myBranch2.setText(config.MERGE_WITH_BRANCH2_NAME);
    mySwitchToHeadRevision.setSelected(false);
    myCreateNewDirectories.setSelected(config.CREATE_NEW_DIRECTORIES);
    myCleanCopy.setSelected(false);

    myDateOrRevisionOrTagSettings.updateFrom(config.UPDATE_DATE_OR_REVISION_SETTINGS);

    for (int i = 0; i < myMergingGroup.length; i++) {
      JRadioButton jRadioButton = myMergingGroup[i];
      jRadioButton.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          enableBranchField();
        }
      });
    }

    enableBranchField();

  }

  private void enableBranchField() {
    int mergingMode = getSelected(myMergingGroup);
    switch (mergingMode) {
      case CvsConfiguration.DO_NOT_MERGE:
        {
          myBranch.setEnabled(false);
          myBranch2.setEnabled(false);
          return;
        }
      case CvsConfiguration.MERGE_WITH_BRANCH:
        {
          myBranch.setEnabled(true);
          myBranch2.setEnabled(false);
          return;
        }
      case CvsConfiguration.MERGE_TWO_BRANCHES:
        {
          myBranch.setEnabled(true);
          myBranch2.setEnabled(true);
          return;
        }

    }
  }

  public void apply() {
    CvsConfiguration configuration = CvsConfiguration.getInstance(myProject);

    configuration.CLEAN_COPY = false;
    if (myCleanCopy.isSelected()) {
      if (Messages.showYesNoDialog(
        "'Clean copy' option was enabled and your changes will be replaced. Are you sure you want to perform update with this option?",
        "Clean Copy Confirmation", Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE) {
        configuration.CLEAN_COPY = true;
      }
    }


    configuration.PRUNE_EMPTY_DIRECTORIES = myPruneEmptyDirectories.isSelected();
    configuration.MERGING_MODE = getSelected(myMergingGroup);
    configuration.MERGE_WITH_BRANCH1_NAME = myBranch.getText();
    configuration.MERGE_WITH_BRANCH2_NAME = myBranch2.getText();
    configuration.RESET_STICKY = mySwitchToHeadRevision.isSelected();
    configuration.CREATE_NEW_DIRECTORIES = myCreateNewDirectories.isSelected();
    configuration.UPDATE_KEYWORD_SUBSTITUTION = myChangeKeywordSubstitutionPanel.getKeywordSubstitution();


    myDateOrRevisionOrTagSettings.saveTo(configuration.UPDATE_DATE_OR_REVISION_SETTINGS);
  }

  private int getSelected(JRadioButton[] mergingGroup) {
    for (int i = 0; i < mergingGroup.length; i++) {
      JRadioButton jRadioButton = mergingGroup[i];
      if (jRadioButton.isSelected()) return i;
    }
    return 0;
  }

  public JComponent getPanel() {
    return myPanel;
  }
}
