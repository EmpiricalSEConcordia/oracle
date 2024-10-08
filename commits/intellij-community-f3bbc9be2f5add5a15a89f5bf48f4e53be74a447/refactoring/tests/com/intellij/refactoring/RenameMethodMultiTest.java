package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameProcessor;

/**
 * @author dsl
 */
public class RenameMethodMultiTest extends MultiFileTestCase {
  protected String getTestRoot() {
    return "/refactoring/renameMethod/multi/";
  }

  public void testStaticImport1() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport2() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport3() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport4() throws Exception {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }



  private void doTest(final String className, final String methodSignature, final String newName) throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final JavaPsiFacade manager = getJavaFacade();
        final PsiClass aClass = manager.findClass(className, GlobalSearchScope.moduleScope(myModule));
        assertNotNull(aClass);
        final PsiMethod methodBySignature = aClass.findMethodBySignature(manager.getElementFactory().createMethodFromText(
                  methodSignature + "{}", null), false);
        assertNotNull(methodBySignature);
        final RenameProcessor renameProcessor = new RenameProcessor(myProject, methodBySignature, newName, false, false);
        renameProcessor.run();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

}
