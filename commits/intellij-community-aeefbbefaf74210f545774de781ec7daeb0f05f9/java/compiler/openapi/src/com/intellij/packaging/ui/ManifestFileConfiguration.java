package com.intellij.packaging.ui;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class ManifestFileConfiguration {
  private List<String> myClasspath = new ArrayList<String>();
  private String myMainClass;
  private String myManifestFilePath;

  public ManifestFileConfiguration(ManifestFileConfiguration configuration) {
    myClasspath = new ArrayList<String>(configuration.getClasspath());
    myMainClass = configuration.getMainClass();
    myManifestFilePath = configuration.getManifestFilePath();
  }

  public ManifestFileConfiguration(List<String> classpath, String mainClass, String manifestFilePath) {
    myClasspath = classpath;
    myMainClass = mainClass;
    myManifestFilePath = manifestFilePath;
  }

  public List<String> getClasspath() {
    return myClasspath;
  }

  public void setClasspath(List<String> classpath) {
    myClasspath = classpath;
  }

  public String getMainClass() {
    return myMainClass;
  }

  public void setMainClass(String mainClass) {
    myMainClass = mainClass;
  }

  public String getManifestFilePath() {
    return myManifestFilePath;
  }

  public void setManifestFilePath(String manifestFilePath) {
    myManifestFilePath = manifestFilePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ManifestFileConfiguration)) return false;

    ManifestFileConfiguration that = (ManifestFileConfiguration)o;

    if (!myClasspath.equals(that.myClasspath)) return false;
    if (myMainClass != null ? !myMainClass.equals(that.myMainClass) : that.myMainClass != null) return false;
    if (myManifestFilePath != null ? !myManifestFilePath.equals(that.myManifestFilePath) : that.myManifestFilePath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }

  public void addToClasspath(List<String> classpath) {
    for (String path : classpath) {
      if (!myClasspath.contains(path)) {
        myClasspath.add(path);
      }
    }
  }
}
