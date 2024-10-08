package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.config.impl.AntReference;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.StorageAccessors;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

public class AntSetPanel {
  private final Form myForm;
  private final GlobalAntConfiguration myAntConfiguration;

  public AntSetPanel() {
    this(GlobalAntConfiguration.getInstance());
  }

  AntSetPanel(GlobalAntConfiguration antConfiguration) {
    myAntConfiguration = antConfiguration;
    myForm = new Form(antConfiguration);
  }

  @Nullable
  public AntInstallation showDialog(JComponent parent) {
    final DialogWrapper dialog = new MyDialog(parent);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }
    
    apply();
    return myForm.getSelectedAnt();
  }

  void reset() {
    myForm.setAnts(myAntConfiguration.getConfiguredAnts().values());
  }

  void apply() {
    for (AntInstallation ant : myForm.getRemovedAnts()) {
      myAntConfiguration.removeConfiguration(ant);
    }
    
    final Map<AntReference, AntInstallation> currentAnts = myAntConfiguration.getConfiguredAnts();
    for (AntInstallation installation : currentAnts.values()) {
      installation.updateClasspath();
    }
    
    for (AntInstallation ant : myForm.getAddedAnts()) {
      myAntConfiguration.addConfiguration(ant);
    }
    myForm.applyModifications();
  }

  public void setSelection(AntInstallation antInstallation) {
    myForm.selectAnt(antInstallation);
  }

  public JComponent getComponent() {
    return myForm.getComponent();
  }

  private static class Form implements AntUIUtil.PropertiesEditor<AntInstallation> {
    private final Splitter mySplitter = new Splitter(false);
    private final StorageAccessors myAccessors = StorageAccessors.createGlobal("antConfigurations");
    private final RightPanel myRightPanel;
    private AnActionListEditor<AntInstallation> myAnts = new AnActionListEditor<AntInstallation>();
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();
    private final EditPropertyContainer myGlobalWorkingProperties;
    private final Map<AntInstallation, EditPropertyContainer> myWorkingProperties = new HashMap<AntInstallation, EditPropertyContainer>();

    private AntInstallation myCurrent;
    private final PropertyChangeListener myImmediateUpdater = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        myBinding.apply(getProperties(myCurrent));
        myAnts.updateItem(myCurrent);
      }
    };

    public Form(final GlobalAntConfiguration antInstallation) {
      mySplitter.setProportion(myAccessors.getFloat("splitter", 0.3f));
      mySplitter.setShowDividerControls(true);
      mySplitter.setFirstComponent(myAnts);
      myGlobalWorkingProperties = new EditPropertyContainer(antInstallation.getProperties());
      myRightPanel = new RightPanel(myBinding, myImmediateUpdater);
      mySplitter.setSecondComponent(myRightPanel.myWholePanel);
      myAnts.addAddAction(new NewAntFactory(myAnts));
      myAnts.addRemoveButtonForAnt(antInstallation.IS_USER_ANT, AntBundle.message("remove.action.name"));
      myAnts.actionsBuilt();
      JList list = myAnts.getList();
      list.setCellRenderer(new AntUIUtil.AntInstallationRenderer(this));
      list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (myCurrent != null) myBinding.apply(getProperties(myCurrent));
          myCurrent = myAnts.getSelectedItem();
          if (myCurrent == null) {
            myBinding.loadValues(AbstractProperty.AbstractPropertyContainer.EMPTY);
            myBinding.beDisabled();
          }
          else {
            if (antInstallation.IS_USER_ANT.value(myCurrent)) {
              myBinding.beEnabled();
            }
            else {
              myBinding.beDisabled();
            }
            myBinding.loadValues(getProperties(myCurrent));
          }
        }
      });
    }

    public JList getAntsList() {
      return myAnts.getList();
    }

    public JComponent getComponent() {
      return mySplitter;
    }

    public AntInstallation getSelectedAnt() {
      return myAnts.getSelectedItem();
    }

    public void setAnts(Collection<AntInstallation> antInstallations) {
      myAnts.setItems(antInstallations);
    }

    public void applyModifications() {
      if (myCurrent != null) myBinding.apply(getProperties(myCurrent));
      ArrayList<AbstractProperty> properties = new ArrayList<AbstractProperty>();
      myBinding.addAllPropertiesTo(properties);
      for (AntInstallation ant : myWorkingProperties.keySet()) {
        EditPropertyContainer container = myWorkingProperties.get(ant);
        container.apply();
      }
      myGlobalWorkingProperties.apply();
    }

    public void selectAnt(AntInstallation antInstallation) {
      myAnts.setSelection(antInstallation);
    }

    public ArrayList<AntInstallation> getAddedAnts() {
      return myAnts.getAdded();
    }

    public ArrayList<AntInstallation> getRemovedAnts() {
      return myAnts.getRemoved();
    }

    public EditPropertyContainer getProperties(AntInstallation ant) {
      EditPropertyContainer properties = myWorkingProperties.get(ant);
      if (properties != null) return properties;
      properties = new EditPropertyContainer(myGlobalWorkingProperties, ant.getProperties());

      myWorkingProperties.put(ant, properties);
      return properties;
    }

    private static class RightPanel {
      private JLabel myNameLabel;
      private JLabel myHome;
      private JTextField myName;
      private AntClasspathEditorPanel myClasspath;
      private JPanel myWholePanel;

      public RightPanel(UIPropertyBinding.Composite binding, PropertyChangeListener immediateUpdater) {
        myNameLabel.setLabelFor(myName);
        binding.addBinding(myClasspath.setClasspathProperty(AntInstallation.CLASS_PATH));
        binding.bindString(myHome, AntInstallation.HOME_DIR);
        binding.bindString(myName, AntInstallation.NAME).addChangeListener(immediateUpdater);
      }
    }
  }

  private static class NewAntFactory implements Factory<AntInstallation> {
    private final AnActionListEditor<AntInstallation> myParent;

    public NewAntFactory(AnActionListEditor<AntInstallation> parent) {
      myParent = parent;
    }

    public AntInstallation create() {
      VirtualFile[] files = FileChooser.chooseFiles(myParent, FileChooserDescriptorFactory.createSingleFolderDescriptor());
      if (files.length == 0) return null;
      VirtualFile homePath = files[0];
      try {
        final AntInstallation inst = AntInstallation.fromHome(homePath.getPresentableUrl());
        adjustName(inst);
        return inst;
      }
      catch (AntInstallation.ConfigurationException e) {
        Messages.showErrorDialog(myParent, e.getMessage(), AntBundle.message("ant.setup.dialog.title"));
        return null;
      }
    }

    private void adjustName(final AntInstallation justCreated) {
      int nameIndex = 0;
      String adjustedName = justCreated.getName();
      final ListModel model = myParent.getList().getModel();
      
      int idx = 0;
      while (idx < model.getSize()) {
        final AntInstallation inst = (AntInstallation)model.getElementAt(idx++);
        if (adjustedName.equals(inst.getName())) {
          adjustedName = justCreated.getName() + " (" + (++nameIndex) + ")";
          idx = 0; // search from beginning
        }
      }
      
      if (!adjustedName.equals(justCreated.getName())) {
        justCreated.setName(adjustedName);
      }
    }
  }

  private class MyDialog extends DialogWrapper {
    public MyDialog(final JComponent parent) {
      super(parent, true);
      setTitle(AntBundle.message("configure.ant.dialog.title"));
      init();
    }

    @Nullable
      protected JComponent createCenterPanel() {
      return myForm.getComponent();
    }

    @NonNls
      protected String getDimensionServiceKey() {
      return "antSetDialogDimensionKey";
    }

    public JComponent getPreferredFocusedComponent() {
      return myForm.getAntsList();
    }

    protected void doOKAction() {
      final Set<String> names = new HashSet<String>();
      final ListModel model = myForm.getAntsList().getModel();
      for (int idx = 0; idx  < model.getSize(); idx++) {
        final AntInstallation inst = (AntInstallation)model.getElementAt(idx);
        final String name = AntInstallation.NAME.get(myForm.getProperties(inst));
        if (names.contains(name)) {
          Messages.showErrorDialog("Duplicate ant installation name: \"" + name+ "\"", getTitle());
          return;
        }
        names.add(name);
      }
      
      super.doOKAction();
    }
  }
}
