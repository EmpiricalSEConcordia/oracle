package com.intellij.application.options;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.OptionGroup;

import javax.swing.*;
import java.awt.*;

public class CodeStyleBlankLinesPanel extends CodeStyleAbstractPanel {
  private JTextField myKeepBlankLinesInDeclarations;
  private JTextField myKeepBlankLinesInCode;
  private JTextField myBlankLinesBeforePackage;
  private JTextField myBlankLinesAfterPackage;
  private JTextField myBlankLinesBeforeImports;
  private JTextField myBlankLinesAfterImports;
  private JTextField myBlankLinesAroundClass;
  private JTextField myBlankLinesAroundField;
  private JTextField myBlankLinesAroundMethod;
  private JTextField myBlankLinesAfterClassHeader;
  private JTextField myKeepBlankLinesBeforeRBrace;

  private final JPanel myPanel = new JPanel(new GridBagLayout());

  public CodeStyleBlankLinesPanel(CodeStyleSettings settings) {
    super(settings);

    myPanel
      .add(createKeepBlankLinesPanel(),
           new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 4), 0, 0));
    myPanel
      .add(createBlankLinesPanel(),
           new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 4), 0, 0));

    final JPanel previewPanel = createPreviewPanel();
    myPanel
      .add(previewPanel,
           new GridBagConstraints(1, 0, 1, 2, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 4), 0, 0));

    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);

  }

  private JPanel createBlankLinesPanel() {
    OptionGroup optionGroup = new OptionGroup("Blank Lines");

    myBlankLinesBeforePackage = createTextField();
    optionGroup.add(new JLabel("Before package statement:"), myBlankLinesBeforePackage);

    myBlankLinesAfterPackage = createTextField();
    optionGroup.add(new JLabel("After package statement:"), myBlankLinesAfterPackage);

    myBlankLinesBeforeImports = createTextField();
    optionGroup.add(new JLabel("Before imports:"), myBlankLinesBeforeImports);

    myBlankLinesAfterImports = createTextField();
    optionGroup.add(new JLabel("After imports:"), myBlankLinesAfterImports);

    myBlankLinesAroundClass = createTextField();
    optionGroup.add(new JLabel("Around class:"), myBlankLinesAroundClass);

    myBlankLinesAroundField = createTextField();
    optionGroup.add(new JLabel("Around field:"), myBlankLinesAroundField);

    myBlankLinesAroundMethod = createTextField();
    optionGroup.add(new JLabel("Around method:"), myBlankLinesAroundMethod);

    myBlankLinesAfterClassHeader = createTextField();
    optionGroup.add(new JLabel("After class header:  "), myBlankLinesAfterClassHeader);

    return optionGroup.createPanel();
  }

  private JPanel createKeepBlankLinesPanel() {
    OptionGroup optionGroup = new OptionGroup("Keep Blank Lines");

    myKeepBlankLinesInDeclarations = createTextField();
    optionGroup.add(new JLabel("In declarations:"), myKeepBlankLinesInDeclarations);

    myKeepBlankLinesInCode = createTextField();
    optionGroup.add(new JLabel("In code:"), myKeepBlankLinesInCode);

    myKeepBlankLinesBeforeRBrace = createTextField();
    optionGroup.add(new JLabel("Before '}': "), myKeepBlankLinesBeforeRBrace);

    return optionGroup.createPanel();
  }

  private JPanel createPreviewPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder("Preview"));
    panel.setPreferredSize(new Dimension(200, 0));
    return panel;
  }

  protected LexerEditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return HighlighterFactory.createJavaHighlighter(scheme, LanguageLevel.HIGHEST);
  }

  protected String getPreviewText() {
    return "/*\n" +
           " * This is a sample file.\n" +
           " */\n" +
           "package com.intellij.samples;\n" +
           "import com.intellij.idea.Main;\n" +
           "import javax.swing.*;\n" +
           "import java.util.Vector;\n" +
           "public class Foo {\n" +
           "  private int field1;\n" +
           "  private int field2;\n" +
           "  public void foo1() {\n\n" +
           "  }\n" +
           "  public void foo2() {\n" +
           "  }\n\n" +
           "}";
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    myKeepBlankLinesInDeclarations.setText("" + settings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    myKeepBlankLinesInCode.setText("" + settings.KEEP_BLANK_LINES_IN_CODE);
    myKeepBlankLinesBeforeRBrace.setText("" + settings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    myBlankLinesBeforePackage.setText("" + settings.BLANK_LINES_BEFORE_PACKAGE);
    myBlankLinesAfterPackage.setText("" + settings.BLANK_LINES_AFTER_PACKAGE);
    myBlankLinesBeforeImports.setText("" + settings.BLANK_LINES_BEFORE_IMPORTS);
    myBlankLinesAfterImports.setText("" + settings.BLANK_LINES_AFTER_IMPORTS);
    myBlankLinesAroundClass.setText("" + settings.BLANK_LINES_AROUND_CLASS);
    myBlankLinesAroundField.setText("" + settings.BLANK_LINES_AROUND_FIELD);
    myBlankLinesAroundMethod.setText("" + settings.BLANK_LINES_AROUND_METHOD);
    myBlankLinesAfterClassHeader.setText("" + settings.BLANK_LINES_AFTER_CLASS_HEADER);

  }

  public void apply(CodeStyleSettings settings) {
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = getValue(myKeepBlankLinesInDeclarations);
    settings.KEEP_BLANK_LINES_IN_CODE = getValue(myKeepBlankLinesInCode);
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = getValue(myKeepBlankLinesBeforeRBrace);
    settings.BLANK_LINES_BEFORE_PACKAGE = getValue(myBlankLinesBeforePackage);
    settings.BLANK_LINES_AFTER_PACKAGE = getValue(myBlankLinesAfterPackage);
    settings.BLANK_LINES_BEFORE_IMPORTS = getValue(myBlankLinesBeforeImports);
    settings.BLANK_LINES_AFTER_IMPORTS = getValue(myBlankLinesAfterImports);
    settings.BLANK_LINES_AROUND_CLASS = getValue(myBlankLinesAroundClass);
    settings.BLANK_LINES_AROUND_FIELD = getValue(myBlankLinesAroundField);
    settings.BLANK_LINES_AROUND_METHOD = getValue(myBlankLinesAroundMethod);
    settings.BLANK_LINES_AFTER_CLASS_HEADER = getValue(myBlankLinesAfterClassHeader);

  }

  public boolean isModified(CodeStyleSettings settings) {
    boolean isModified;
    isModified = settings.KEEP_BLANK_LINES_IN_DECLARATIONS != getValue(myKeepBlankLinesInDeclarations);
    isModified |= settings.KEEP_BLANK_LINES_IN_CODE != getValue(myKeepBlankLinesInCode);
    isModified |= settings.KEEP_BLANK_LINES_BEFORE_RBRACE != getValue(myKeepBlankLinesBeforeRBrace);
    isModified |= settings.BLANK_LINES_BEFORE_PACKAGE != getValue(myBlankLinesBeforePackage);
    isModified |= settings.BLANK_LINES_AFTER_PACKAGE != getValue(myBlankLinesAfterPackage);
    isModified |= settings.BLANK_LINES_BEFORE_IMPORTS != getValue(myBlankLinesBeforeImports);
    isModified |= settings.BLANK_LINES_AFTER_IMPORTS != getValue(myBlankLinesAfterImports);
    isModified |= settings.BLANK_LINES_AROUND_CLASS != getValue(myBlankLinesAroundClass);
    isModified |= settings.BLANK_LINES_AROUND_FIELD != getValue(myBlankLinesAroundField);
    isModified |= settings.BLANK_LINES_AROUND_METHOD != getValue(myBlankLinesAroundMethod);
    isModified |= settings.BLANK_LINES_AFTER_CLASS_HEADER != getValue(myBlankLinesAfterClassHeader);
    return isModified;

  }

  private static int getValue(JTextField textField) {
    int ret = 0;
    try {
      ret = Integer.parseInt(textField.getText());
      if (ret < 0) {
        ret = 0;
      }
      if (ret > 10) {
        ret = 10;
      }
    }
    catch (NumberFormatException e) {
    }
    return ret;
  }

  private JTextField createTextField() {

    return new JTextField(6);
  }

  protected int getRightMargin() {
    return 37;
  }

  protected FileType getFileType() {
    return StdFileTypes.JAVA;
  }

  public JComponent getPanel() {
    return myPanel;
  }

}