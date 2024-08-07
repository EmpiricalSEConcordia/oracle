/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomCollectionProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.ui.actions.AddDomElementAction;
import com.intellij.util.xml.ui.actions.DefaultAddAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class DomCollectionControl<T extends DomElement> extends DomUIControl implements Highlightable, TypeSafeDataProvider {
  private static final DataKey<DomCollectionControl> DOM_COLLECTION_CONTROL = DataKey.create("DomCollectionControl");

  private final EventDispatcher<CommitListener> myDispatcher = EventDispatcher.create(CommitListener.class);
  private DomTableView myCollectionPanel;

  private final DomElement myParentDomElement;
  private final DomCollectionChildDescription myChildDescription;
  private List<T> myCollectionElements = new ArrayList<T>();
  private ColumnInfo<T, ?>[] myColumnInfos;
  private boolean myEditable = false;
  private final AnAction myAddAction = new AddAction() {
    protected DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
      return DomCollectionControl.this;
    }
  };

  private final AnAction myEditAction = new EditAction() {
    protected DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
      return DomCollectionControl.this;
    }
  };
  private final AnAction myRemoveAction = new RemoveAction() {
    protected DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
      return DomCollectionControl.this;
    }
  };
  public static final Icon ADD_ICON = IconLoader.getIcon("/general/add.png");
  public static final Icon EDIT_ICON = IconLoader.getIcon("/actions/editSource.png");
  public static final Icon REMOVE_ICON = IconLoader.getIcon("/general/remove.png");

  public DomCollectionControl(DomElement parentElement,
                              DomCollectionChildDescription description,
                              final boolean editable,
                              ColumnInfo<T, ?>... columnInfos) {
    myChildDescription = description;
    myParentDomElement = parentElement;
    myColumnInfos = columnInfos;
    myEditable = editable;
  }

  public DomCollectionControl(DomElement parentElement,
                              @NonNls String subTagName,
                              final boolean editable,
                              ColumnInfo<T, ?>... columnInfos) {
    this(parentElement, parentElement.getGenericInfo().getCollectionChildDescription(subTagName), editable, columnInfos);
  }

  public DomCollectionControl(DomElement parentElement, DomCollectionChildDescription description) {
    myChildDescription = description;
    myParentDomElement = parentElement;
  }

  public DomCollectionControl(DomElement parentElement, @NonNls String subTagName) {
    this(parentElement, parentElement.getGenericInfo().getCollectionChildDescription(subTagName));
  }

  public boolean isEditable() {
    return myEditable;
  }

  public void bind(JComponent component) {
    assert component instanceof DomTableView;

    initialize((DomTableView)component);
  }

  public void addCommitListener(CommitListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeCommitListener(CommitListener listener) {
    myDispatcher.removeListener(listener);
  }


  public boolean canNavigate(DomElement element) {
    final Class<DomElement> aClass = (Class<DomElement>)ReflectionUtil.getRawType(myChildDescription.getType());

    final DomElement domElement = element.getParentOfType(aClass, false);

    return domElement != null && myCollectionElements.contains(domElement);
  }

  public void navigate(DomElement element) {
    final Class<DomElement> aClass = (Class<DomElement>)ReflectionUtil.getRawType(myChildDescription.getType());
    final DomElement domElement = element.getParentOfType(aClass, false);

    int index = myCollectionElements.indexOf(domElement);
    if (index < 0) index = 0;

    myCollectionPanel.getTable().setRowSelectionInterval(index, index);
  }

  public void calcData(final DataKey key, final DataSink sink) {
    if (DOM_COLLECTION_CONTROL.equals(key)) {
      sink.put(DOM_COLLECTION_CONTROL, this);
    }
  }

  @Nullable
  protected String getHelpId() {
    return null;
  }

  @Nullable
  protected String getEmptyPaneText() {
    return null;
  }

  protected void initialize(final DomTableView boundComponent) {
    if (boundComponent == null) {
      myCollectionPanel = new DomTableView(getProject(), getEmptyPaneText(), getHelpId());
    }
    else {
      myCollectionPanel = boundComponent;
    }
    myCollectionPanel.setToolbarActions(myAddAction, myEditAction, myRemoveAction);
    myCollectionPanel.installPopup(ActionPlaces.J2EE_ATTRIBUTES_VIEW_POPUP, createPopupActionGroup());
    myCollectionPanel.initializeTable();
    myCollectionPanel.addCustomDataProvider(this);
    myCollectionPanel.addChangeListener(new DomTableView.ChangeListener() {
      public void changed() {
        reset();
      }
    });
    reset();
  }

  protected DefaultActionGroup createPopupActionGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction("DomCollectionControl");
  }

  protected ColumnInfo[] createColumnInfos(DomElement parent) {
    return myColumnInfos;
  }

  protected final void doEdit() {
    doEdit(myCollectionElements.get(myCollectionPanel.getTable().getSelectedRow()));
  }

  protected void doEdit(final T t) {
    final DomEditorManager manager = getDomEditorManager(this);
    if (manager != null) {
      manager.openDomElementEditor(t);
    }
  }

  protected void doRemove(final List<T> toDelete) {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        for (final T t : toDelete) {
          if (t.isValid()) {
            t.undefine();
          }
        }
      }
    }.execute();
  }

  protected final void doRemove() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final int[] selected = myCollectionPanel.getTable().getSelectedRows();
        if (selected == null || selected.length == 0) return;
        final List<T> selectedElements = new ArrayList<T>(selected.length);
        for (final int i : selected) {
          selectedElements.add(myCollectionElements.get(i));
        }

        doRemove(selectedElements);
        reset();
        int selection = selected[0];
        if (selection >= myCollectionElements.size()) {
          selection = myCollectionElements.size() - 1;
        }
        if (selection >= 0) {
          myCollectionPanel.getTable().setRowSelectionInterval(selection, selection);
        }
      }
    });
  }

  protected static void performWriteCommandAction(final WriteCommandAction writeCommandAction) {
    writeCommandAction.execute();
  }

  public void commit() {
    final CommitListener listener = myDispatcher.getMulticaster();
    listener.beforeCommit(this);
    listener.afterCommit(this);
    validate();
  }

  private void validate() {
    DomElement domElement = getDomElement();
    final List<DomElementProblemDescriptor> list =
      DomElementAnnotationsManager.getInstance(getProject()).getCachedProblemHolder(domElement).getProblems(domElement);
    final List<String> messages = new ArrayList<String>();
    for (final DomElementProblemDescriptor descriptor : list) {
      if (descriptor instanceof DomCollectionProblemDescriptor
          && myChildDescription.equals(((DomCollectionProblemDescriptor)descriptor).getChildDescription())) {
        messages.add(descriptor.getDescriptionTemplate());
      }
    }
    myCollectionPanel.setErrorMessages(ArrayUtil.toStringArray(messages));
    myCollectionPanel.repaint();
  }

  public void dispose() {
    if (myCollectionPanel != null) {
      myCollectionPanel.dispose();
    }
  }

  protected final Project getProject() {
    return myParentDomElement.getManager().getProject();
  }

  public DomTableView getComponent() {
    if (myCollectionPanel == null) initialize(null);

    return myCollectionPanel;
  }

  public final DomCollectionChildDescription getChildDescription() {
    return myChildDescription;
  }

  public final DomElement getDomElement() {
    return myParentDomElement;
  }

  public final void reset() {
    myCollectionElements = new ArrayList<T>(getCollectionElements());
    myCollectionPanel.reset(createColumnInfos(myParentDomElement), myCollectionElements);
    validate();
  }

  public List<T> getCollectionElements() {
    return (List<T>)myChildDescription.getValues(myParentDomElement);
  }

  @Nullable
  protected AnAction[] createAdditionActions() {
    return null;
  }

  protected DefaultAddAction createDefaultAction(final String name, final Icon icon, final Type type) {
    return new ControlAddAction(name, name, icon) {
      protected Type getElementType() {
        return type;
      }
    };
  }

  protected final Class<? extends T> getCollectionElementClass() {
    return (Class<? extends T>)ReflectionUtil.getRawType(myChildDescription.getType());
  }


  @Nullable
  private static DomEditorManager getDomEditorManager(DomUIControl control) {
    JComponent component = control.getComponent();
    while (component != null && !(component instanceof DomEditorManager)) {
      final Container parent = component.getParent();
      if (!(parent instanceof JComponent)) {
        return null;
      }
      component = (JComponent)parent;
    }
    return (DomEditorManager)component;
  }

  public void updateHighlighting() {
    if (myCollectionPanel != null) {
      myCollectionPanel.revalidate();
      myCollectionPanel.repaint();
    }
  }

  public class ControlAddAction extends DefaultAddAction<T> {

    public ControlAddAction() {
    }

    public ControlAddAction(final String text) {
      super(text);
    }

    public ControlAddAction(final String text, final String description, final Icon icon) {
      super(text, description, icon);
    }

    protected final DomCollectionChildDescription getDomCollectionChildDescription() {
      return myChildDescription;
    }

    protected final DomElement getParentDomElement() {
      return myParentDomElement;
    }

    /**
     * return negative value to disable auto-edit
     * @return
     */
    protected int getColumnToEditAfterAddition() {
      return 0;
    }

    protected void afterAddition(final JTable table, final int rowIndex) {
      table.setRowSelectionInterval(rowIndex, rowIndex);
      final int column = getColumnToEditAfterAddition();
      if (column >= 0 ) {
        table.editCellAt(rowIndex, column);
      }
    }

    protected final void afterAddition(final T newElement) {
      reset();
      afterAddition(myCollectionPanel.getTable(), myCollectionElements.size() - 1);
    }
  }

  public static DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
    return e.getData(DOM_COLLECTION_CONTROL);
  }

  public static class AddAction extends AddDomElementAction {
    protected boolean isEnabled(final AnActionEvent e) {
      return getDomCollectionControl(e) != null || "ProjectViewToolbar".equals(e.getPlace());
    }

    protected DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
      return DomCollectionControl.getDomCollectionControl(e);
    }

    @NotNull
    protected DomCollectionChildDescription[] getDomCollectionChildDescriptions(final AnActionEvent e) {
      return new DomCollectionChildDescription[] {getDomCollectionControl(e).getChildDescription()};
    }

    protected DomElement getParentDomElement(final AnActionEvent e) {
      return getDomCollectionControl(e).getDomElement();
    }

    protected JComponent getComponent(AnActionEvent e) {
      return getDomCollectionControl(e).getComponent();
    }

    @NotNull
    public AnAction[] getChildren(final AnActionEvent e) {
      final DomCollectionControl control = getDomCollectionControl(e);
      AnAction[] actions = control.createAdditionActions();
      return actions == null ? super.getChildren(e) : actions;
    }

    protected DefaultAddAction createAddingAction(final AnActionEvent e,
                                                  final String name,
                                                  final Icon icon,
                                                  final Type type,
                                                  final DomCollectionChildDescription description) {
      return getDomCollectionControl(e).createDefaultAction(name, icon, type);
    }

  }

  public static class EditAction extends AnAction {
    protected DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
      return DomCollectionControl.getDomCollectionControl(e);
    }

    public EditAction() {
      super(ApplicationBundle.message("action.edit"), null, DomCollectionControl.EDIT_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      final DomCollectionControl control = getDomCollectionControl(e);
      control.doEdit();
      control.reset();
    }

    public void update(AnActionEvent e) {
      final DomCollectionControl control = getDomCollectionControl(e);
      final boolean visible = control != null && control.isEditable();
      e.getPresentation().setVisible(visible);
      e.getPresentation().setEnabled(visible && control.getComponent().getTable().getSelectedRowCount() == 1);
    }
  }

  public static class RemoveAction extends AnAction {
    public RemoveAction() {
      super(ApplicationBundle.message("action.remove"), null, DomCollectionControl.REMOVE_ICON);
    }

    protected DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
      return DomCollectionControl.getDomCollectionControl(e);
    }

    public void actionPerformed(AnActionEvent e) {
      final DomCollectionControl control = getDomCollectionControl(e);
      control.doRemove();
      control.reset();
    }

    public void update(AnActionEvent e) {
      final boolean enabled;
      final DomCollectionControl control = getDomCollectionControl(e);
      if (control != null) {
        final JTable table = control.getComponent().getTable();
        enabled = table != null && table.getSelectedRowCount() > 0;
      } else {
        enabled = false;
      }
      e.getPresentation().setEnabled(enabled);
    }
  }
}
