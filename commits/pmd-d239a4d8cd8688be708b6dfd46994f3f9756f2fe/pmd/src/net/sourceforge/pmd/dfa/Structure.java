/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.dfa;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import net.sourceforge.pmd.ast.JavaDataFlowNode;
import net.sourceforge.pmd.ast.SimpleNode;


/**
 * @author raik
 *         <p/>
 *         Structure contains only raw data. A set of nodes which represent a data flow
 *         and 2 stacks to link the nodes to each other.
 */
public class Structure {

    private LinkedList<DataFlowNode> dataFlow = new LinkedList<DataFlowNode>();
    private Stack<StackObject> braceStack = new Stack<StackObject>();
    private Stack<StackObject> continueBreakReturnStack = new Stack<StackObject>();

    /**
     * This class encapsulates the access to the DataFlowNode class. Is this worthwhile?
     * TODO I think it's too confusing to have the DataFlowNode constructor
     * add the created instance to the LinkedList.  I think it'd be clearer if we did
     * that more "procedurally", i.e., create the object, then add it to the list.
     */
    public DataFlowNode createNewNode(SimpleNode node) {
	// FUTURE Keep working on generalizing beyond just Java support
        return new JavaDataFlowNode(this.dataFlow, node);
    }

    public DataFlowNode createStartNode(int line) {
        return new StartOrEndDataFlowNode(this.dataFlow, line, true);
    }

    public DataFlowNode createEndNode(int line) {
        return new StartOrEndDataFlowNode(this.dataFlow, line, false);
    }

    public DataFlowNode getLast() {
        return this.dataFlow.getLast();
    }

    public DataFlowNode getFirst() {
        return this.dataFlow.getFirst();
    }

//  ----------------------------------------------------------------------------
//	STACK FUNCTIONS

    /**
     * The braceStack contains all nodes which are important to link the data
     * flow nodes. The cbrStack contains continue, break, and return nodes.
     * There are 2 Stacks because the have to process differently.
     */
    protected void pushOnStack(int type, DataFlowNode node) {
        StackObject obj = new StackObject(type, node);
        if (type == NodeType.RETURN_STATEMENT
        		|| type == NodeType.BREAK_STATEMENT
        		|| type == NodeType.CONTINUE_STATEMENT
        		|| type == NodeType.THROW_STATEMENT) {
            // ugly solution - stores the type information in two ways
            continueBreakReturnStack.push(obj);
        } else {
            braceStack.push(obj);
        }
        ((DataFlowNode) node).setType(type);
    }

    public List getBraceStack() {
        return braceStack;
    }

    public List getContinueBreakReturnStack() {
        return continueBreakReturnStack;
    }

}
