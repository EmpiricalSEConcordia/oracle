/* Generated By:JJTree: Do not edit this line. ASTAnnotation.java */

package net.sourceforge.pmd.ast;

import net.sourceforge.pmd.Rule;

public class ASTAnnotation extends SimpleJavaNode {
    public ASTAnnotation(int id) {
        super(id);
    }

    public ASTAnnotation(JavaParser p, int id) {
        super(p, id);
    }


    public boolean suppresses(Rule rule) {
        /*  Check for "suppress all warnings" case
          @SuppressWarnings("")
          TypeDeclaration
          Annotation
           NormalAnnotation
            Name:SuppressWarnings
        */
        if (jjtGetChild(0) instanceof ASTSingleMemberAnnotation) {
            ASTSingleMemberAnnotation n = (ASTSingleMemberAnnotation) jjtGetChild(0);
            if (n.jjtGetChild(0) instanceof ASTName && ((ASTName) n.jjtGetChild(0)).getImage().equals("SuppressWarnings")) {
                return true;
            }
            return false;
        }

        /* Check for "suppress some warnings" case
         @SuppressWarnings({"hi","hey"})
         TypeDeclaration
          Annotation
           SingleMemberAnnotation
            Name:SuppressWarnings
            MemberValue
             MemberValueArrayInitializer
              MemberValue
               PrimaryExpression
                PrimaryPrefix
                 Literal:"hi"
              MemberValue
               PrimaryExpression
                PrimaryPrefix
                 Literal:"hey"
        */
/*

        if (!(jjtGetChild(0) instanceof ASTName)) {
            return false;
        }
        ASTName n = (ASTName)jjtGetChild(0);
        if (n.getImage() == null || n.getImage().equals("SuppressWarnings")) {
            return false;
        }

        //List values = findChildrenOfType()
*/
        return false;
    }


    /**
     * Accept the visitor. *
     */
    public Object jjtAccept(JavaParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }
}
