package net.sourceforge.pmd.util.viewer.gui;

import net.sourceforge.pmd.ast.Node;
import net.sourceforge.pmd.ast.SimpleNode;
import net.sourceforge.pmd.util.viewer.gui.menu.ASTNodePopupMenu;
import net.sourceforge.pmd.util.viewer.model.ASTModel;
import net.sourceforge.pmd.util.viewer.model.ViewerModel;
import net.sourceforge.pmd.util.viewer.model.ViewerModelEvent;
import net.sourceforge.pmd.util.viewer.model.ViewerModelListener;
import net.sourceforge.pmd.util.viewer.util.NLS;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;

/**
 * tree panel GUI
 *
 * @author Boris Gruschko ( boris at gruschko.org )
 * @version $Id$
 */

public class ASTPanel extends JPanel implements ViewerModelListener, TreeSelectionListener {
    private ViewerModel model;
    private JTree tree;

    /**
     * constructs the panel
     *
     * @param model model to attach the panel to
     */
    public ASTPanel(ViewerModel model) {
        this.model = model;
        init();
    }

    private void init() {
        model.addViewerModelListener(this);
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), NLS.nls("AST.PANEL.TITLE")));
        setLayout(new BorderLayout());
        tree = new JTree((TreeNode) null);
        tree.addTreeSelectionListener(this);
        tree.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    TreePath path = tree.getClosestPathForLocation(e.getX(), e.getY());
                    tree.setSelectionPath(path);
                    JPopupMenu menu = new ASTNodePopupMenu(model, (SimpleNode) path.getLastPathComponent());
                    menu.show(tree, e.getX(), e.getY());
                }
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    /**
     * @see ViewerModelListener#viewerModelChanged(ViewerModelEvent)
     */
    public void viewerModelChanged(ViewerModelEvent e) {
        switch (e.getReason()) {
            case ViewerModelEvent.CODE_RECOMPILED:
                tree.setModel(new ASTModel(model.getRootNode()));
                break;
            case ViewerModelEvent.NODE_SELECTED:
                if (e.getSource() != this) {
                    LinkedList<Node> list = new LinkedList<Node>();
                    for (Node n = (Node) e.getParameter(); n != null; n = n.jjtGetParent()) {
                        list.addFirst(n);
                    }
                    TreePath path = new TreePath(list.toArray());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                }
                break;
        }
    }

    /**
     * @see javax.swing.event.TreeSelectionListener#valueChanged(javax.swing.event.TreeSelectionEvent)
     */
    public void valueChanged(TreeSelectionEvent e) {
        model.selectNode((SimpleNode) e.getNewLeadSelectionPath().getLastPathComponent(), this);
    }
}
