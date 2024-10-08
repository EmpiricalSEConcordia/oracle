/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Chunk;
import com.intellij.util.PathUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 29, 2004
 */
public class ModuleChunk extends Chunk<Module> {
  private final CompileContextEx myContext;
  private final Map<Module, List<VirtualFile>> myModuleToFilesMap = new THashMap<Module, List<VirtualFile>>();
  private final Map<VirtualFile, VirtualFile> myTransformedToOriginalMap = new THashMap<VirtualFile, VirtualFile>();
  private int mySourcesFilter = ALL_SOURCES;

  public ModuleChunk(CompileContextEx context, Chunk<Module> chunk, Map<Module, List<VirtualFile>> moduleToFilesMap) {
    super(chunk.getNodes());
    myContext = context;
    for (final Module module : chunk.getNodes()) {
      final List<VirtualFile> set = moduleToFilesMap.get(module);
      myModuleToFilesMap.put(module, set == null ? Collections.<VirtualFile>emptyList() : set);
    }
  }

  public static final int SOURCES = 0x1;
  public static final int TEST_SOURCES = 0x2;
  public static final int ALL_SOURCES = SOURCES | TEST_SOURCES;

  public void setSourcesFilter(int filter) {
    mySourcesFilter = filter;
  }

  public void substituteWithTransformedVersion(Module module, int fileIndex, VirtualFile transformedFile) {
    final List<VirtualFile> moduleFiles = getFilesToCompile(module);
    final VirtualFile currentFile = moduleFiles.get(fileIndex);
    moduleFiles.set(fileIndex, transformedFile);
    VirtualFile originalFile = myTransformedToOriginalMap.remove(currentFile);
    if (originalFile == null) {
      originalFile = currentFile;
    }
    myTransformedToOriginalMap.put(transformedFile, originalFile);
  }

  @NotNull
  public List<VirtualFile> getFilesToCompile(Module forModule) {
    return myModuleToFilesMap.get(forModule);
  }

  @NotNull
  public List<VirtualFile> getFilesToCompile() {
    if (getModuleCount() == 0) {
      return Collections.emptyList();
    }
    final Set<Module> modules = getNodes();

    final List<VirtualFile> filesToCompile = new ArrayList<VirtualFile>();
    for (final Module module : modules) {
      final List<VirtualFile> moduleCompilableFiles = getFilesToCompile(module);
      if (mySourcesFilter == ALL_SOURCES) {
        filesToCompile.addAll(moduleCompilableFiles);
      }
      else {
        for (final VirtualFile file : moduleCompilableFiles) {
          VirtualFile originalFile = myTransformedToOriginalMap.get(file);
          if (originalFile == null) {
            originalFile = file;
          }
          if (mySourcesFilter == TEST_SOURCES) {
            if (myContext.isInTestSourceContent(originalFile)) {
              filesToCompile.add(file);
            }
          }
          else {
            if (!myContext.isInTestSourceContent(originalFile)) {
              filesToCompile.add(file);
            }
          }
        }
      }
    }
    return filesToCompile;
  }

  /**
   * @return the jdk. Assumes that the jdk is the same for all modules
   */
  public Sdk getJdk() {
    final Module module = getNodes().iterator().next();
    return ModuleRootManager.getInstance(module).getSdk();
  }

