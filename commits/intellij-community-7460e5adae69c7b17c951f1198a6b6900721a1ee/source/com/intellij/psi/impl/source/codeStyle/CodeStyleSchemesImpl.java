package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;

import java.io.File;
import java.util.Collection;

/**
 * @author MYakovlev
 *         Date: Jul 16, 2002
 */
public class CodeStyleSchemesImpl extends CodeStyleSchemes implements ExportableApplicationComponent,JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl");
  private HashMap<String, CodeStyleScheme> mySchemes = new HashMap<String, CodeStyleScheme>();   // name -> scheme
  private CodeStyleScheme myCurrentScheme;

  public String CURRENT_SCHEME_NAME = "Default";
  private boolean myIsInitialized = false;

  private CodeStyleSchemesImpl() {
  }

  public String getComponentName() {
    return "CodeStyleSchemes";
  }

  public void initComponent() {
    init();
    addScheme(new CodeStyleSchemeImpl("Default", true, null));
    CodeStyleScheme current = findSchemeByName(CURRENT_SCHEME_NAME);
    if (current == null) current = getDefaultScheme();
    setCurrentScheme(current);
  }

  public void disposeComponent() {
  }

  public CodeStyleScheme[] getSchemes() {
    final Collection<CodeStyleScheme> schemes = mySchemes.values();
    return schemes.toArray(new CodeStyleScheme[schemes.size()]);
  }

  public CodeStyleScheme getCurrentScheme() {
    return myCurrentScheme;
  }

  public void setCurrentScheme(CodeStyleScheme scheme) {
    myCurrentScheme = scheme;
    CURRENT_SCHEME_NAME = scheme.getName();
  }

  public CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme) {
    String name;
    if (preferredName == null) {
      // Generate using parent name
      name = null;
      for (int i = 1; name == null; i++) {
        String currName = parentScheme.getName() + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    else {
      name = null;
      for (int i = 0; name == null; i++) {
        String currName = i == 0 ? preferredName : preferredName + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    return new CodeStyleSchemeImpl(name, false, parentScheme);
  }

  public void deleteScheme(CodeStyleScheme scheme) {
    if (scheme.isDefault()) {
      throw new IllegalArgumentException("Unable to delete default scheme!");
    }
    CodeStyleSchemeImpl currScheme = (CodeStyleSchemeImpl)getCurrentScheme();
    if (currScheme == scheme) {
      CodeStyleScheme newCurrentScheme = getDefaultScheme();
      if (newCurrentScheme == null) {
        throw new IllegalStateException("Unable to load default scheme!");
      }
      setCurrentScheme(newCurrentScheme);
    }
    mySchemes.remove(scheme.getName());
  }

  public CodeStyleScheme getDefaultScheme() {
    return findSchemeByName("Default");
  }

  public CodeStyleScheme findSchemeByName(String name) {
    return mySchemes.get(name);
  }

  public void addScheme(CodeStyleScheme scheme) {
    String name = scheme.getName();
    LOG.assertTrue(!mySchemes.containsKey(name), "Not unique scheme name");
    mySchemes.put(name, scheme);
  }

  protected void removeScheme(CodeStyleScheme scheme) {
    mySchemes.remove(scheme.getName());
  }

  public void readExternal(Element element) throws InvalidDataException {
    init();
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  private void init() {
    if (myIsInitialized) return;
    myIsInitialized = true;
    mySchemes.clear();

    File[] files = getSchemeFiles();
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      if (file.getName().toLowerCase().endsWith(".xml")) {
        try {
          addScheme(CodeStyleSchemeImpl.readScheme(file));
        }
        catch (Exception e) {
          Messages.showErrorDialog("Error reading code style scheme from " + file.getName(), "Corrupted File");
        }
      }
    }

    final CodeStyleScheme[] schemes = getSchemes();
    for (int i = 0; i < schemes.length; i++) {
      ((CodeStyleSchemeImpl)schemes[i]).init(this);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    File[] files = getSchemeFiles();
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      String fileName = file.getName().toLowerCase();
      String xmlExtension = ".xml";
      if (fileName.endsWith(xmlExtension)) {
        try {
          String fileNameWithoutExtension = fileName.substring(0, fileName.length() - xmlExtension.length());
          if (!mySchemes.containsKey(fileNameWithoutExtension)) {
            file.delete();
          }
        }
        catch (Exception e) {
          LOG.assertTrue(false, "Unable to save Code Style Settings");
        }
      }
    }

    final CodeStyleScheme[] schemes = getSchemes();
    for (int i = 0; i < schemes.length; i++) {
      CodeStyleScheme scheme = schemes[i];
      if (!scheme.isDefault()) {
        File dir = getDir(true);
        if (dir == null) break;
        ((CodeStyleSchemeImpl)scheme).save(dir);
      }
    }

    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public File[] getExportFiles() {
    return new File[]{getDir(true), PathManager.getDefaultOptionsFile()};
  }

  public String getPresentableName() {
    return "Code style schemes";
  }

  private File[] getSchemeFiles() {
    File schemesDir = getDir(true);
    if (schemesDir == null) {
      return new File[0];
    }

    File[] files = schemesDir.listFiles();
    if (files == null) {
      LOG.error("Cannot read directory: " + schemesDir.getAbsolutePath());
      return new File[0];
    }
    return files;
  }

  private static File getDir(boolean create) {
    String directoryPath = PathManager.getConfigPath() + File.separator + "codestyles";
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        Messages.showErrorDialog("Cannot save code style schemes. Directory " + directoryPath + " cannot be created.",
                                 "Cannot Save Settings");
        return null;
      }
    }
    return directory;
  }
}
