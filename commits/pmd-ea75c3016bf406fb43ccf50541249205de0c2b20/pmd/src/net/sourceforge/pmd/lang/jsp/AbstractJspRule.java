/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.lang.jsp;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;

import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.lang.AbstractRule;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.jsp.ast.ASTAttribute;
import net.sourceforge.pmd.lang.jsp.ast.ASTAttributeValue;
import net.sourceforge.pmd.lang.jsp.ast.ASTCData;
import net.sourceforge.pmd.lang.jsp.ast.ASTCommentTag;
import net.sourceforge.pmd.lang.jsp.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.jsp.ast.ASTContent;
import net.sourceforge.pmd.lang.jsp.ast.ASTDeclaration;
import net.sourceforge.pmd.lang.jsp.ast.ASTDoctypeDeclaration;
import net.sourceforge.pmd.lang.jsp.ast.ASTDoctypeExternalId;
import net.sourceforge.pmd.lang.jsp.ast.ASTElExpression;
import net.sourceforge.pmd.lang.jsp.ast.ASTElement;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspComment;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspDeclaration;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspDirective;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspDirectiveAttribute;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspExpression;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspExpressionInAttribute;
import net.sourceforge.pmd.lang.jsp.ast.ASTJspScriptlet;
import net.sourceforge.pmd.lang.jsp.ast.ASTText;
import net.sourceforge.pmd.lang.jsp.ast.ASTUnparsedText;
import net.sourceforge.pmd.lang.jsp.ast.ASTValueBinding;
import net.sourceforge.pmd.lang.jsp.ast.JspNode;
import net.sourceforge.pmd.lang.jsp.ast.JspParserVisitor;

public abstract class AbstractJspRule extends AbstractRule implements
		JspParserVisitor {

	@Override
	public void setUsesTypeResolution() {
		// No Type resolution for JSP rules?
	}

	/**
	 * Adds a violation to the report.
	 * 
	 * @param data
	 *            the RuleContext
	 * @param node
	 *            the node that produces the violation
	 */
	protected final void addViolation(Object data, JspNode node) {
		RuleContext ctx = (RuleContext)data;
		ctx.getReport().addRuleViolation(new JspRuleViolation(this, ctx, node));
	}

	/**
	 * Adds a violation to the report.
	 * 
	 * @param data
	 *            the RuleContext
	 * @param node
	 *            the node that produces the violation
	 * @param msg
	 *            specific message to put in the report
	 */
	protected final void addViolationWithMessage(Object data, JspNode node,
			String msg) {
		RuleContext ctx = (RuleContext)data;
		ctx.getReport().addRuleViolation(
				new JspRuleViolation(this, ctx, node, msg));
	}

	/**
	 * Adds a violation to the report.
	 * 
	 * @param data
	 *            the RuleContext
	 * @param node
	 *            the node that produces the violation
	 * @param embed
	 *            a variable to embed in the rule violation message
	 */
	protected final void addViolation(Object data, JspNode node, String embed) {
		RuleContext ctx = (RuleContext)data;
		ctx.getReport().addRuleViolation(
				new JspRuleViolation(this, ctx, node, MessageFormat.format(
						getMessage(), embed)));
	}

	/**
	 * Adds a violation to the report.
	 * 
	 * @param data
	 *            the RuleContext
	 * @param node
	 *            the node that produces the violation, may be null, in which
	 *            case all line and column info will be set to zero
	 * @param args
	 *            objects to embed in the rule violation message
	 */
	protected final void addViolation(Object data, Node node, Object[] args) {
		RuleContext ctx = (RuleContext)data;
		ctx.getReport().addRuleViolation(
				new JspRuleViolation(this, ctx, (JspNode)node, MessageFormat
						.format(getMessage(), args)));
	}

	public void apply(List acus, RuleContext ctx) {
		visitAll(acus, ctx);
	}

	protected void visitAll(List acus, RuleContext ctx) {
		for (Iterator i = acus.iterator(); i.hasNext();) {
			JspNode node = (JspNode)i.next();
			visit(node, ctx);
		}
	}

	//
	// The following APIs are identical to those in JspParserVisitorAdapter.
	// Due to Java single inheritance, it preferred to extend from the more
	// complex Rule base class instead of from relatively simple Visitor.
	//

	public Object visit(JspNode node, Object data) {
		node.childrenAccept(this, data);
		return null;
	}

	public Object visit(ASTCompilationUnit node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTContent node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTJspDirective node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTJspDirectiveAttribute node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTJspScriptlet node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTJspExpression node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTJspDeclaration node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTJspComment node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTText node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTUnparsedText node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTElExpression node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTValueBinding node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTCData node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTElement node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTAttribute node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTAttributeValue node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTJspExpressionInAttribute node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTCommentTag node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTDeclaration node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTDoctypeDeclaration node, Object data) {
		return visit((JspNode)node, data);
	}

	public Object visit(ASTDoctypeExternalId node, Object data) {
		return visit((JspNode)node, data);
	}
}
