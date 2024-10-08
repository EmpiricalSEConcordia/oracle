/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 31, 2002
 * Time: 9:03:01 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlElement;

public class XmlProcessingInstructionImpl extends XmlElementImpl implements XmlProcessingInstruction {
  public XmlProcessingInstructionImpl() {
    super(XML_PROCESSING_INSTRUCTION);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlElement(this);
  }

  public XmlTag getParentTag() {
    final PsiElement parent = getParent();
    if(parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    PsiElement nextSibling = getNextSibling();
    if(nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    final PsiElement prevSibling = getPrevSibling();
    if(prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }
}
