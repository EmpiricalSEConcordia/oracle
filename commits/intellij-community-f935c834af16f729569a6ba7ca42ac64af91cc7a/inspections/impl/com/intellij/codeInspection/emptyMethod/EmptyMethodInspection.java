package com.intellij.codeInspection.emptyMethod;

import com.intellij.ExtensionPoints;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class EmptyMethodInspection extends GlobalInspectionTool {
  private static final String DISPLAY_NAME = InspectionsBundle.message("inspection.empty.method.display.name");
  @NonNls private static final String SHORT_NAME = "EmptyMethod";

  private final BidirectionalMap<Boolean, QuickFix> myQuickFixes = new BidirectionalMap<Boolean, QuickFix>();

  private final JDOMExternalizableStringList EXCLUDE_ANNOS = new JDOMExternalizableStringList();
  @NonNls private static final String QUICK_FIX_NAME = InspectionsBundle.message("inspection.empty.method.delete.quickfix");
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.emptyMethod.EmptyMethodInspection");

  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                AnalysisScope scope,
                                                InspectionManager manager,
                                                GlobalInspectionContext globalContext,
                                                ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (!isBodyEmpty(refMethod)) return null;
      if (refMethod.isConstructor()) return null;
      if (refMethod.isSyntheticJSP()) return null;

      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        if (checkElement(refSuper, scope, manager, globalContext, processor) != null) return null;
      }

      String message = null;
      boolean needToDeleteHierarchy = false;
      if (refMethod.isOnlyCallsSuper() && !refMethod.isFinal()) {
        RefMethod refSuper = findSuperWithBody(refMethod);
        final RefUtil refUtil = RefUtil.getInstance();
        if (refSuper != null && Comparing.strEqual(refMethod.getAccessModifier(), refSuper.getAccessModifier())){
          if (Comparing.strEqual(refSuper.getAccessModifier(), PsiModifier.PROTECTED) //protected modificator gives access to method in another package
              && !Comparing.strEqual(refUtil.getPackageName(refSuper), refUtil.getPackageName(refMethod))) return null;
        }
        if (refSuper == null || refUtil.compareAccess(refMethod.getAccessModifier(), refSuper.getAccessModifier()) <= 0) {
          message = InspectionsBundle.message("inspection.empty.method.problem.descriptor");
        }
      }
      else if (refMethod.hasBody() && hasEmptySuperImplementation(refMethod)) {

        message = InspectionsBundle.message("inspection.empty.method.problem.descriptor1");
      }
      else if (areAllImplementationsEmpty(refMethod)) {
        if (refMethod.hasBody()) {
          if (refMethod.getDerivedMethods().isEmpty()) {
            if (refMethod.getSuperMethods().isEmpty()) {
              message = InspectionsBundle.message("inspection.empty.method.problem.descriptor2");
            }
          }
          else {
            needToDeleteHierarchy = true;
            message = InspectionsBundle.message("inspection.empty.method.problem.descriptor3");
          }
        }
        else {
          if (!refMethod.getDerivedMethods().isEmpty()) {
            needToDeleteHierarchy = true;
            message = InspectionsBundle.message("inspection.empty.method.problem.descriptor4");
          }
        }
      }

      if (message != null) {
        final ArrayList<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
        fixes.add(getFix(processor, needToDeleteHierarchy));
        SpecialAnnotationsUtil.createAddToSpecialAnnotationFixes(refMethod.getElement(), new Processor<String>() {
          public boolean process(final String qualifiedName) {
            fixes.add(SpecialAnnotationsUtil.createAddToSpecialAnnotationsListQuickFix(
              QuickFixBundle.message("fix.add.special.annotation.text", qualifiedName),
              QuickFixBundle.message("fix.add.special.annotation.family"),
              EXCLUDE_ANNOS, qualifiedName, refMethod.getElement()));
            return true;
          }
        });

        final ProblemDescriptor descriptor = manager.createProblemDescriptor(refMethod.getElement().getNavigationElement(), message,
                                                                             fixes.toArray(new LocalQuickFix[fixes.size()]),
                                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        return new ProblemDescriptor[]{descriptor};
      }
    }

    return null;
  }

  private boolean isBodyEmpty(final RefMethod refMethod) {
    if (!refMethod.isBodyEmpty()) {
      return false;
    }
    if (AnnotationUtil.isAnnotated(refMethod.getElement(), EXCLUDE_ANNOS)) {
      return false;
    }
    for (final Object extension : Extensions.getExtensions(ExtensionPoints.EMPTY_METHOD_TOOL)) {
      if (((Condition<RefMethod>) extension).value(refMethod)) {
        return false;
      }
    }

    return true;
  }

  @Nullable
  private static RefMethod findSuperWithBody(RefMethod refMethod) {
    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (refSuper.hasBody()) return refSuper;
    }
    return null;
  }

  private boolean areAllImplementationsEmpty(RefMethod refMethod) {
    if (refMethod.hasBody() && !isBodyEmpty(refMethod)) return false;

    for (RefMethod refDerived : refMethod.getDerivedMethods()) {
      if (!areAllImplementationsEmpty(refDerived)) return false;
    }

    return true;
  }

  private boolean hasEmptySuperImplementation(RefMethod refMethod) {
    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (refSuper.hasBody() && isBodyEmpty(refSuper)) return true;
    }

    return false;
  }


  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefElement && problemDescriptionsProcessor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefVisitor() {
            @Override public void visitMethod(final RefMethod refMethod) {
              globalContext.enqueueDerivedMethodsProcessor(refMethod, new GlobalInspectionContextImpl.DerivedMethodsProcessor() {
                public boolean process(PsiMethod derivedMethod) {
                  PsiCodeBlock body = derivedMethod.getBody();
                  if (body == null) return true;
                  if (body.getStatements().length == 0) return true;
                  if (RefUtil.getInstance().isMethodOnlyCallsSuper(derivedMethod)) return true;

                  problemDescriptionsProcessor.ignoreElement(refMethod);
                  return false;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }


  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private LocalQuickFix getFix(final ProblemDescriptionsProcessor processor, final boolean needToDeleteHierarchy) {
    QuickFix fix = myQuickFixes.get(needToDeleteHierarchy);
    if (fix == null) {
      fix = new DeleteMethodQuickFix(processor, needToDeleteHierarchy);
      myQuickFixes.put(needToDeleteHierarchy, fix);
      return (LocalQuickFix)fix;
    }
    return (LocalQuickFix)fix;
  }

  public String getHint(final QuickFix fix) {
    final List<Boolean> list = myQuickFixes.getKeysByValue(fix);
    if (list != null) {
      LOG.assertTrue(list.size() == 1);
      return String.valueOf(list.get(0));
    }
    return null;
  }

  @Nullable
  public LocalQuickFix getQuickFix(final String hint) {
    return new DeleteMethodIntention(hint);
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel listPanel = SpecialAnnotationsUtil
      .createSpecialAnnotationsListControl(EXCLUDE_ANNOS, InspectionsBundle.message("special.annotations.annotations.list"));

    final JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(listPanel, BorderLayout.NORTH);
    return panel;
  }

  private class DeleteMethodIntention implements LocalQuickFix {
    private final String myHint;

    public DeleteMethodIntention(final String hint) {
      myHint = hint;
    }

    @NotNull
    public String getName() {
      return QUICK_FIX_NAME;
    }

    @NotNull
    public String getFamilyName() {
      return QUICK_FIX_NAME;
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class, false);
      if (psiMethod != null) {
        final List<PsiElement> psiElements = new ArrayList<PsiElement>();
        psiElements.add(psiMethod);
        if (Boolean.valueOf(myHint).booleanValue()) {
          final Query<Pair<PsiMethod, PsiMethod>> query = AllOverridingMethodsSearch.search(psiMethod.getContainingClass());
          query.forEach(new Processor<Pair<PsiMethod, PsiMethod>>() {
            public boolean process(final Pair<PsiMethod, PsiMethod> pair) {
              if (pair.first == psiMethod) {
                psiElements.add(pair.second);
              }
              return true;
            }
          });
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            SafeDeleteHandler.invoke(project, psiElements.toArray(new PsiElement[psiElements.size()]), false);
          }
        });
      }
    }
  }


  private class DeleteMethodQuickFix implements LocalQuickFix {
    private final ProblemDescriptionsProcessor myProcessor;
    private final boolean myNeedToDEleteHierarchy;

    public DeleteMethodQuickFix(final ProblemDescriptionsProcessor processor, final boolean needToDeleteHierarchy) {
      myProcessor = processor;
      myNeedToDEleteHierarchy = needToDeleteHierarchy;
    }

    @NotNull
    public String getName() {
      return QUICK_FIX_NAME;
    }

    public void applyFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      RefElement refElement = (RefElement)myProcessor.getElement(descriptor);
      if (refElement.isValid() && refElement instanceof RefMethod) {
        final List<RefElement> refElements = new ArrayList<RefElement>(1);
        RefMethod refMethod = (RefMethod)refElement;
        final List<PsiElement> psiElements = new ArrayList<PsiElement>();
        if (myNeedToDEleteHierarchy) {
          deleteHierarchy(refMethod, psiElements, refElements);
        } else {
          deleteMethod(refMethod, psiElements, refElements);
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            SafeDeleteHandler.invoke(project, psiElements.toArray(new PsiElement[psiElements.size()]), false, new Runnable() {
              public void run() {
                QuickFixAction.removeElements(refElements.toArray(new RefElement[refElements.size()]), project, (InspectionTool)myProcessor);
              }
            });
          }
        });
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    private void deleteHierarchy(RefMethod refMethod, List<PsiElement> result, List<RefElement> refElements) {
      Collection<RefMethod> derivedMethods = refMethod.getDerivedMethods();
      RefMethod[] refMethods = derivedMethods.toArray(new RefMethod[derivedMethods.size()]);
      for (RefMethod refDerived : refMethods) {
        deleteMethod(refDerived, result, refElements);
      }
      deleteMethod(refMethod, result, refElements);
    }

    private void deleteMethod(RefMethod refMethod, List<PsiElement> result, List<RefElement> refElements) {
      refElements.add(refMethod);
      PsiElement psiElement = refMethod.getElement();
      if (psiElement == null) return;
      if (!result.contains(psiElement)) result.add(psiElement);
    }
  }
}
