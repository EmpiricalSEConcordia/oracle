package com.intellij.packageDependencies.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;

public class TreeModelBuilder {
  private ProjectFileIndex myFileIndex;
  private PsiManager myPsiManager;
  private Project myProject;

  private static final class ScopeType {
    private ScopeType() {}

    public static ScopeType TEST = new ScopeType();
    public static ScopeType SOURCE = new ScopeType();
    public static ScopeType LIB = new ScopeType();
  }

  private boolean myShowModules;
  private boolean myGroupByScopeType;
  private boolean myFlattenPackages;
  private boolean myShowFiles;
  private boolean myShowIndividualLibs;
  private Marker myMarker;
  private boolean myAddUnmarkedFiles;
  private PackageDependenciesNode myRoot;
  private Map<ScopeType, Map<Pair<Module, PsiPackage>, PackageNode>> myModulePackageNodes = new HashMap<ScopeType, Map<Pair<Module, PsiPackage>, PackageNode>>();
  private Map<ScopeType, Map<Pair<OrderEntry, PsiPackage>, PackageNode>> myLibraryPackageNodes = new HashMap<ScopeType, Map<Pair<OrderEntry, PsiPackage>, PackageNode>>();
  private Map<ScopeType, Map<Module, ModuleNode>> myModuleNodes = new HashMap<ScopeType, Map<Module, ModuleNode>>();
  private Map<ScopeType, Map<OrderEntry, LibraryNode>> myLibraryNodes = new HashMap<ScopeType, Map<OrderEntry, LibraryNode>>();
  private int myScannedFileCount = 0;
  private int myTotalFileCount = 0;
  private int myMarkedFileCount = 0;
  private GeneralGroupNode myAllLibsNode = null;

  private GeneralGroupNode mySourceRoot = null;
  private GeneralGroupNode myTestRoot = null;
  private GeneralGroupNode myLibsRoot = null;


  private static final Icon LIB_ICON_OPEN = IconLoader.getIcon("/nodes/ppLibOpen.png");
  private static final Icon LIB_ICON_CLOSED = IconLoader.getIcon("/nodes/ppLibClosed.png");
  private static final Icon PACKAGE_CLOSED = IconLoader.getIcon("/nodes/packageClosed.png");
  private static final Icon PACKAGE_OPEN = IconLoader.getIcon("/nodes/packageOpen.png");
  private static final Icon TEST_ICON = IconLoader.getIcon("/nodes/testSourceFolder.png");
  public static final String PRODUCTION_NAME = "Production Classes";
  public static final String TEST_NAME = "Test Classes";
  public static final String LIBRARY_NAME = "Library Classes";

  private TreeModelBuilder(Project project, boolean showIndividualLibs, Marker marker) {
    myProject = project;
    DependencyUISettings settings = DependencyUISettings.getInstance();
    myShowModules = settings.UI_SHOW_MODULES;
    myGroupByScopeType = settings.UI_GROUP_BY_SCOPE_TYPE;
    myFlattenPackages = settings.UI_FLATTEN_PACKAGES;
    myShowFiles = settings.UI_SHOW_FILES;
    myShowIndividualLibs = showIndividualLibs;
    myMarker = marker;
    myAddUnmarkedFiles = !settings.UI_FILTER_LEGALS;
    myRoot = new RootNode();
    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myPsiManager = PsiManager.getInstance(project);

    createMaps(ScopeType.LIB);
    createMaps(ScopeType.SOURCE);
    createMaps(ScopeType.TEST);

    if (myGroupByScopeType) {
      mySourceRoot = new GeneralGroupNode(PRODUCTION_NAME, PACKAGE_OPEN, PACKAGE_CLOSED);
      myTestRoot = new GeneralGroupNode(TEST_NAME, TEST_ICON, TEST_ICON);
      myLibsRoot = new GeneralGroupNode(LIBRARY_NAME, LIB_ICON_OPEN, LIB_ICON_CLOSED);
      myRoot.add(mySourceRoot);
      myRoot.add(myTestRoot);
      myRoot.add(myLibsRoot);
    }
  }

  private void createMaps(ScopeType scopeType) {
    myModulePackageNodes.put(scopeType, new HashMap<Pair<Module, PsiPackage>, PackageNode>());
    myLibraryPackageNodes.put(scopeType, new HashMap<Pair<OrderEntry, PsiPackage>, PackageNode>());
    myModuleNodes.put(scopeType, new HashMap<Module, ModuleNode>());
    myLibraryNodes.put(scopeType, new HashMap<OrderEntry, LibraryNode>());
  }

  public static class TreeModel extends DefaultTreeModel {
    private int myMarkedFileCount = 0;
    private int myTotalFileCount = 0;

    public TreeModel(TreeNode root, int total, int marked) {
      super(root);
      myMarkedFileCount = marked;
      myTotalFileCount = total;
    }

    public int getMarkedFileCount() {
      return myMarkedFileCount;
    }

