package com.intellij.openapi.vcs.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.HistoryAsTreeProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TableUtil;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.TreeItem;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.List;

public class CompareWithSelectedRevisionAction extends AbstractVcsAction {

  private static final ColumnInfo<TreeNodeAdapter,String> BRANCH_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revisions.list.branch")){
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getBranchName();
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> REVISION_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revision.list.revision")){
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getRevisionNumber().asString();
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> DATE_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revisions.list.filter")){
    public String valueOf(final TreeNodeAdapter object) {
      return VcsRevisionListCellRenderer.DATE_FORMAT.format(object.getRevision().getRevisionDate());
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> AUTHOR_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revision.list.author")){
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getAuthor();
    }
  };

  private static final ColumnInfo<VcsFileRevision, String> REVISION_TABLE_COLUMN = new ColumnInfo<VcsFileRevision, String>(VcsBundle.message("column.name.revision.list.revision")) {
    public String valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision.getRevisionNumber().asString();
    }
  };

  private static final ColumnInfo<VcsFileRevision, String> DATE_TABLE_COLUMN = new ColumnInfo<VcsFileRevision, String>(VcsBundle.message("column.name.revision.list.revision")) {
    public String valueOf(final VcsFileRevision vcsFileRevision) {
      return VcsRevisionListCellRenderer.DATE_FORMAT.format(vcsFileRevision.getRevisionDate());
    }
  };

  private static final ColumnInfo<VcsFileRevision,String> AUTHOR_TABLE_COLUMN = new ColumnInfo<VcsFileRevision, String>(VcsBundle.message("column.name.revision.list.author")){
    public String valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision.getAuthor();
    }
  };

  private static final ColumnInfo<VcsFileRevision,String> BRANCH_TABLE_COLUMN = new ColumnInfo<VcsFileRevision, String>(VcsBundle.message("column.name.revisions.list.branch")){
    public String valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision.getBranchName();
    }
  };

  public void update(VcsContext e, Presentation presentation) {
    AbstractShowDiffAction.updateDiffAction(presentation, e);
  }


  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected void actionPerformed(VcsContext vcsContext) {
    final VirtualFile file = vcsContext.getSelectedFiles()[0];
    final Project project = vcsContext.getProject();
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    final VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();

    try {
      final VcsHistorySession session = vcsHistoryProvider.createSessionFor(new FilePathImpl(file));
      if (session == null) return;
      final List<VcsFileRevision> revisions = session.getRevisionList();
      final HistoryAsTreeProvider treeHistoryProvider = vcsHistoryProvider.getTreeHistoryProvider();
      if (treeHistoryProvider != null) {
        showTreePopup(treeHistoryProvider.createTreeOn(revisions), file, project, vcs.getDiffProvider());
      }
      else {
        showListPopup(revisions, project, new Consumer<VcsFileRevision>() {
          public void consume(final VcsFileRevision revision) {
            DiffActionExecutor.showDiff(vcs.getDiffProvider(), revision.getRevisionNumber(), file, project);
          }
        }, true);
      }

    }
    catch (VcsException e1) {
      Messages.showErrorDialog(VcsBundle.message("message.text.cannot.show.differences", e1.getMessage()),
                               CommonBundle.message("title.error"));
    }


  }

  private static void showTreePopup(final List<TreeItem<VcsFileRevision>> roots, final VirtualFile file, final Project project, final DiffProvider diffProvider) {
    final TreeTableView treeTable = new TreeTableView(new ListTreeTableModelOnColumns(new TreeNodeAdapter(null, null, roots),
                                                                                      new ColumnInfo[]{BRANCH_COLUMN, REVISION_COLUMN,
                                                                                      DATE_COLUMN, AUTHOR_COLUMN}));
    Runnable runnable = new Runnable() {
      public void run() {
        int index = treeTable.getSelectionModel().getMinSelectionIndex();
        if (index == -1) {
          return;
        }
        VcsFileRevision revision = getRevisionAt(treeTable, index);
        if (revision != null) {
          DiffActionExecutor.showDiff(diffProvider, revision.getRevisionNumber(), file, project);
        }
      }
    };

    treeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    new PopupChooserBuilder(treeTable).
      setTitle(VcsBundle.message("lookup.title.vcs.file.revisions")).
      setItemChoosenCallback(runnable).
      setSouthComponent(createCommentsPanel(treeTable)).
      setResizable(true).
      setDimensionServiceKey("Vcs.CompareWithSelectedRevision.Popup").
      createPopup().
      showCenteredInCurrentWindow(project);
  }


  @Nullable private static VcsFileRevision getRevisionAt(final TreeTableView treeTable, final int index) {
    final List items = treeTable.getItems();
    if (items.size() <= index) {
      return null;
    } else {
      return ((TreeNodeAdapter)items.get(index)).getRevision();
    }

  }

  private static JPanel createCommentsPanel(final TreeTableView treeTable) {
    JPanel panel = new JPanel(new BorderLayout());
    final JTextArea textArea = createTextArea();
    treeTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int index = treeTable.getSelectionModel().getMinSelectionIndex();
        if (index == -1) {
          textArea.setText("");
        } else {
          final VcsFileRevision revision = getRevisionAt(treeTable, index);
          if (revision != null) {
            textArea.setText(revision.getCommitMessage());
          } else {
            textArea.setText("");
          }
        }
      }
    });
    final JScrollPane textScrollPane = new JScrollPane(textArea);
    panel.add(textScrollPane, BorderLayout.CENTER);
    textScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray),VcsBundle.message("border.selected.revision.commit.message")));
    return panel;
  }

  private static JTextArea createTextArea() {
    final JTextArea textArea = new JTextArea();
    textArea.setRows(5);
    textArea.setEditable(false);
    textArea.setWrapStyleWord(true);
    textArea.setLineWrap(true);
    return textArea;
  }

  public static void showListPopup(final List<VcsFileRevision> revisions, final Project project, final Consumer<VcsFileRevision> selectedRevisionConsumer,
                                   final boolean showComments) {
    ColumnInfo[] columns = new ColumnInfo[] { REVISION_TABLE_COLUMN, DATE_TABLE_COLUMN, AUTHOR_TABLE_COLUMN };
    for(VcsFileRevision revision: revisions) {
      if (revision.getBranchName() != null) {
        columns = new ColumnInfo[] { REVISION_TABLE_COLUMN, BRANCH_TABLE_COLUMN, DATE_TABLE_COLUMN, AUTHOR_TABLE_COLUMN };
        break;
      }
    }
    final TableView<VcsFileRevision> table = new TableView<VcsFileRevision>(new ListTableModel<VcsFileRevision>(columns, revisions, 0));
    table.setShowHorizontalLines(false);
    table.setTableHeader(null);
    Runnable runnable = new Runnable() {
      public void run() {
        VcsFileRevision revision = table.getSelectedObject();
        if (revision != null) {
          selectedRevisionConsumer.consume(revision);
        }
      }
    };

    if (table.getModel().getRowCount() == 0) {
      table.clearSelection();
    }

    new SpeedSearchBase<TableView>(table) {
      protected int getSelectedIndex() {
        return table.getSelectedRow();
      }

      protected Object[] getAllElements() {
        return revisions.toArray();
      }

      protected String getElementText(Object element) {
        VcsFileRevision revision = (VcsFileRevision) element;
        return revision.getRevisionNumber().asString() + " " + revision.getBranchName() + " " + revision.getAuthor();
      }

      protected void selectElement(Object element, String selectedText) {
        VcsFileRevision revision = (VcsFileRevision) element;
        TableUtil.selectRows(myComponent, new int[] {revisions.indexOf(revision)});
        TableUtil.scrollSelectionToVisible(myComponent);
      }
    };

    table.setMinimumSize(new Dimension(300, 50));
    final PopupChooserBuilder builder = new PopupChooserBuilder(table);

    if (showComments) {
      builder.setSouthComponent(createCommentsPanel(table));
    }

    builder.setTitle(VcsBundle.message("lookup.title.vcs.file.revisions")).
        setItemChoosenCallback(runnable).
        setResizable(true).
        setDimensionServiceKey("Vcs.CompareWithSelectedRevision.Popup").setMinSize(new Dimension(300, 300));
    final JBPopup popup = builder.createPopup();
    
    popup.showCenteredInCurrentWindow(project);
  }

  private static JPanel createCommentsPanel(final TableView<VcsFileRevision> table) {
    final JTextArea textArea = createTextArea();
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final VcsFileRevision revision = table.getSelectedObject();
        if (revision == null) {
          textArea.setText("");
        } else {
          textArea.setText(revision.getCommitMessage());
          textArea.select(0, 0);
        }
      }
    });

    JPanel jPanel = new JPanel(new BorderLayout());
    final JScrollPane textScrollPane = new JScrollPane(textArea);
    textScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray),VcsBundle.message("border.selected.revision.commit.message")));
    jPanel.add(textScrollPane, BorderLayout.SOUTH);

    jPanel.setPreferredSize(new Dimension(300, 100));
    return jPanel;
  }

  private static class TreeNodeAdapter extends DefaultMutableTreeNode {
    private final TreeItem<VcsFileRevision> myRevision;

    public TreeNodeAdapter(TreeNodeAdapter parent, TreeItem<VcsFileRevision> revision, List<TreeItem<VcsFileRevision>> children) {
      if (parent != null) {
        parent.add(this);
      }
      myRevision = revision;
      for (TreeItem<VcsFileRevision> treeItem : children) {
        new TreeNodeAdapter(this, treeItem, treeItem.getChildren());
      }
    }

    public VcsFileRevision getRevision() {
      return myRevision.getData();
    }
  }
}
