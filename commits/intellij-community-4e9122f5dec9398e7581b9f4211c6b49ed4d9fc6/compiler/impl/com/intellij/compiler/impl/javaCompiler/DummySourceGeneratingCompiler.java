/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.SourceGeneratingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 9, 2004
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DummySourceGeneratingCompiler implements SourceGeneratingCompiler{
  public static final String MODULE_NAME = "generated";
  private final Project myProject;

  public DummySourceGeneratingCompiler(Project project) {
    myProject = project;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    final Module module = findMyModule();
    return new GenerationItem[] {
      new MyGenerationItem("aaa/p1.properties", module, false),
      new MyGenerationItem("bbb/p2.properties", module, false),
      new MyGenerationItem("bbb/ccc/p3.properties", module, false),
      new MyGenerationItem("aaa/p1.properties", module, true),
      new MyGenerationItem("bbb/p2-t.properties", module, true),
      new MyGenerationItem("bbb/ccc/p3.properties", module, true)
    };
  }

  private Module findMyModule() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      public Module compute() {
        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        for (Module module : modules) {
          if (MODULE_NAME.equals(module.getName())) {
            return module;
          }
        }
        return null;
      }
    });
  }

  public GenerationItem[] generate(CompileContext context, GenerationItem[] items, final VirtualFile outputRootDirectory) {
    final String rootPath = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return outputRootDirectory.getPath();
      }
    });
    final List<GenerationItem> success = new ArrayList<GenerationItem>();
    for (GenerationItem item1 : items) {
      try {
        GenerationItem item = item1;
        File file = new File(rootPath + File.separator + item.getPath());
        file.getParentFile().mkdirs();
        file.createNewFile();
        success.add(item);
      }
      catch (IOException e) {
      }
    }
    return success.toArray(new GenerationItem[success.size()]);
  }

  @NotNull
  public String getDescription() {
    return "Dummy Source Generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return findMyModule() != null;
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return null;
  }

  private static class MyGenerationItem implements GenerationItem {
    private final String myRelPath;
    private final Module myModule;
    private boolean myIsTestSource;

    public MyGenerationItem(String relPath, Module module, final boolean isTestSource) {
      myRelPath = relPath;
      myModule = module;
      myIsTestSource = isTestSource;
    }

    public String getPath() {
      return myRelPath;
    }

    public ValidityState getValidityState() {
      return null;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isTestSource() {
      return myIsTestSource;
    }
  }
}
