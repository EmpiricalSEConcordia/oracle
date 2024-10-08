/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.rules;

import net.sourceforge.pmd.AbstractRule;
import net.sourceforge.pmd.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.ast.Node;
import net.sourceforge.pmd.ast.SimpleNode;
import net.sourceforge.pmd.symboltable.VariableNameDeclaration;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UnusedFormalParameterRule extends AbstractRule {

    public Object visit(ASTConstructorDeclaration node, Object data) {
        check(node, data);
        return data;
    }

    public Object visit(ASTMethodDeclaration node, Object data) {
        if (!node.isPrivate() && !hasProperty("checkall")) {
            return data;
        }
        if (!node.isNative()) {
            check(node, data);
        }
        return data;
    }

    private void check(SimpleNode node, Object data) {
        Node parent = node.jjtGetParent().jjtGetParent().jjtGetParent();
        if (parent instanceof ASTClassOrInterfaceDeclaration && !((ASTClassOrInterfaceDeclaration) parent).isInterface()) {
            Map vars = node.getScope().getVariableDeclarations();
            for (Iterator i = vars.keySet().iterator(); i.hasNext();) {
                VariableNameDeclaration nameDecl = (VariableNameDeclaration) i.next();
                if (!((List) vars.get(nameDecl)).isEmpty()) {
                    continue;
                }
                addViolation(data, node, new Object[]{node instanceof ASTMethodDeclaration ? "method" : "constructor", nameDecl.getImage()});
            }
        }
    }

}
