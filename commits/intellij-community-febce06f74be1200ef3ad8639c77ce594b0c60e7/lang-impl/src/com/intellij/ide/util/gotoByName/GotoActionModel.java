package com.intellij.ide.util.gotoByName;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ArrayUtil;
import org.apache.oro.text.regex.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class GotoActionModel implements ChooseByNameModel {
  private final Project myProject;
  private final Component myContextComponent;

  private ActionManager myActionManager = ActionManager.getInstance();

  private static final EmptyIcon EMPTY_ICON = new EmptyIcon(18, 18);

  private String myPattern;

  private Pattern myCompiledPattern;
  private PatternMatcher myMatcher = new Perl5Matcher();


  public GotoActionModel(Project project, final Component component) {
    myProject = project;
    myContextComponent = component;
  }

  public String getPromptText() {
    return IdeBundle.message("prompt.gotoaction.enter.action");
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.other.included");
  }

  public char getCheckBoxMnemonic() {
    return 'd';
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.menu.actions.found");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.actions.found");
  }

  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return propertiesComponent.isTrueValue("GoToAction.allIncluded");
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue("GoToAction.allIncluded", Boolean.toString(state));
  }

  public ListCellRenderer getListCellRenderer() {
    return new DefaultListCellRenderer() {

      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        final Color bg = isSelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
        panel.setBackground(bg);


        if (value instanceof Map.Entry) {

          final Map.Entry actionWithParentGroup = (Map.Entry)value;

          final AnAction anAction = (AnAction)actionWithParentGroup.getKey();
          final Presentation templatePresentation = anAction.getTemplatePresentation();
          final Icon icon = templatePresentation.getIcon();
          final LayeredIcon layeredIcon = new LayeredIcon(2);
          layeredIcon.setIcon(EMPTY_ICON, 0);
          if (icon != null && icon.getIconWidth() <= EMPTY_ICON.getIconWidth() && icon.getIconHeight() <= EMPTY_ICON.getIconHeight()) {
            layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth())/2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight())/2);
          }

          final Presentation presentation = new Presentation();

          final AnActionEvent event = new AnActionEvent(null, DataManager.getInstance().getDataContext(myContextComponent),
                                                        ActionPlaces.UNKNOWN, presentation, ActionManager.getInstance(),
                                                        0);
          anAction.beforeActionPerformedUpdate(event);
          anAction.update(event);

          final Color fg = isSelected ? UIUtil.getListSelectionForeground() :
                           presentation.isEnabled() && presentation.isVisible() ? UIUtil.getListForeground() : UIUtil.getInactiveTextColor();

          final Shortcut[] shortcutSet = KeymapManager.getInstance().getActiveKeymap().getShortcuts(myActionManager.getId(anAction));
          final String actionPresentation = templatePresentation.getText() + (shortcutSet != null && shortcutSet.length > 0
                                                                              ? " (" + KeymapUtil.getShortcutText(shortcutSet[0]) + ")"
                                                                              : "");
          final JLabel actionLabel = new JLabel(actionPresentation, layeredIcon, SwingConstants.LEFT);
          actionLabel.setBackground(bg);
          actionLabel.setForeground(fg);

          panel.add(actionLabel, BorderLayout.WEST);

          final String groupName = (String)actionWithParentGroup.getValue();
          if (groupName != null) {
            final JLabel groupLabel = new JLabel(groupName);
            groupLabel.setBackground(bg);
            groupLabel.setForeground(fg);
            panel.add(groupLabel, BorderLayout.EAST);
          }
        }
        return panel;
      }
    };
  }

  public String[] getNames(boolean checkBoxState) {
    final ArrayList<String> result = new ArrayList<String>();
    collectActionIds(result, (ActionGroup)myActionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU));
    if (checkBoxState) {
      final Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
      for (String id : ids) {
        final AnAction anAction = myActionManager.getAction(id);
        if (!(anAction instanceof ActionGroup)) {
          result.add(id);
        }
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  private void collectActionIds(Collection<String> result, ActionGroup group){
    final AnAction[] actions = group.getChildren(null);
    for (AnAction action : actions) {
      if (action instanceof ActionGroup) {
        collectActionIds(result, (ActionGroup)action);
      } else {
        result.add(myActionManager.getId(action));
      }
    }
  }

  public Object[] getElementsByName(final String id, final boolean checkBoxState, final String pattern) {
    final HashMap<AnAction, String> map = new HashMap<AnAction, String>();
    final ActionGroup mainMenu = (ActionGroup)myActionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    collectActions(id, map, mainMenu, mainMenu.getTemplatePresentation().getText());
    if (checkBoxState) {
      final Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
      for (AnAction action : map.keySet()) { //do not add already included actions
        ids.remove(myActionManager.getId(action));
      }
      if (ids.contains(id)) {
        final AnAction anAction = myActionManager.getAction(id);
        if (!(anAction instanceof ActionGroup)) {
          map.put(anAction, null);
        }
      }
    }
    return map.entrySet().toArray(new Map.Entry[map.size()]);
  }

  private void collectActions(String id, Map<AnAction, String> result, ActionGroup group, final String containingGroupName){
    final AnAction[] actions = group.getChildren(null);
    for (AnAction action : actions) {
      if (action instanceof ActionGroup) {
        final ActionGroup actionGroup = (ActionGroup)action;
        final String groupName = actionGroup.getTemplatePresentation().getText();
        collectActions(id, result, actionGroup, groupName != null ? groupName : containingGroupName);
      } else if (myActionManager.getId(action) == id) {
        final String groupName = group.getTemplatePresentation().getText();
        result.put(action, groupName != null && groupName.length() > 0 ? groupName : containingGroupName);
      }
    }
  }

  @Nullable
  public String getFullName(final Object element) {
    return getElementName(element);
  }

  @NotNull
  public String[] getSeparators() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public String getElementName(final Object element) {
    if (!(element instanceof Map.Entry)) return null;
    return ((AnAction)((Map.Entry)element).getKey()).getTemplatePresentation().getText();
  }

  public boolean matches(final String name, final String pattern) {
    final AnAction anAction = myActionManager.getAction(name);
    if (!(anAction instanceof ActionGroup)) {
      final Presentation presentation = anAction.getTemplatePresentation();
      final String text = presentation.getText();
      final String description = presentation.getDescription();
      final Pattern compiledPattern = getPattern(pattern);
      if ((text != null && myMatcher.matches(text, compiledPattern)) ||
          (description != null && myMatcher.matches(description, compiledPattern))) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private Pattern getPattern(String pattern) {
    if (!Comparing.strEqual(pattern, myPattern)) {
      myCompiledPattern = null;
      myPattern = pattern;
    }
    if (myCompiledPattern == null) {
      boolean allowToLower = true;
      final int eol = pattern.indexOf('\n');
      if (eol != -1) {
        pattern = pattern.substring(0, eol);
      }
      if (pattern.length() >= 80) {
        pattern = pattern.substring(0, 80);
      }

      final @NonNls StringBuffer buffer = new StringBuffer();

      if (containsOnlyUppercaseLetters(pattern)) {
        allowToLower = false;
      }

      if (allowToLower) {
        buffer.append(".*");
      }

      boolean firstIdentifierLetter = true;
      for (int i = 0; i < pattern.length(); i++) {
        final char c = pattern.charAt(i);
        if (Character.isLetterOrDigit(c)) {
          // This logic allows to use uppercase letters only to catch the name like PDM for PsiDocumentManager
          if (Character.isUpperCase(c) || Character.isDigit(c)) {
            
            if (!firstIdentifierLetter) {
              buffer.append("[^A-Z]*");
            }

            buffer.append("[");
            buffer.append(c);
            if (allowToLower || i == 0) {
              buffer.append('|');
              buffer.append(Character.toLowerCase(c));
            }
            buffer.append("]");
          }
          else if (Character.isLowerCase(c)) {
            buffer.append('[');
            buffer.append(c);
            buffer.append('|');
            buffer.append(Character.toUpperCase(c));
            buffer.append(']');
          }
          else {
            buffer.append(c);
          }

          firstIdentifierLetter = false;
        }
        else if (c == '*') {
          buffer.append(".*");
          firstIdentifierLetter = true;
        }
        else if (c == '.') {
          buffer.append("\\.");
          firstIdentifierLetter = true;
        }
        else if (c == ' ') {
          buffer.append("[^A-Z]*\\ ");
          firstIdentifierLetter = true;
        }
        else {
          firstIdentifierLetter = true;
          // for standard RegExp engine
          // buffer.append("\\u");
          // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

          // for OROMATCHER RegExp engine
          buffer.append("\\x");
          buffer.append(Integer.toHexString(c + 0x20000).substring(3));
        }
      }

      buffer.append(".*");


      try {
        myCompiledPattern = new Perl5Compiler().compile(buffer.toString());
      }
      catch (MalformedPatternException e) {
        //do nothing
      }
    }

    return myCompiledPattern;
  }

  private static boolean containsOnlyUppercaseLetters(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != '*' && c != ' ' && !Character.isUpperCase(c)) return false;
    }
    return true;
  }
}