package com.intellij.ide.highlighter.custom;

import java.util.Set;
import java.util.TreeSet;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public class SyntaxTable implements Cloneable {
  private Set myKeywords1;
  private Set myKeywords2;
  private Set myKeywords3;
  private Set myKeywords4;

  private String myLineComment;
  private String myStartComment;
  private String myEndComment;

  private String myHexPrefix;
  private String myNumPostfixChars;

  private boolean myIgnoreCase;
  private boolean myHasBraces;
  private boolean myHasBrackets;
  private boolean myHasParens;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public SyntaxTable() {
    myKeywords1 = new TreeSet();
    myKeywords2 = new TreeSet();
    myKeywords3 = new TreeSet();
    myKeywords4 = new TreeSet();
  }

  protected Object clone() throws CloneNotSupportedException {
    SyntaxTable cl = (SyntaxTable) super.clone();
    cl.myKeywords1 = new TreeSet(myKeywords1);
    cl.myKeywords2 = new TreeSet(myKeywords2);
    cl.myKeywords3 = new TreeSet(myKeywords3);
    cl.myKeywords4 = new TreeSet(myKeywords4);

    return cl;
  }

  // -------------------------------------------------------------------------
  // Public interface
  // -------------------------------------------------------------------------

  public void addKeyword1(String keyword) {
    myKeywords1.add(keyword);
  }

  public Set getKeywords1() {
    return myKeywords1;
  }

  public void addKeyword2(String keyword) {
    myKeywords2.add(keyword);
  }

  public Set getKeywords2() {
    return myKeywords2;
  }

  public void addKeyword3(String keyword) {
    myKeywords3.add(keyword);
  }

  public Set getKeywords3() {
    return myKeywords3;
  }

  public void addKeyword4(String keyword) {
    myKeywords4.add(keyword);
  }

  public Set getKeywords4() {
    return myKeywords4;
  }

  public String getLineComment() {
    return myLineComment;
  }

  public void setLineComment(String lineComment) {
    this.myLineComment = lineComment;
  }

  public String getStartComment() {
    return myStartComment;
  }

  public void setStartComment(String startComment) {
    this.myStartComment = startComment;
  }

  public String getEndComment() {
    return myEndComment;
  }

  public void setEndComment(String endComment) {
    this.myEndComment = endComment;
  }

  public String getHexPrefix() {
    return myHexPrefix;
  }

  public void setHexPrefix(String hexPrefix) {
    this.myHexPrefix = hexPrefix;
  }

  public String getNumPostfixChars() {
    return myNumPostfixChars;
  }

  public void setNumPostfixChars(String numPostfixChars) {
    this.myNumPostfixChars = numPostfixChars;
  }

  public boolean isIgnoreCase() {
    return myIgnoreCase;
  }

  public void setIgnoreCase(boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
  }

  public boolean isHasBraces() {
    return myHasBraces;
  }

  public void setHasBraces(boolean hasBraces) {
    myHasBraces = hasBraces;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SyntaxTable)) return false;

    final SyntaxTable syntaxTable = (SyntaxTable)o;

    if (myIgnoreCase != syntaxTable.myIgnoreCase) return false;
    if (myEndComment != null ? !myEndComment.equals(syntaxTable.myEndComment) : syntaxTable.myEndComment != null) return false;
    if (myHexPrefix != null ? !myHexPrefix.equals(syntaxTable.myHexPrefix) : syntaxTable.myHexPrefix != null) return false;
    if (!myKeywords1.equals(syntaxTable.myKeywords1)) return false;
    if (!myKeywords2.equals(syntaxTable.myKeywords2)) return false;
    if (!myKeywords3.equals(syntaxTable.myKeywords3)) return false;
    if (!myKeywords4.equals(syntaxTable.myKeywords4)) return false;
    if (myLineComment != null ? !myLineComment.equals(syntaxTable.myLineComment) : syntaxTable.myLineComment != null) return false;
    if (myNumPostfixChars != null ? !myNumPostfixChars.equals(syntaxTable.myNumPostfixChars) : syntaxTable.myNumPostfixChars != null) return false;
    if (myStartComment != null ? !myStartComment.equals(syntaxTable.myStartComment) : syntaxTable.myStartComment != null) return false;

    if (myHasBraces != syntaxTable.myHasBraces) return false;
    if (myHasBrackets != syntaxTable.myHasBrackets) return false;
    if (myHasParens != syntaxTable.myHasParens) return false;

    return true;
  }

  public int hashCode() {
    return myKeywords1.hashCode();
  }

  public boolean isHasBrackets() {
    return myHasBrackets;
  }

  public boolean isHasParens() {
    return myHasParens;
  }

  public void setHasBrackets(boolean hasBrackets) {
    myHasBrackets = hasBrackets;
  }

  public void setHasParens(boolean hasParens) {
    myHasParens = hasParens;
  }
}