  public VirtualFile[] getSourceRoots() {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
      public VirtualFile[] compute() {
        return filterRoots(getAllSourceRoots(), getNodes().iterator().next().getProject());
      }
    });
  }

  public VirtualFile[] getSourceRoots(final Module module) {
    if (!getNodes().contains(module)) {
      return VirtualFile.EMPTY_ARRAY;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
      public VirtualFile[] compute() {
        return filterRoots(myContext.getSourceRoots(module), module.getProject());
      }
    });
  }

  private VirtualFile[] filterRoots(VirtualFile[] roots, Project project) {
    final List<VirtualFile> filteredRoots = new ArrayList<VirtualFile>(roots.length);
    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    for (final VirtualFile root : roots) {
      if (mySourcesFilter != ALL_SOURCES) {
        if (myContext.isInTestSourceContent(root)) {
          if ((mySourcesFilter & TEST_SOURCES) == 0) {
            continue;
          }
        }
        else {
          if ((mySourcesFilter & SOURCES) == 0) {
            continue;
          }
        }
      }
      if (compilerConfiguration.isExcludedFromCompilation(root)) {
        continue;
      }
      filteredRoots.add(root);
    }
    return filteredRoots.toArray(new VirtualFile[filteredRoots.size()]);
  }

  private VirtualFile[] getAllSourceRoots() {
    final Set<Module> modules = getNodes();
    Set<VirtualFile> roots = new HashSet<VirtualFile>();
    for (final Module module : modules) {
      roots.addAll(Arrays.asList(myContext.getSourceRoots(module)));
    }
    return roots.toArray(new VirtualFile[roots.size()]);
  }

  public String getCompilationClasspath() {
    final OrderedSet<VirtualFile> cpFiles = getCompilationClasspathFiles();
    return convertToStringPath(cpFiles);

  }

  public OrderedSet<VirtualFile> getCompilationClasspathFiles() {
    final Set<Module> modules = getNodes();

    final OrderedSet<VirtualFile> cpFiles = new OrderedSet<VirtualFile>(TObjectHashingStrategy.CANONICAL);
    for (final Module module : modules) {
      final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      boolean skip = true;
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          skip = false;
          continue;
        }
        if (skip) {
          continue;
        }
        if ((mySourcesFilter & TEST_SOURCES) == 0) {
          cpFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES)));
        }
        else {
          cpFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)));
        }
      }
    }
    return cpFiles;
  }

  public String getCompilationBootClasspath() {
    final Set<Module> modules = getNodes();
    final OrderedSet<VirtualFile> cpFiles = new OrderedSet<VirtualFile>(TObjectHashingStrategy.CANONICAL);
    final OrderedSet<VirtualFile> jdkFiles = new OrderedSet<VirtualFile>(TObjectHashingStrategy.CANONICAL);
    for (final Module module : modules) {
      final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          jdkFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.CLASSES)));
          break;
        }
        else {
          if ((mySourcesFilter & TEST_SOURCES) == 0) {
            cpFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES)));
          }
          else {
            cpFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)));
          }
        }
      }
    }
    cpFiles.addAll(jdkFiles);
    return convertToStringPath(cpFiles);
  }

  private String convertToStringPath(final OrderedSet<VirtualFile> cpFiles) {
    final StringBuilder classpathBuffer = StringBuilderSpinAllocator.alloc();
    try {
      for (final VirtualFile file : cpFiles) {
        final String path = PathUtil.getLocalPath(file);
        //if (file.getFileSystem() instanceof LocalFileSystem && file.isDirectory()) {
        //  path = tryZipFor(file.getPath());
        //}
        //else {
        //  path = PathUtil.getLocalPath(file);
        //}

        if (path == null) {
          continue;
        }
        if (classpathBuffer.length() > 0) {
          classpathBuffer.append(File.pathSeparatorChar);
        }
        classpathBuffer.append(path);
      }

      return classpathBuffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(classpathBuffer);
    }
  }

  //private String tryZipFor(String outputDir) {
  //  final File zip = CompilerPathsEx.getZippedOutputPath(myContext.getProject(), outputDir);
  //  if (zip.exists()) {
  //    try {
  //      myContext.commitZip(outputDir); // flush unsaved data if any
  //    }
  //    catch (IOException e) {
  //      LOG.info(e);
  //    }
  //    return zip.getPath();
  //  }
  //  return outputDir;
  //}

  public int getModuleCount() {
    return getNodes().size();
  }

  public Module[] getModules() {
    final Set<Module> nodes = getNodes();
    return nodes.toArray(new Module[nodes.size()]);
  }

  public String getSourcePath() {
    if (getModuleCount() == 0) {
      return "";
    }
    final VirtualFile[] filteredRoots = getSourceRoots();
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (VirtualFile root : filteredRoots) {
            if (buffer.length() > 0) {
              buffer.append(File.pathSeparatorChar);
            }
            buffer.append(root.getPath().replace('/', File.separatorChar));
          }
        }
      });
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  //the check for equal language levels is done elsewhere
  public LanguageLevel getLanguageLevel() {
    return LanguageLevelUtil.getEffectiveLanguageLevel(getModules()[0]);
  }
}
