package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SideBorder2;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredText;

import javax.swing.*;
import java.awt.*;

public class HintUtil {
  public static final Color INFORMATION_COLOR = new Color(253, 254, 226);
  public static final Color QUESTION_COLOR = new Color(181, 208, 251);
  private static final Color ERROR_COLOR = new Color(255, 220, 220);

  private static final Icon INFORMATION_ICON = null;
  private static final Icon QUESTION_ICON = IconLoader.getIcon("/actions/help.png");
  private static final Icon ERROR_ICON = null;

  public static final Color QUESTION_UNDERSCORE_COLOR = Color.black;

  public static JLabel createInformationLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text);
    label.setIcon(INFORMATION_ICON);
//    label.setBorder(BorderFactory.createLineBorder(Color.black));
    label.setBorder(
      BorderFactory.createCompoundBorder(
        new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1),
        BorderFactory.createEmptyBorder(2, 2, 2, 2)
      )
    );
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(INFORMATION_COLOR);
    label.setOpaque(true);

    return label;
  }

  public static JComponent createInformationLabel(SimpleColoredText text) {
    SimpleColoredComponent  highlighted = new SimpleColoredComponent ();

    highlighted.setIcon(INFORMATION_ICON);
    highlighted.setBackground(INFORMATION_COLOR);
    highlighted.setForeground(Color.black);
    highlighted.setFont(getBoldFont());
    text.appendToComponent(highlighted);


    Box box = Box.createHorizontalBox();
    box.setBorder(
      new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1)
    );
    box.setForeground(Color.black);
    box.setBackground(INFORMATION_COLOR);
    box.add(highlighted);
    box.setOpaque(true);

    return box;
  }

  public static JLabel createQuestionLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text);
    label.setIcon(QUESTION_ICON);
//    label.setBorder(BorderFactory.createLineBorder(Color.black));
    label.setBorder(
      BorderFactory.createCompoundBorder(
        new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1),
        BorderFactory.createEmptyBorder(2, 2, 2, 2)
      )
    );
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(QUESTION_COLOR);
    label.setOpaque(true);
    return label;
  }

  public static JLabel createErrorLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text);
    label.setIcon(ERROR_ICON);
//    label.setBorder(BorderFactory.createLineBorder(Color.black));
    label.setBorder(
      BorderFactory.createCompoundBorder(
        new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1),
        BorderFactory.createEmptyBorder(2, 2, 2, 2)
      )
    );
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(ERROR_COLOR);
    label.setOpaque(true);
    return label;
  }

  private static Font getBoldFont() {
    return UIManager.getFont("Label.font").deriveFont(Font.BOLD);
  }
  
  private static class HintLabel extends JLabel {
    public void setText(String s) {
      if (s == null) {
        super.setText(null);
      }
      else {
        super.setText(" " + s + " ");
      }
    }
  }
}