    public int getTotalFileCount() {
      return myTotalFileCount;
    }
  }

  public interface Marker {
    boolean isMarked(PsiFile file);
  }

  public static TreeModel createTreeModel(Project project, boolean showProgress, Set<PsiFile> files, Marker marker) {
    return new TreeModelBuilder(project, true, marker).build(files, showProgress);
  }

  public static TreeModel createTreeModel(Project project, boolean showProgress,
                                          boolean showIndividualLibs,
                                          Marker marker) {
    return new TreeModelBuilder(project, showIndividualLibs, marker).build(project, showProgress);
  }

  private VirtualFile[] getLibraryRoots(Project project) {
    Set<VirtualFile> roots = new HashSet<VirtualFile>();
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      roots.addAll(Arrays.asList(ModuleRootManager.getInstance(module).getFiles(OrderRootType.SOURCES)));
    }
    return roots.toArray(new VirtualFile[roots.size()]);
  }

  private void countFiles(Project project) {
    myFileIndex.iterateContent(new ContentIterator() {
      public boolean processFile(VirtualFile fileOrDir) {
        if (!fileOrDir.isDirectory()) {
          if (myFileIndex.isContentJavaSourceFile(fileOrDir)) {
            myTotalFileCount++;
          }
        }
        return true;
      }
    });

    VirtualFile[] roots = getLibraryRoots(project);
    for (int i = 0; i < roots.length; i++) {
      countFilesRecursively(roots[i]);
    }
  }

  private TreeModel build(final Project project, boolean showProgress) {
    countFiles(project);

    Runnable buildingRunnable = new Runnable() {
      public void run() {
        final PsiManager psiManager = PsiManager.getInstance(project);
        myFileIndex.iterateContent(new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              if (myFileIndex.isContentJavaSourceFile(fileOrDir)) {
                buildFileNode(psiManager.findFile(fileOrDir));
              }
            }
            return true;
          }
        });

        VirtualFile[] roots = getLibraryRoots(project);
        for (int i = 0; i < roots.length; i++) {
          processFilesRecursively(roots[i], psiManager);
        }
      }
    };

    if (showProgress) {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(buildingRunnable, "Scanning Packages", false, project);
    }
    else {
      buildingRunnable.run();
    }

    TreeUtil.sort(myRoot, new DependecyNodeComparator());
    return new TreeModel(myRoot, myTotalFileCount, myMarkedFileCount);
  }

  private void processFilesRecursively(VirtualFile file, PsiManager psiManager) {
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (int i = 0; i < children.length; i++) {
        processFilesRecursively(children[i], psiManager);
      }
    }
    else if (myFileIndex.isInLibrarySource(file) && myFileIndex.isJavaSourceFile(file)) {
      buildFileNode(psiManager.findFile(file));
    }
  }

  private void countFilesRecursively(VirtualFile file) {
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (int i = 0; i < children.length; i++) {
        countFilesRecursively(children[i]);
      }
    }
    else if (myFileIndex.isInLibrarySource(file) && myFileIndex.isJavaSourceFile(file)) {
      myTotalFileCount++;
    }
  }

  private TreeModel build(final Set<PsiFile> files, boolean showProgress) {
    if (files.size() == 1) {
      myShowFiles = true;
    }
    
    Runnable buildingRunnable = new Runnable() {
      public void run() {
        for (Iterator<PsiFile> iterator = files.iterator(); iterator.hasNext();) {
          buildFileNode(iterator.next());
        }
      }
    };

    if (showProgress) {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(buildingRunnable, "Scanning Packages", false, myProject);
    }
    else {
      buildingRunnable.run();
    }

    TreeUtil.sort(myRoot, new DependecyNodeComparator());
    return new TreeModel(myRoot, myTotalFileCount, myMarkedFileCount);
  }

  private void buildFileNode(PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return;
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText("Scanning packages");
      indicator.setText2(file.getVirtualFile().getPresentableUrl());
      indicator.setFraction(((double)myScannedFileCount++) / myTotalFileCount);
    }

    boolean isMarked = myMarker == null ? false : myMarker.isMarked(file);
    if (isMarked) myMarkedFileCount++;
    if (isMarked || myAddUnmarkedFiles) {
      PackageDependenciesNode dirNode = getFileParentNode((PsiJavaFile)file);

      if (myShowFiles) {
        FileNode fileNode = new FileNode(file, isMarked);
        dirNode.add(fileNode);
      }
      else {
        dirNode.addFile(file, isMarked);
      }
    }
  }

  private PackageDependenciesNode getFileParentNode(PsiJavaFile file) {
    VirtualFile vFile = file.getVirtualFile();
    PsiPackage aPackage = getFilePackage(file);
    if (myFileIndex.isInLibrarySource(vFile) || myFileIndex.isInLibraryClasses(vFile)) {
      return getLibraryDirNode(aPackage, getLibraryForFile(file));
    }
    else {
      return getModuleDirNode(aPackage, myFileIndex.getModuleForFile(vFile), getFileScopeType(vFile));
    }
  }

  private PsiPackage getFilePackage(PsiJavaFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (myFileIndex.isInLibrarySource(vFile)) {
      return myPsiManager.findPackage(myFileIndex.getPackageNameByDirectory(vFile.getParent()));
    }
    return myPsiManager.findPackage(file.getPackageName());
  }

  private ScopeType getFileScopeType(VirtualFile file) {
    if (myFileIndex.isLibraryClassFile(file) || myFileIndex.isInLibrarySource(file)) return ScopeType.LIB;
    if (myFileIndex.isInTestSourceContent(file)) return ScopeType.TEST;
    return ScopeType.SOURCE;
  }

  private OrderEntry getLibraryForFile(PsiFile file) {
    OrderEntry[] orders = myFileIndex.getOrderEntriesForFile(file.getVirtualFile());
    for (int i = 0; i < orders.length; i++) {
      OrderEntry order = orders[i];
      if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) return order;
    }
    return null;
  }

  private <T> T getMap(Map<ScopeType, T> map, ScopeType scopeType) {
    return map.get(myGroupByScopeType ? scopeType : ScopeType.SOURCE);
  }

  private PackageDependenciesNode getLibraryDirNode(PsiPackage aPackage, OrderEntry libraryOrJdk) {
    if (aPackage == null) {
      return getLibraryOrJDKNode(libraryOrJdk);
    }

    if (!myShowModules && !myGroupByScopeType) {
      return getModuleDirNode(aPackage, null, ScopeType.LIB);
    }

    Pair<OrderEntry, PsiPackage> descriptor = new Pair<OrderEntry, PsiPackage>(myShowModules ? libraryOrJdk : null, aPackage);
    PackageNode node = getMap(myLibraryPackageNodes, ScopeType.LIB).get(descriptor);
    if (node != null) return node;

    node = new PackageNode(aPackage, myFlattenPackages);
    getMap(myLibraryPackageNodes, ScopeType.LIB).put(descriptor, node);

    if (myFlattenPackages) {
      getLibraryOrJDKNode(libraryOrJdk).add(node);
    }
    else {
      getLibraryDirNode(aPackage.getParentPackage(), libraryOrJdk).add(node);
    }

    return node;
  }

  private PackageDependenciesNode getModuleDirNode(PsiPackage aPackage, Module module, ScopeType scopeType) {
    if (aPackage == null) {
      return getModuleNode(module, scopeType);
    }

    Pair<Module, PsiPackage> descriptor = new Pair<Module, PsiPackage>(myShowModules ? module : null, aPackage);
    PackageNode node = getMap(myModulePackageNodes, scopeType).get(descriptor);

    if (node != null) return node;

    node = new PackageNode(aPackage, myFlattenPackages);
    getMap(myModulePackageNodes, scopeType).put(descriptor, node);

    if (myFlattenPackages) {
      getModuleNode(module, scopeType).add(node);
    }
    else {
      getModuleDirNode(aPackage.getParentPackage(), module, scopeType).add(node);
    }

    return node;
  }

  private PackageDependenciesNode getModuleNode(Module module, ScopeType scopeType) {
    if (module == null || !myShowModules) {
      return getScopeNode(scopeType);
    }

    ModuleNode node = getMap(myModuleNodes, scopeType).get(module);
    if (node != null) return node;
    node = new ModuleNode(module);
    getMap(myModuleNodes, scopeType).put(module, node);
    getScopeNode(scopeType).add(node);

    return node;
  }

  private PackageDependenciesNode getLibraryOrJDKNode(OrderEntry libraryOrJdk) {
    if (libraryOrJdk == null || !myShowModules) {
      return getScopeNode(ScopeType.LIB);
    }

    if (!myShowIndividualLibs) {
      if (myGroupByScopeType) return getScopeNode(ScopeType.LIB);
      if (myAllLibsNode == null) {
        myAllLibsNode = new GeneralGroupNode("Libraries", LIB_ICON_OPEN, LIB_ICON_CLOSED);
        getScopeNode(ScopeType.LIB).add(myAllLibsNode);
      }
      return myAllLibsNode;
    }

    LibraryNode node = getMap(myLibraryNodes, ScopeType.LIB).get(libraryOrJdk);
    if (node != null) return node;
    node = new LibraryNode(libraryOrJdk);
    getMap(myLibraryNodes, ScopeType.LIB).put(libraryOrJdk, node);

    getScopeNode(ScopeType.LIB).add(node);
    return node;
  }


  private PackageDependenciesNode getScopeNode(ScopeType scopeType) {
    if (!myGroupByScopeType) {
      return myRoot;
    }
    else {
      if (scopeType == ScopeType.TEST) {
        return myTestRoot;
      }
      else if (scopeType == ScopeType.SOURCE) {
        return mySourceRoot;
      }
      else if (scopeType == ScopeType.LIB) {
        return myLibsRoot;
      }
    }
    return null;
  }
}