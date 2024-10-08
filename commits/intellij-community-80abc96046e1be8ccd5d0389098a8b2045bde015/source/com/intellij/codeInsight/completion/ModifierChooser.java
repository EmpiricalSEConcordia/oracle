package com.intellij.codeInsight.completion;

import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.classes.InterfaceFilter;
import com.intellij.psi.javadoc.PsiDocComment;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 17.02.2003
 * Time: 17:03:09
 * To change this template use Options | File Templates.
 */

public class ModifierChooser
 implements KeywordChooser{
  private static Map myMap = new com.intellij.util.containers.HashMap();

  static {
    myMap.put(new NotFilter(new InterfaceFilter()), new String[][]{
      new String[]{"public", "protected", "private"},
      new String[]{"static"},
      new String[]{"final", "abstract"},
      new String[]{"native"},
      new String[]{"synchronized"},
      new String[]{"volatile"},
      new String[]{"transient"}
    });

    myMap.put(new InterfaceFilter(), new String[][]{
      new String[]{"public", "protected"},
      new String[]{"static"},
      new String[]{"final"}
    });


    myMap.put(new ClassFilter(PsiJavaFile.class), new String[][]{
      new String[]{"public"},
      new String[]{"final", "abstract"}
    });

    myMap.put(new ClassFilter(PsiCodeBlock.class), new String[][]{
      new String[]{"final", "synchronized"}
    });

    myMap.put(new ClassFilter(PsiParameterList.class), new String[][]{
      new String[]{"final"}
    });
  }

  public String[] getKeywords(CompletionContext context, PsiElement position){
    final List ret = new ArrayList();
    try{
      PsiElement scope;

      if(position == null)
        scope = context.file;
      else
        scope = position.getParent();

      final PsiModifierList list = getModifierList(position);

scopes:
      while (scope != null) {
        for (final Object o : myMap.keySet()) {
          final ElementFilter filter = (ElementFilter)o;
          if (filter.isClassAcceptable(scope.getClass()) && filter.isAcceptable(scope, scope.getParent())) {
            final String[][] keywordSets = (String[][])myMap.get(filter);
            keywordSets:
            for (int i = 0; i < keywordSets.length; i++) {
              final String[] keywords = keywordSets[keywordSets.length - i - 1];
              boolean containModifierFlag = false;
              if (list != null) {
                for (String keyword : keywords) {
                  if (list.hasModifierProperty(keyword)) {
                    containModifierFlag = true;
                    break;
                  }
                }
              }
              if (!containModifierFlag) {
                ret.addAll(Arrays.asList(keywords));
              }
//              else break keywordSets;
            }
            break scopes;
          }
        }
        scope = scope.getParent();
        if (scope instanceof PsiDirectory) break;
      }
    }
    catch(Exception e){}
    return (String[])ret.toArray(new String[ret.size()]);
  }

  private PsiModifierList getModifierList(PsiElement element)
  throws Exception{
    if(element == null){
      return null;
    }
    if(element.getParent() instanceof PsiModifierList)
      return (PsiModifierList)element.getParent();

    final PsiElement prev = FilterUtil.searchNonSpaceNonCommentBack(element);

    if(prev != null && prev.getParent() instanceof PsiModifierList){
      return (PsiModifierList)prev.getParent();
    }

    PsiElement parent = element.getParent();
    while(parent != null && (parent instanceof PsiJavaCodeReferenceElement
      || parent instanceof PsiErrorElement || parent instanceof PsiTypeElement
      || parent instanceof PsiMethod || parent instanceof PsiVariable
      || parent instanceof PsiDeclarationStatement || parent instanceof PsiImportList
      || parent instanceof PsiDocComment
      || element.getText().equals(parent.getText()))){
      parent = parent.getParent();
    }

    if(parent == null) throw new Exception();
    for (final Object o : myMap.keySet()) {
      final ElementFilter filter = (ElementFilter)o;
      if (filter.isClassAcceptable(parent.getClass()) && filter.isAcceptable(parent, parent.getParent())) {
        if (parent instanceof PsiParameterList) {
          if (prev == null || Arrays.asList(new String[]{"(", ","}).contains(prev.getText())
              || Arrays.asList(new String[]{"(", ","}).contains(element.getText())) {
            return null;
          }
        }
        else if (prev == null || JavaCompletionData.END_OF_BLOCK.isAcceptable(element, prev.getParent())) {
          return null;
        }
      }
    }

    throw new Exception("Can't find modifier list");
  }

  public String toString(){
    return "modifier-chooser";
  }
}
