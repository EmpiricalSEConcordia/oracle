package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.DependencyProcessor;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfigurable;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;


public class CompilerAPICompiler implements BackendCompiler {
  private final Project myProject;
  private static final Set<FileType> COMPILABLE_TYPES = Collections.<FileType>singleton(StdFileTypes.JAVA);

  public CompilerAPICompiler(Project project) {
    myProject = project;
  }

  public DependencyProcessor getDependencyProcessor() {
    return null;
  }

  public boolean checkCompiler(final CompileScope scope) {
    final Module[] modules = scope.getAffectedModules();
    final Set<Sdk> checkedJdks = new HashSet<Sdk>();
    for (final Module module : modules) {
      final Sdk jdk  = ModuleRootManager.getInstance(module).getSdk();
      if (jdk == null) {
        continue;
      }
      checkedJdks.add(jdk);
    }
    Sdk projectJdk = ProjectRootManager.getInstance(myProject).getProjectJdk();
    if (projectJdk != null) checkedJdks.add(projectJdk);

    for (Sdk sdk : checkedJdks) {
      String versionString = sdk.getVersionString();
      if (sdk.getSdkType() instanceof JavaSdk && !CompilerUtil.isOfVersion(versionString, "1.6") && !CompilerUtil.isOfVersion(versionString, "1.7")) {
        Messages.showErrorDialog(myProject, "Compiler API requires JDK version 6 or later: "+ versionString, "Incompatible JDK");
        return false;
      }
    }
    return true;
  }

  @NotNull
  @NonNls
  // used for externalization
  public String getId() {
    return "compAPI";
  }

  @NotNull
  public String getPresentableName() {
    return "Javac in-process (Java6 only)";
  }

  @NotNull
  public Configurable createConfigurable() {
    return new JavacConfigurable(CompilerAPISettings.getInstance(myProject));
  }

  @NotNull
  public Set<FileType> getCompilableFileTypes() {
    return COMPILABLE_TYPES;
  }

  @Nullable
  public OutputParser createErrorParser(@NotNull final String outputDir, final Process process) {
    return new OutputParser() {
      public boolean processMessageLine(Callback callback) {
        return ((MyProcess)process).myCompAPIDriver.processAll(callback);
      }
    };
  }

  @Nullable
  public OutputParser createOutputParser(@NotNull final String outputDir) {
    return null;
  }

  public void compileFinished() {
  }

  @NotNull
  public Process launchProcess(@NotNull final ModuleChunk chunk, @NotNull final String outputDir, @NotNull final CompileContext compileContext) throws IOException {
    final IOException[] ex = {null};
    @NonNls final List<String> commandLine = ApplicationManager.getApplication().runReadAction(new Computable<List<String>>() {
      public List<String> compute() {
        try {
          List<String> commandLine = new ArrayList<String>();
          JavacSettings javacSettings = CompilerAPISettings.getInstance(myProject);
          final List<String> additionalOptions =
            JavacCompiler.addAdditionalSettings(commandLine, javacSettings, false, false, false, false, false);

          JavacCompiler.addCommandLineOptions(chunk, commandLine, outputDir, chunk.getJdk(), false,false, null, false, false);
          commandLine.addAll(additionalOptions);
          return commandLine;
        }
        catch (IOException e) {
          ex[0] = e;
        }
        return null;
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    return new MyProcess(commandLine, chunk, outputDir, compileContext);
  }

  private static void compile(List<String> commandLine, ModuleChunk chunk, String outputDir, CompAPIDriver myCompAPIDriver) {
    List<VirtualFile> filesToCompile = chunk.getFilesToCompile();
    List<File> paths = new ArrayList<File>(filesToCompile.size());
    for (VirtualFile file : filesToCompile) {
      paths.add(new File(file.getPresentableUrl()));
    }
    myCompAPIDriver.compile(commandLine, paths, outputDir);
  }

  private static class MyProcess extends Process {
    private final List<String> myCommandLine;
    private final ModuleChunk myChunk;
    private final String myOutputDir;
    private final CompileContext myCompileContext;
    private final CompAPIDriver myCompAPIDriver = new CompAPIDriver();

    private MyProcess(List<String> commandLine, ModuleChunk chunk, String outputDir, CompileContext compileContext) {
      myCommandLine = commandLine;
      myChunk = chunk;
      myOutputDir = outputDir;
      myCompileContext = compileContext;
    }

    public OutputStream getOutputStream() {
      throw new UnsupportedOperationException();
    }

    public InputStream getInputStream() {
      return null;
    }

    public InputStream getErrorStream() {
      return null;
    }

    public void destroy() {
      myCompAPIDriver.finish();
    }

    private int myExitCode;
    public int waitFor() {
      try {
        myCommandLine.remove("-verbose");
        compile(myCommandLine, myChunk, myOutputDir, myCompAPIDriver);
        myExitCode = 0;
        return myExitCode;
      }
      catch (Exception e) {
        myCompileContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
        myExitCode = -1;
        return -1;
      }
    }

    public int exitValue() {
      return myExitCode;
    }

    @Override
    public String toString() {
      return myChunk.toString();
    }
  }
}