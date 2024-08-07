/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 31-Jul-2006
 * Time: 17:44:39
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class InspectionToolsConfigurable extends BaseConfigurable implements ErrorsConfigurable, SearchableConfigurable,
                                                                                      Configurable.NoScroll {
  private final CardLayout myLayout = new CardLayout();
  private JPanel myPanel;

  public static final String ID = "Errors";
  public static final String DISPLAY_NAME = "Inspections";

  protected JComboBox myProfiles;
  private final Map<String, SingleInspectionProfilePanel> myPanels = new HashMap<String, SingleInspectionProfilePanel>();

  private JPanel myWholePanel;
  private JButton myAddButton;
  private JButton myDeleteButton;
  private JButton myImportButton;
  private JButton myExportButton;
  private JCheckBox myShareProfileCheckBox;
  private JButton myCopyButton;
  private JBScrollPane myJBScrollPane;

  private final ArrayList<String> myDeletedProfiles = new ArrayList<String>();
  protected final InspectionProfileManager myProfileManager;
  protected final InspectionProjectProfileManager myProjectProfileManager;
  private static final Logger LOG = Logger.getInstance("#" + InspectionToolsConfigurable.class.getName());
  private Alarm mySelectionAlarm;


  public InspectionToolsConfigurable(InspectionProjectProfileManager projectProfileManager, InspectionProfileManager profileManager) {
    InspectionToolRegistrar.getInstance().buildInspectionSearchIndexIfNecessary();
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Set<String> existingProfileNames = myPanels.keySet();
        final ModifiableModel model = SingleInspectionProfilePanel.createNewProfile(-1, getSelectedObject(), myWholePanel, "", existingProfileNames);
        if (model != null) {
          addProfile((InspectionProfileImpl)model);
          myDeletedProfiles.remove(model.getName());
          myDeleteButton.setEnabled(true);
        }
      }
    });

    myDeleteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final InspectionProfileImpl selectedProfile = (InspectionProfileImpl)myProfiles.getSelectedItem();
        ((DefaultComboBoxModel)myProfiles.getModel()).removeElement(selectedProfile);
        myDeletedProfiles.add(selectedProfile.getName());
        myDeleteButton.setEnabled(isDeleteEnabled(selectedProfile));
      }
    });

    final Project project = projectProfileManager.getProject();
    myImportButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false){
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            return file.getFileType().equals(StdFileTypes.XML);
          }
        };
        descriptor.setDescription("Choose profile file");
        final VirtualFile file = FileChooser.chooseFile(descriptor, myWholePanel, project, null);
        if (file == null) return;
        InspectionProfileImpl profile =
        new InspectionProfileImpl("TempProfile", InspectionToolRegistrar.getInstance(), myProfileManager);
        try {
          Element rootElement = JDOMUtil.loadDocument(VfsUtil.virtualToIoFile(file)).getRootElement();
          if (Comparing.strEqual(rootElement.getName(), "component")) {//import right from .idea/inspectProfiles/xxx.xml
            rootElement = (Element)rootElement.getChildren().get(0);
          }
          final Set<String> levels = new HashSet<String>();
          for (Object o : rootElement.getChildren("inspection_tool")) {
            final Element inspectElement = (Element)o;
            levels.add(inspectElement.getAttributeValue("level"));
            for (Object s : inspectElement.getChildren("scope")) {
              levels.add(((Element)s).getAttributeValue("level"));
            }
          }
          for (Iterator<String> iterator = levels.iterator(); iterator.hasNext();) {
            String level = iterator.next();
            if (myProfileManager.getOwnSeverityRegistrar().getSeverity(level) != null) {
              iterator.remove();
            }
          }
          if (!levels.isEmpty()) {
            if (Messages.showYesNoDialog(myWholePanel, "Undefined severities detected: " +
                                                          StringUtil.join(levels, ", ") +
                                                          ". Do you want to create them?", "Warning", Messages.getWarningIcon()) ==
                DialogWrapper.OK_EXIT_CODE) {
              for (String level : levels) {
                final TextAttributes textAttributes = CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes();
                HighlightInfoType.HighlightInfoTypeImpl info
                  = new HighlightInfoType.HighlightInfoTypeImpl(new HighlightSeverity(level, 50), TextAttributesKey.createTextAttributesKey(level));
                myProfileManager.getOwnSeverityRegistrar()
                  .registerSeverity(new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes.clone(), info),
                                    textAttributes.getErrorStripeColor());
              }
            }
          }
          profile.readExternal(rootElement);
          profile.setLocal(true);
          profile.initInspectionTools(null);
          profile.setModified(true);
          if (myPanels.get(profile.getName()) != null) {
            if (Messages.showOkCancelDialog(myWholePanel, "Profile with name \'" + profile.getName() + "\' already exists. Do you want to overwrite it?", "Warning", Messages.getInformationIcon()) != DialogWrapper.OK_EXIT_CODE) return;
          }
          addProfile((InspectionProfileImpl)profile.getModifiableModel());
          myDeletedProfiles.remove(profile.getName());
          myDeleteButton.setEnabled(true);
        }
        catch (InvalidDataException e1) {
          LOG.error(e1);
        }
        catch (JDOMException e1) {
          LOG.error(e1);
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
      }
    });

    myExportButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setDescription("Choose directory to store profile file");
        final VirtualFile file = FileChooser.chooseFile(descriptor, myWholePanel, project, null);
        if (file == null) return;
        final Element element = new Element("inspections");
        try {
          final SingleInspectionProfilePanel panel = getSelectedPanel();
          LOG.assertTrue(panel != null);
          final InspectionProfileImpl profile = (InspectionProfileImpl)panel.getSelectedProfile();
          profile.writeExternal(element);
          final String filePath = FileUtil.toSystemDependentName(file.getPath()) + File.separator + FileUtil.sanitizeFileName(profile.getName()) + ".xml";
          if (new File(filePath).isFile()) {
            if (Messages.showOkCancelDialog(myWholePanel, "File \'" + filePath + "\' already exist. Do you want to overwrite it?", "Warning", Messages.getQuestionIcon()) != DialogWrapper.OK_EXIT_CODE) return;
          }
          JDOMUtil.writeDocument(new Document(element), filePath, SystemProperties.getLineSeparator());
        }
        catch (WriteExternalException e1) {
          LOG.error(e1);
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
      }
    });

    myCopyButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Set<String> existingProfileNames = myPanels.keySet();
        final InspectionProfileImpl model = (InspectionProfileImpl)
          SingleInspectionProfilePanel.createNewProfile(0, getSelectedObject(), myWholePanel, "", existingProfileNames);
        if (model != null) {
          final InspectionProfileImpl modifiableModel = (InspectionProfileImpl)model.getModifiableModel();
          modifiableModel.setModified(true);
          addProfile(modifiableModel);
          myDeletedProfiles.remove(model.getName());
          myDeleteButton.setEnabled(true);
        }
      }
    });

    myProjectProfileManager = projectProfileManager;
    myProfileManager = profileManager;

    myJBScrollPane.setBorder(null);
  }

  private void addProfile(InspectionProfileImpl model) {
    final String modelName = model.getName();
    final SingleInspectionProfilePanel panel = new SingleInspectionProfilePanel(myProjectProfileManager, modelName, model);
    myPanel.add(modelName, panel);
    if (!myPanels.containsKey(modelName)) {
      ((DefaultComboBoxModel)myProfiles.getModel()).addElement(model);
    }
    myPanels.put(modelName, panel);
    myProfiles.setSelectedItem(model);
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public Icon getIcon() {
    return AllIcons.General.ConfigurableErrorHighlighting;
  }

  public String getHelpTopic() {
    return "preferences.inspections";
  }

  @NotNull
  public String getId() {
    return ID;
  }

  public Runnable enableSearch(final String option) {
    return new Runnable(){
      public void run() {
        SingleInspectionProfilePanel panel = getSelectedPanel();
        if (panel != null) {
          panel.setFilter(option);
        }
      }
    };
  }

  public JComponent createComponent() {
    myProfiles.setRenderer(new ListCellRendererWrapper<Profile>(myProfiles.getRenderer()) {
      @Override
      public void customize(final JList list, final Profile value, final int index, final boolean selected, final boolean hasFocus) {
        final String profileName = value.getName();
        setText(profileName);
        final SingleInspectionProfilePanel panel = myPanels.get(profileName);
        setIcon(panel != null && panel.isProfileShared() ? Profile.PROJECT_PROFILE : Profile.LOCAL_PROFILE);
      }
    });
    myProfiles.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final InspectionProfileImpl profile = (InspectionProfileImpl)myProfiles.getSelectedItem();
        myDeleteButton.setEnabled(isDeleteEnabled(profile));
        myLayout.show(myPanel, profile.getName());
        SingleInspectionProfilePanel panel = getSelectedPanel();
        if (panel != null) {
          myShareProfileCheckBox.setSelected(panel.isProfileShared());
        }
      }
    });
    myShareProfileCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final SingleInspectionProfilePanel panel = getSelectedPanel();
        LOG.assertTrue(panel != null, "No settings panel for: " + getSelectedObject());
        panel.setProfileShared(myShareProfileCheckBox.isSelected());
        myProfiles.repaint();
      }
    });

    myPanel.setLayout(myLayout);
    return myWholePanel;
  }

  protected abstract InspectionProfileImpl getCurrentProfile();

  public boolean isModified() {
    for (SingleInspectionProfilePanel panel : myPanels.values()) {
      if (panel.isModified()) return true;
    }
    if (getProfiles().size() != myPanels.size()) return true;
    return !myDeletedProfiles.isEmpty();
  }

  public void apply() throws ConfigurationException {
    for (final Iterator<String> it = myPanels.keySet().iterator(); it.hasNext();) {
      final String name = it.next();
      if (myDeletedProfiles.remove(name)) {
        deleteProfile(name);
        it.remove();
      } else {
        myPanels.get(name).apply();
      }
    }
  }

  protected void deleteProfile(String name) {
    if (myProfileManager.getProfile(name, false) != null) {
      myProfileManager.deleteProfile(name);
    }
    if (myProjectProfileManager.getProfile(name, false) != null) {
      myProjectProfileManager.deleteProfile(name);
    }
  }

  public void reset() {
    myDeletedProfiles.clear();
    myPanels.clear();
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    myProfiles.setModel(model);
    for (Profile profile : getProfiles()) {
      model.addElement(profile);
      final String profileName = profile.getName();
      final SingleInspectionProfilePanel panel = new SingleInspectionProfilePanel(myProjectProfileManager, profileName, ((InspectionProfileImpl)profile).getModifiableModel());
      myPanels.put(profileName, panel);
      myPanel.add(profileName, panel);
    }
    final InspectionProfileImpl inspectionProfile = getCurrentProfile();
    myProfiles.setSelectedItem(inspectionProfile);
    myLayout.show(myPanel, inspectionProfile.getName());
    myDeleteButton.setEnabled(isDeleteEnabled(inspectionProfile));
    final SingleInspectionProfilePanel panel = getSelectedPanel();
    if (panel != null) {
      panel.setVisible(true);//make sure that UI was initialized
      mySelectionAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (mySelectionAlarm != null) {
            mySelectionAlarm.addRequest(new Runnable() {
              public void run() {
                panel.updateSelection();
              }
            }, 200);
          }
        }
      });

      myShareProfileCheckBox.setSelected(panel.isProfileShared());
    }
  }

  private boolean isDeleteEnabled(InspectionProfileImpl inspectionProfile) {
    final ProfileManager profileManager = inspectionProfile.getProfileManager();

    boolean projectProfileFound = false;
    boolean ideProfileFound = false;

    final ComboBoxModel model = myProfiles.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      Profile profile = (Profile)model.getElementAt(i);
      if (inspectionProfile == profile) continue;
      final boolean isProjectProfile = profile.getProfileManager() == myProjectProfileManager;
      projectProfileFound |= isProjectProfile;
      ideProfileFound |= !isProjectProfile;

      if (ideProfileFound && projectProfileFound) break;
    }

    return profileManager == myProjectProfileManager ? projectProfileFound : ideProfileFound;
  }
  
  protected Collection<Profile> getProfiles() {
    final Collection<Profile> result = new ArrayList<Profile>();
    result.addAll(myProfileManager.getProfiles());
    result.addAll(myProjectProfileManager.getProfiles());
    return result;
  }

  public void disposeUIResources() {
    for (SingleInspectionProfilePanel panel : myPanels.values()) {
      panel.disposeUI();
    }
    myPanels.clear();
    if (mySelectionAlarm != null) {
      Disposer.dispose(mySelectionAlarm);
      mySelectionAlarm = null;
    }
  }

  public void selectProfile(String name) {
    for (int i = 0; i < myProfiles.getItemCount(); i++) {
      if (Comparing.strEqual(((InspectionProfileImpl)myProfiles.getItemAt(i)).getName(), name)) {
        myProfiles.setSelectedIndex(i);
        break;
      }
    }
  }

  public void selectInspectionTool(String selectedToolShortName) {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    assert inspectionProfile != null : configuredProfiles();
    final SingleInspectionProfilePanel panel = myPanels.get(inspectionProfile.getName());
    LOG.assertTrue(panel != null, "No settings panel for: " + inspectionProfile.getName() + "; " + configuredProfiles());
    panel.selectInspectionTool(selectedToolShortName);
  }

  protected SingleInspectionProfilePanel getSelectedPanel() {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    assert inspectionProfile != null : configuredProfiles();
    return myPanels.get(inspectionProfile.getName());
  }

  private String configuredProfiles() {
    return "configured profiles: " + StringUtil.join(myPanels.keySet(), ", ");
  }

  protected Set<String> getKnownNames() {
    return myPanels.keySet();
  }

  public InspectionProfileImpl getSelectedObject() {
    return (InspectionProfileImpl)myProfiles.getSelectedItem();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    assert inspectionProfile != null : configuredProfiles();
    return myPanels.get(inspectionProfile.getName()).getTree();
  }
}
