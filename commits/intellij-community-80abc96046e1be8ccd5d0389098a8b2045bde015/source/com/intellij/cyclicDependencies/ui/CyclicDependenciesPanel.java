package com.intellij.cyclicDependencies.ui;

import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.cyclicDependencies.actions.CyclicDependenciesHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.packageDependencies.ui.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesPanel extends JPanel {
  private static final HashSet<PsiFile> EMPTY_FILE_SET = new HashSet<PsiFile>(0);

  private HashMap<PsiPackage, Set<List<PsiPackage>>> myDependencies;
  private MyTree myLeftTree = new MyTree();
  private MyTree myRightTree = new MyTree();
  private UsagesPanel myUsagesPanel;

  private TreeExpantionMonitor myRightTreeExpantionMonitor;
  private TreeExpantionMonitor myLeftTreeExpantionMonitor;

  private Project myProject;
  private CyclicDependenciesBuilder myBuilder;
  private Content myContent;
  private DependenciesPanel.DependencyPanelSettings mySettings = new DependenciesPanel.DependencyPanelSettings();
  public static final String DEFAULT_PACKAGE_ABBREVIATION = "<default package>";


  public CyclicDependenciesPanel(Project project, final CyclicDependenciesBuilder builder) {
    super(new BorderLayout());
    myDependencies = builder.getCyclicDependencies();
    myBuilder = builder;
    myProject = project;
    myUsagesPanel =
    new UsagesPanel(myProject, builder.getForwardBuilder());

    mySettings.UI_SHOW_MODULES = false; //exist without modules - and doesn't with

    Splitter treeSplitter = new Splitter();
    treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myLeftTree));
    treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myRightTree));

    Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(treeSplitter);
    splitter.setSecondComponent(myUsagesPanel);
    add(splitter, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.NORTH);

    myRightTreeExpantionMonitor = TreeExpantionMonitor.install(myRightTree, myProject);
    myLeftTreeExpantionMonitor = TreeExpantionMonitor.install(myLeftTree, myProject);

    updateLeftTreeModel();
    updateRightTreeModel();

    myLeftTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateRightTreeModel();
        myUsagesPanel.setToInitialPosition();
      }
    });

    myRightTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            Set<PsiFile> searchIn = getSelectedScope(myRightTree);
            final PackageNode selectedPackageNode = getSelectedPackage(myRightTree);
            if (selectedPackageNode == null) {
              return;
            }
            final PackageDependenciesNode nextPackageNode = getNextPackageNode(selectedPackageNode);
            Set<PsiFile> searchFor = new HashSet<PsiFile>();
            Set<PackageNode> packNodes = new HashSet<PackageNode>();
            getPackageNodesHierarchy(selectedPackageNode, packNodes);
            for (Iterator<PackageNode> iterator = packNodes.iterator(); iterator.hasNext();) {
              PackageNode packageNode = iterator.next();
              searchFor.addAll(myBuilder.getDependentFilesInPackage((PsiPackage)packageNode.getPsiElement(), ((PsiPackage)nextPackageNode.getPsiElement())));
            }
            if (searchIn.isEmpty() || searchFor.isEmpty()) {
              myUsagesPanel.setToInitialPosition();
            }
            else {
              myBuilder.setRootNodeNameInUsageView("Usages of package \'" + ((PsiPackage)nextPackageNode.getPsiElement()).getQualifiedName() + "\' in package \'" + ((PsiPackage)selectedPackageNode.getPsiElement()).getQualifiedName() + "\'");
              myUsagesPanel.findUsages(searchIn, searchFor);
            }
          }
        });
      }
    });

    initTree(myLeftTree);
    initTree(myRightTree);

    mySettings.UI_FILTER_LEGALS = false;
    mySettings.UI_FLATTEN_PACKAGES = false;

    TreeUtil.selectFirstNode(myLeftTree);
  }

  private void getPackageNodesHierarchy(PackageNode node, Set<PackageNode> result){
    result.add(node);
    for (int i = 0; i < node.getChildCount(); i++){
      final TreeNode child = node.getChildAt(i);
      if (child instanceof PackageNode){
        final PackageNode packNode = (PackageNode)child;
        if (!result.contains(packNode)){
          getPackageNodesHierarchy(packNode, result);
        }
      }
    }
  }

  @Nullable
  private PackageDependenciesNode getNextPackageNode(DefaultMutableTreeNode node) {
    DefaultMutableTreeNode child = node;
    while (node != null) {
      if (node instanceof CycleNode) {
        final TreeNode packageDependenciesNode = child.getNextSibling() != null
                                                 ? child.getNextSibling()
                                                 : node.getChildAt(0);
        if (packageDependenciesNode instanceof PackageNode){
          return (PackageNode)packageDependenciesNode;
        }
        if (packageDependenciesNode instanceof ModuleNode){
          return (PackageNode)packageDependenciesNode.getChildAt(0);
        }
      }
      child = node;
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return null;
  }

  private PackageDependenciesNode hideEmptyMiddlePackages(PackageDependenciesNode node, StringBuffer result){
    if (node.getChildCount() == 0 || node.getChildCount() > 1 || (node.getChildCount() == 1 && node.getChildAt(0) instanceof FileNode)){
      result.append((result.length() != 0 ? ".":"") + (node.toString().equals(DEFAULT_PACKAGE_ABBREVIATION) ? "" : node.toString()));//toString()
    } else {
      if (node.getChildCount() == 1){
        PackageDependenciesNode child = (PackageDependenciesNode)node.getChildAt(0);
        if (!(node instanceof PackageNode)){  //e.g. modules node
          node.removeAllChildren();
          child = hideEmptyMiddlePackages(child, result);
          node.add(child);
        } else {
          if (child instanceof PackageNode){
            node.removeAllChildren();
            result.append((result.length() != 0 ? ".":"") + (node.toString().equals(DEFAULT_PACKAGE_ABBREVIATION) ? "" : node.toString()));
            node = hideEmptyMiddlePackages(child, result);
            ((PackageNode)node).setPackageName(result.toString());//toString()
          }
        }
      }
    }
    return node;
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseAction());
    group.add(new RerunAction(this));
    group.add(new ShowFilesAction());
    group.add(new GroupByScopeTypeAction());
    group.add(new HelpAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  private void rebuild() {
    updateLeftTreeModel();
    updateRightTreeModel();
  }

  private void initTree(final MyTree tree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.putClientProperty("JTree.lineStyle", "Angled");

    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    new TreeSpeedSearch(tree);

    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(), ActionManager.getInstance());


  }

  private void updateLeftTreeModel() {
    final Set<PsiPackage> psiPackages = myDependencies.keySet();
    final Set<PsiFile> psiFiles = new HashSet<PsiFile>();
    for (Iterator<PsiPackage> iterator = psiPackages.iterator(); iterator.hasNext();) {
      PsiPackage psiPackage = iterator.next();
      psiFiles.addAll(getPackageFiles(psiPackage));
    }
    boolean showFiles = mySettings.UI_SHOW_FILES; //do not show files in the left tree
    mySettings.UI_FLATTEN_PACKAGES = true;
    mySettings.UI_SHOW_FILES = false;
    myLeftTreeExpantionMonitor.freeze();
    myLeftTree.setModel(TreeModelBuilder.createTreeModel(myProject, false, psiFiles, new TreeModelBuilder.Marker() {
      public boolean isMarked(PsiFile file) {
        return false;
      }
    }, mySettings));
    myLeftTreeExpantionMonitor.restore();
    expandFirstLevel(myLeftTree);
    mySettings.UI_SHOW_FILES = showFiles;
    mySettings.UI_FLATTEN_PACKAGES = false;
  }

  private ActionGroup createTreePopupActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));
    return group;
  }

  private void updateRightTreeModel() {
    PackageDependenciesNode root = new RootNode();
    final PackageNode packageNode = getSelectedPackage(myLeftTree);
    if (packageNode != null) {
      boolean group = mySettings.UI_GROUP_BY_SCOPE_TYPE;
      mySettings.UI_GROUP_BY_SCOPE_TYPE = false;
      final PsiPackage aPackage = (PsiPackage)packageNode.getPsiElement();
      final Set<List<PsiPackage>> cyclesOfPackages = myDependencies.get(aPackage);
      for (Iterator<List<PsiPackage>> iterator = cyclesOfPackages.iterator(); iterator.hasNext();) {
        List<PsiPackage> packCycle = iterator.next();
        PackageDependenciesNode[] nodes = new PackageDependenciesNode[packCycle.size()];
        for (int i = packCycle.size() - 1; i >=0; i--) {
          final PsiPackage psiPackage = packCycle.get(i);
          PsiPackage nextPackage = packCycle.get(i == 0 ? packCycle.size() - 1 : i - 1);
          PsiPackage prevPackage = packCycle.get(i == packCycle.size() - 1 ? 0 : i + 1);
          final Set<PsiFile> dependentFilesInPackage = myBuilder.getDependentFilesInPackage(prevPackage, psiPackage, nextPackage);

          final PackageDependenciesNode pack = (PackageDependenciesNode)TreeModelBuilder.createTreeModel(myProject, false, dependentFilesInPackage, new TreeModelBuilder.Marker() {
              public boolean isMarked(PsiFile file) {
                return false;
              }
            }, mySettings).getRoot();
          nodes[i] = hideEmptyMiddlePackages((PackageDependenciesNode)pack.getChildAt(0), new StringBuffer());
        }

        PackageDependenciesNode cycleNode = new CycleNode();
        for (int i = 0; i < nodes.length; i++) {
          nodes[i].setEquals(true);
          cycleNode.insert(nodes[i], 0);
        }
        root.add(cycleNode);
      }
      mySettings.UI_GROUP_BY_SCOPE_TYPE = group;
    }
    myRightTreeExpantionMonitor.freeze();
    myRightTree.setModel(new TreeModelBuilder.TreeModel(root, -1, -1));
    myRightTreeExpantionMonitor.restore();
    expandFirstLevel(myRightTree);
  }

  private HashSet<PsiFile> getPackageFiles(final PsiPackage psiPackage) {
    final HashSet<PsiFile> psiFiles = new HashSet<PsiFile>();
    final PsiClass[] classes = psiPackage.getClasses();
    for (int i = 0; i < classes.length; i++) {
      PsiClass aClass = classes[i];
      final PsiFile file = aClass.getContainingFile();
      if (myBuilder.getScope().contains(file)){
        psiFiles.add(file);
      }
    }
    return psiFiles;
  }

  private static void expandFirstLevel(Tree tree) {
    PackageDependenciesNode root = (PackageDependenciesNode)tree.getModel().getRoot();
    int count = root.getChildCount();
    if (count < 10) {
      for (int i = 0; i < count; i++) {
        PackageDependenciesNode child = (PackageDependenciesNode)root.getChildAt(i);
        expandNodeIfNotTooWide(tree, child);
      }
    }
  }

  private static void expandNodeIfNotTooWide(Tree tree, PackageDependenciesNode node) {
    int count = node.getChildCount();
    if (count > 5) return;
    tree.expandPath(new TreePath(node.getPath()));
  }

  @Nullable
  private PackageNode getSelectedPackage(final Tree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length != 1) return null;
    PackageDependenciesNode node = (PackageDependenciesNode)paths[0].getLastPathComponent();
    if (node.isRoot()) return null;
    if (node instanceof PackageNode) {
      return (PackageNode)node;
    }
    if (node instanceof FileNode) {
      return (PackageNode)node.getParent();
    }
    if (node instanceof ModuleNode){
      return (PackageNode)node.getChildAt(0);
    }
    return null;
  }

  private Set<PsiFile> getSelectedScope(final Tree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length != 1) return EMPTY_FILE_SET;
    PackageDependenciesNode node = (PackageDependenciesNode)paths[0].getLastPathComponent();
    if (node.isRoot()) return EMPTY_FILE_SET;
    Set<PsiFile> result = new HashSet<PsiFile>();
    node.fillFiles(result, true);
    return result;
  }

  public void setContent(Content content) {
    myContent = content;
  }

  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      PackageDependenciesNode node;
      if (value instanceof PackageDependenciesNode){
        node = (PackageDependenciesNode)value;
      } else {
        node = (PackageDependenciesNode)((DefaultMutableTreeNode)value).getUserObject(); //cycle node children
      }
      if (expanded) {
        setIcon(node.getOpenIcon());
      }
      else {
        setIcon(node.getClosedIcon());
      }
      append(node.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private final class CloseAction extends AnAction {
    public CloseAction() {
      super("Close", "Close Dependency Viewer", IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      myUsagesPanel.dispose();
      ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
    }
  }

  private final class ShowFilesAction extends ToggleAction {
    ShowFilesAction() {
      super("Show Files", "Show/Hide Files", IconLoader.getIcon("/fileTypes/java.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_SHOW_FILES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_FILES = flag;
      mySettings.UI_SHOW_FILES = flag;
      rebuild();
    }
  }


  private final class GroupByScopeTypeAction extends ToggleAction {
    GroupByScopeTypeAction() {
      super("Group by Scope Type", "Group by Scope Type (production, test, libraries)", IconLoader.getIcon("/nodes/testSourceFolder.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_GROUP_BY_SCOPE_TYPE;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
      mySettings.UI_GROUP_BY_SCOPE_TYPE = flag;
      rebuild();
    }
  }

  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super("Rerun", "Rerun Dependency Analysis", IconLoader.getIcon("/actions/refreshUsages.png"));
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myBuilder.getScope().isValid());
    }

    public void actionPerformed(AnActionEvent e) {
      ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          new CyclicDependenciesHandler(myProject, myBuilder.getScope()).analyze();
        }
      });
    }
  }

  private static class HelpAction extends AnAction {
    private HelpAction() {
      super("Help", null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent event) {
      HelpManager.getInstance().invokeHelp("editing.analyzeDependencies");//todo
    }
  }

  private static class MyTree extends Tree implements DataProvider {
    public Object getData(String dataId) {
      PackageDependenciesNode node = getSelectedNode();
      if (DataConstants.NAVIGATABLE.equals(dataId)) {
        return node;
      }
      return null;
    }

    public PackageDependenciesNode getSelectedNode() {
      TreePath[] paths = getSelectionPaths();
      if (paths == null || paths.length != 1) return null;
      final Object lastPathComponent = paths[0].getLastPathComponent();
      if (lastPathComponent instanceof PackageDependenciesNode) {
        return (PackageDependenciesNode)lastPathComponent;
      } else {
        return (PackageDependenciesNode)((DefaultMutableTreeNode)lastPathComponent).getUserObject();
      }
    }
  }
}
