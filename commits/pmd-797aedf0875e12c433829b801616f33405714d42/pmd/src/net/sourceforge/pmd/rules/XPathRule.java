/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.rules;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Map.Entry;

import net.sourceforge.pmd.CommonAbstractRule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.ast.ASTVariableDeclaratorId;
import net.sourceforge.pmd.ast.Node;
import net.sourceforge.pmd.ast.SimpleNode;
import net.sourceforge.pmd.jaxen.DocumentNavigator;
import net.sourceforge.pmd.jaxen.MatchesFunction;

import org.jaxen.BaseXPath;
import org.jaxen.JaxenException;
import org.jaxen.SimpleVariableContext;
import org.jaxen.XPath;
import org.jaxen.expr.AllNodeStep;
import org.jaxen.expr.DefaultXPathFactory;
import org.jaxen.expr.LocationPath;
import org.jaxen.expr.NameStep;
import org.jaxen.expr.Predicate;
import org.jaxen.expr.Step;
import org.jaxen.expr.UnionExpr;
import org.jaxen.expr.XPathFactory;
import org.jaxen.saxpath.Axis;

/**
 * Rule that tries to match an XPath expression against a DOM
 * view of the AST of a "compilation unit".
 * <p/>
 * This rule needs a property "xpath".
 */
public class XPathRule extends CommonAbstractRule {

    // Mapping from Node name to applicable XPath queries
    private Map nodeNameToXPaths;
    private boolean regexpFunctionRegistered;

    private static final String AST_ROOT = "_AST_ROOT_";

    /**
     * Evaluate the AST with compilationUnit as root-node, against
     * the XPath expression found as property with name "xpath".
     * All matches are reported as violations.
     *
     * @param compilationUnit the Node that is the root of the AST to be checked
     * @param data
     */
    public void evaluate(Node compilationUnit, RuleContext data) {
        try {
            initializeXPathExpression();
            List xpaths = (List)nodeNameToXPaths.get(compilationUnit.toString());
            if (xpaths == null) {
                xpaths = (List)nodeNameToXPaths.get(AST_ROOT);
            }
            for (int i = 0; i < xpaths.size(); i++) {
                XPath xpath = (XPath)xpaths.get(i);
                List results = xpath.selectNodes(compilationUnit);
                for (Iterator j = results.iterator(); j.hasNext();) {
                    SimpleNode n = (SimpleNode) j.next();
                    if (n instanceof ASTVariableDeclaratorId && getBooleanProperty("pluginname")) {
                        addViolation(data, n, n.getImage());
                    } else {
                        addViolation(data, (SimpleNode) n, getMessage());
                    }
                }
            }
        } catch (JaxenException ex) {
            throwJaxenAsRuntime(ex);
        }
    }

    public List getRuleChainVisits() {
        try {
            initializeXPathExpression();
            return super.getRuleChainVisits();
        } catch (JaxenException ex) {
            throwJaxenAsRuntime(ex);
            // Note: This return should never happen because the above method throws an exception
            return null;
        }
    }

