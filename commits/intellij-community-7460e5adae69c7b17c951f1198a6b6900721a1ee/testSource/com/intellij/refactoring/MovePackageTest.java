package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;

public class MovePackageTest extends MultiFileTestCase {

  public void testMoveSingle() throws Exception {
    doTest(new String[]{"pack1"}, "target");
  }

/* IMPLEMENT: soft references in JSP
  public void testJsp() throws Exception {
    doTest(new String[]{"pack1"}, "target");
  }
*/
  public void testQualifiedRef() throws Exception {
    doTest(new String[]{"package1.test"}, "package2");
  }

  public void testInsidePackage() throws Exception {
    doTest(new String[]{"a"}, "a.b");
  }

  protected String getTestRoot() {
    return "/refactoring/movePackage/";
  }

  private void doTest(final String[] packageNames, final String newPackageName) throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        MovePackageTest.this.performAction(rootDir, packageNames, newPackageName);
      }
    });
  }

  private void performAction(VirtualFile rootDir, String[] packageNames, String newPackageName) throws Exception {
    final PsiPackage[] packages = new PsiPackage[packageNames.length];
    for (int i = 0; i < packages.length; i++) {
      String packageName = packageNames[i];
      packages[i] = myPsiManager.findPackage(packageName);
      assertNotNull("Package " + packageName + " not found", packages[i]);
    }

    PsiPackage newParentPackage = myPsiManager.findPackage(newPackageName);
    assertNotNull(newParentPackage);
    final PsiDirectory[] dirs = newParentPackage.getDirectories();
    assertEquals(dirs.length, 1);

    new MoveClassesOrPackagesProcessor(myProject, packages,
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(newParentPackage), dirs[0]),
                                       true, false, false, null, null).testRun();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
