/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.rules;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Map.Entry;

import net.sourceforge.pmd.AbstractRule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.ast.ASTCompilationUnit;
import net.sourceforge.pmd.ast.ASTVariableDeclaratorId;
import net.sourceforge.pmd.ast.SimpleNode;
import net.sourceforge.pmd.jaxen.DocumentNavigator;

import org.jaxen.BaseXPath;
import org.jaxen.JaxenException;
import org.jaxen.SimpleVariableContext;
import org.jaxen.XPath;

public class XPathRule extends AbstractRule {

    private XPath xpath;

    public Object visit(ASTCompilationUnit node, Object data) {
        try {
            init();
            for (Iterator iter = xpath.selectNodes(node).iterator(); iter.hasNext();) {
                SimpleNode actualNode = (SimpleNode) iter.next();
                RuleContext ctx = (RuleContext) data;
                String msg = getMessage();
                if (actualNode instanceof ASTVariableDeclaratorId && getBooleanProperty("pluginname")) {
                    msg = MessageFormat.format(msg, new Object[]{actualNode.getImage()});
                }
                ctx.getReport().addRuleViolation(createRuleViolation(ctx, actualNode, msg));
            }
        } catch (JaxenException ex) {
            throwJaxenAsRuntime(ex);
        }
        return data;
    }

    private void init() throws JaxenException {
        if (xpath == null) {
            String path = getStringProperty("xpath");
            String subst = getStringProperty("subst");
            if (subst != null && subst.length() > 0) {
                path = MessageFormat.format(path, new String[]{subst});
            }
            xpath = new BaseXPath(path, new DocumentNavigator());
            initXPathVariables();
        }
    }

    /**
     * Create xpath variables for the rule properties
     */
    private void initXPathVariables() {
        if (properties.size()>1) {  //Don't make the xpath itself available
            SimpleVariableContext varContext = new SimpleVariableContext();
            for (Iterator iter = properties.entrySet().iterator(); iter.hasNext();) {
                Entry entry = (Entry) iter.next();
                if (!"xpath".equals(entry.getKey())) { //Don't make the xpath itself available
                    varContext.setVariableValue((String)entry.getKey(), entry.getValue());
                }
            }
            xpath.setVariableContext(varContext);
        }
    }

    private static void throwJaxenAsRuntime(final JaxenException ex) {
        throw new RuntimeException() {
            public void printStackTrace() {
                super.printStackTrace();
                ex.printStackTrace();
            }

            public void printStackTrace(PrintWriter writer) {
                super.printStackTrace(writer);
                ex.printStackTrace(writer);
            }

            public void printStackTrace(PrintStream stream) {
                super.printStackTrace(stream);
                ex.printStackTrace(stream);
            }

            public String getMessage() {
                return super.getMessage() + ex.getMessage();
            }
        };
    }
}