    private void initializeXPathExpression() throws JaxenException {
        if (nodeNameToXPaths != null) {
            return;
        }

        if (!regexpFunctionRegistered) {
            MatchesFunction.registerSelfInSimpleContext();
            regexpFunctionRegistered = true;
        }

        //
        // Attempt to use the RuleChain with this XPath query.  To do so, the queries
        // should generally look like //TypeA or //TypeA | //TypeB.  We will look at the
        // parsed XPath AST using the Jaxen APIs to make this determination.
        // If the query is not exactly what we are looking for, do not use the RuleChain.
        //
        nodeNameToXPaths = new HashMap();

        BaseXPath originalXPath = (BaseXPath)createXPath(getStringProperty("xpath"));
        indexXPath(originalXPath, AST_ROOT);

        boolean useRuleChain = true;
        Stack pending = new Stack();
        pending.push(originalXPath.getRootExpr());
        while (!pending.isEmpty()) {
            Object node = pending.pop();

            // Need to prove we can handle this part of the query
            boolean valid = false;

            // Must be a LocationPath... that is something like //Type
            if (node instanceof LocationPath) {
                LocationPath locationPath = (LocationPath)node;
                if (locationPath.isAbsolute()) {
                    // Should be at least two steps
                    List steps = locationPath.getSteps();
                    if (steps.size() >= 2) {
                        Step step1 = (Step)steps.get(0);
                        Step step2 = (Step)steps.get(1);
                        // First step should be an AllNodeStep using the descendant or self axis
                        if (step1 instanceof AllNodeStep && ((AllNodeStep)step1).getAxis() == Axis.DESCENDANT_OR_SELF) {
                            // Second step should be a NameStep using the child axis.
                            if (step2 instanceof NameStep && ((NameStep)step2).getAxis() == Axis.CHILD) {
                                // Construct a new expression that is appropriate for RuleChain use
                                XPathFactory xpathFactory = new DefaultXPathFactory();

                                // Instead of an absolute location path, we'll be using a relative path
                                LocationPath relativeLocationPath = xpathFactory.createRelativeLocationPath();
                                // The first step will be along the self axis
                                Step allNodeStep = xpathFactory.createAllNodeStep(Axis.SELF);
                                // Retain all predicates from the original name step
                                for (Iterator i = step2.getPredicates().iterator(); i.hasNext();) {
                                    allNodeStep.addPredicate((Predicate)i.next());
                                }
                                relativeLocationPath.addStep(allNodeStep);

                                // Retain the remaining steps from the original location path
                                for (int i = 2; i < steps.size(); i++) {
                                    relativeLocationPath.addStep((Step)steps.get(i));
                                }

                                BaseXPath xpath = createXPath(relativeLocationPath.getText());
                                indexXPath(xpath, ((NameStep)step2).getLocalName());
                                valid = true;
                            }
                        }
                    }
                }
            } else if (node instanceof UnionExpr) { // Or a UnionExpr, that is something like //TypeA | //TypeB
                UnionExpr unionExpr = (UnionExpr)node;
                pending.push(unionExpr.getLHS());
                pending.push(unionExpr.getRHS());
                valid = true;
            }
            if (!valid) {
                useRuleChain = false;
                break;
            }
        }

        if (useRuleChain) {
            // Use the RuleChain for all the nodes extracted from the xpath queries
            for (Iterator i = nodeNameToXPaths.keySet().iterator(); i.hasNext();) {
                addRuleChainVisit((String)i.next());
            }
        } else { // Use original XPath if we cannot use the rulechain
            nodeNameToXPaths.clear();
            indexXPath(originalXPath, AST_ROOT);
            //System.err.println("Unable to use RuleChain for " + this.getName() + " for XPath: " + getStringProperty("xpath"));
        }
    }

    private void indexXPath(XPath xpath, String nodeName) {
        List xpaths = (List)nodeNameToXPaths.get(nodeName);
        if (xpaths == null) {
            xpaths = new ArrayList();
            nodeNameToXPaths.put(nodeName, xpaths);
        }
        xpaths.add(xpath);
    }

    private BaseXPath createXPath(String xpathQueryString) throws JaxenException {
        // TODO As of Jaxen 1.1, LiteralExpr which contain " or ' characters
        // are not escaped properly.  The following is fix for the known
        // XPath queries built into PMD.  It will not necessarily work for
        // arbitrary XPath queries users of PMD will create.  JAXEN-177 is
        // about this problem: http://jira.codehaus.org/browse/JAXEN-177
        // PMD should upgrade to the next Jaxen release containing this fix.
        xpathQueryString = xpathQueryString.replaceAll("\"\"\"", "'\"'");

        BaseXPath xpath = new BaseXPath(xpathQueryString, new DocumentNavigator());
        if (properties.size() > 1) {
            SimpleVariableContext vc = new SimpleVariableContext();
            for (Iterator j = properties.entrySet().iterator(); j.hasNext();) {
                Entry e = (Entry) j.next();
                if (!"xpath".equals(e.getKey())) {
                    vc.setVariableValue((String) e.getKey(), e.getValue());
                }
            }
            xpath.setVariableContext(vc);
        }
        return xpath;
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

    /**
     * Apply the rule to all compilation units.
     */
    public void apply(List astCompilationUnits, RuleContext ctx) {
        for (Iterator i = astCompilationUnits.iterator(); i.hasNext();) {
            evaluate((Node) i.next(), ctx);
        }
    }
}
