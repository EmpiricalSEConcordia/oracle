/* Generated By:JJTree: Do not edit this line. ASTBreakStatement.java */

package com.infoether.pmd.ast;

public class ASTBreakStatement extends SimpleNode {
  public ASTBreakStatement(int id) {
    super(id);
  }

  public ASTBreakStatement(JavaParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(JavaParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}
