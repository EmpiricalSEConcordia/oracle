////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2015 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle.checks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

import org.apache.commons.beanutils.ConversionException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Maintains a set of check suppressions from {@link SuppressWarnings}
 * annotations.
 * @author Trevor Robinson
 */
public class SuppressWarningsHolder
    extends Check
{
    /**
     * Optional prefix for warning suppressions that are only intended to be
     * recognized by checkstyle. For instance, to suppress {@code
     * FallThroughCheck} only in checkstyle (and not in javac), use the
     * suppression {@code "checkstyle:fallthrough"}. To suppress the warning in
     * both tools, just use {@code "fallthrough"}.
     */
    public static final String CHECKSTYLE_PREFIX = "checkstyle:";

    /** java.lang namespace prefix, which is stripped from SuppressWarnings */
    private static final String JAVA_LANG_PREFIX = "java.lang.";

    /** suffix to be removed from subclasses of Check */
    private static final String CHECK_SUFFIX = "Check";

    /** a map from check source names to suppression aliases */
    private static final Map<String, String> CHECK_ALIAS_MAP = new HashMap<>();

    /**
     * a thread-local holder for the list of suppression entries for the last
     * file parsed
     */
    private static final ThreadLocal<List<Entry>> ENTRIES = new ThreadLocal<>();

    /** records a particular suppression for a region of a file */
    private static class Entry
    {
        /** the source name of the suppressed check */
        private final String checkName;
        /** the suppression region for the check */
        private final int firstLine, firstColumn, lastLine, lastColumn;

        /**
         * Constructs a new suppression region entry.
         * @param checkName the source name of the suppressed check
         * @param firstLine the first line of the suppression region
         * @param firstColumn the first column of the suppression region
         * @param lastLine the last line of the suppression region
         * @param lastColumn the last column of the suppression region
         */
        public Entry(String checkName, int firstLine, int firstColumn,
            int lastLine, int lastColumn)
        {
            this.checkName = checkName;
            this.firstLine = firstLine;
            this.firstColumn = firstColumn;
            this.lastLine = lastLine;
            this.lastColumn = lastColumn;
        }

        /** @return the source name of the suppressed check */
        public String getCheckName()
        {
            return checkName;
        }

        /** @return the first line of the suppression region */
        public int getFirstLine()
        {
            return firstLine;
        }

        /** @return the first column of the suppression region */
        public int getFirstColumn()
        {
            return firstColumn;
        }

        /** @return the last line of the suppression region */
        public int getLastLine()
        {
            return lastLine;
        }

        /** @return the last column of the suppression region */
        public int getLastColumn()
        {
            return lastColumn;
        }
    }

    /**
     * Returns the default alias for the source name of a check, which is the
     * source name in lower case with any dotted prefix or "Check" suffix
     * removed.
     * @param sourceName the source name of the check (generally the class
     *        name)
     * @return the default alias for the given check
     */
    public static String getDefaultAlias(String sourceName)
    {
        final int startIndex = sourceName.lastIndexOf('.') + 1;
        int endIndex = sourceName.length();
        if (sourceName.endsWith(CHECK_SUFFIX)) {
            endIndex -= CHECK_SUFFIX.length();
        }
        return sourceName.substring(startIndex, endIndex).toLowerCase();
    }

    /**
     * Returns the alias for the source name of a check. If an alias has been
     * explicitly registered via {@link #registerAlias(String, String)}, that
     * alias is returned; otherwise, the default alias is used.
     * @param sourceName the source name of the check (generally the class
     *        name)
     * @return the current alias for the given check
     */
    public static String getAlias(String sourceName)
    {
        String checkAlias = CHECK_ALIAS_MAP.get(sourceName);
        if (checkAlias == null) {
            checkAlias = getDefaultAlias(sourceName);
        }
        return checkAlias;
    }

    /**
     * Registers an alias for the source name of a check.
     * @param sourceName the source name of the check (generally the class
     *        name)
     * @param checkAlias the alias used in {@link SuppressWarnings} annotations
     */
    public static void registerAlias(String sourceName, String checkAlias)
    {
        CHECK_ALIAS_MAP.put(sourceName, checkAlias);
    }

    /**
     * Registers a list of source name aliases based on a comma-separated list
     * of {@code source=alias} items, such as {@code
     * com.puppycrawl.tools.checkstyle.checks.sizes.ParameterNumberCheck=
     * paramnum}.
     * @param aliasList the list of comma-separated alias assigments
     */
    public void setAliasList(String aliasList)
    {
        for (String sourceAlias : aliasList.split(",")) {
            final int index = sourceAlias.indexOf("=");
            if (index > 0) {
                registerAlias(sourceAlias.substring(0, index), sourceAlias
                    .substring(index + 1));
            }
            else if (sourceAlias.length() > 0) {
                throw new ConversionException(
                    "'=' expected in alias list item: " + sourceAlias);
            }
        }
    }

    /**
     * Checks for a suppression of a check with the given source name and
     * location in the last file processed.
     * @param sourceName the source name of the check
     * @param line the line number of the check
     * @param column the column number of the check
     * @return whether the check with the given name is suppressed at the given
     *         source location
     */
    public static boolean isSuppressed(String sourceName, int line,
        int column)
    {
        final List<Entry> entries = ENTRIES.get();
        final String checkAlias = getAlias(sourceName);
        for (Entry entry : entries) {
            final boolean afterStart =
                entry.getFirstLine() < line
                    || entry.getFirstLine() == line && entry
                        .getFirstColumn() <= column;
            final boolean beforeEnd =
                entry.getLastLine() > line
                    || entry.getLastLine() == line && entry
                        .getLastColumn() >= column;
            final boolean nameMatches =
                entry.getCheckName().equals(checkAlias);
            if (afterStart && beforeEnd && nameMatches) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int[] getDefaultTokens()
    {
        return new int[] {TokenTypes.ANNOTATION};
    }

    @Override
    public void beginTree(DetailAST rootAST)
    {
        ENTRIES.set(new LinkedList<Entry>());
    }

    @Override
    public void visitToken(DetailAST ast)
    {
        // check whether annotation is SuppressWarnings
        // expected children: AT ( IDENT | DOT ) LPAREN <values> RPAREN
        String identifier = getIdentifier(getNthChild(ast, 1));
        if (identifier.startsWith(JAVA_LANG_PREFIX)) {
            identifier = identifier.substring(JAVA_LANG_PREFIX.length());
        }
        if ("SuppressWarnings".equals(identifier)) {

            // get values of annotation
            List<String> values = null;
            final DetailAST lparenAST = ast.findFirstToken(TokenTypes.LPAREN);
            if (lparenAST != null) {
                final DetailAST nextAST = lparenAST.getNextSibling();
                if (nextAST != null) {
                    final int nextType = nextAST.getType();
                    switch (nextType) {
                        case TokenTypes.EXPR:
                        case TokenTypes.ANNOTATION_ARRAY_INIT:
                            values = getAnnotationValues(nextAST);
                            break;

                        case TokenTypes.ANNOTATION_MEMBER_VALUE_PAIR:
                            // expected children: IDENT ASSIGN ( EXPR |
                            // ANNOTATION_ARRAY_INIT )
                            values = getAnnotationValues(getNthChild(nextAST, 2));
                            break;

                        case TokenTypes.RPAREN:
                            // no value present (not valid Java)
                            break;

                        default:
                            // unknown annotation value type (new syntax?)
                    }
                }
            }
            if (values == null) {
                log(ast, "suppress.warnings.missing.value");
                return;
            }

            // get target of annotation
            DetailAST targetAST = null;
            DetailAST parentAST = ast.getParent();
            if (parentAST != null) {
                switch (parentAST.getType()) {
                    case TokenTypes.MODIFIERS:
                    case TokenTypes.ANNOTATIONS:
                        parentAST = parentAST.getParent();
                        if (parentAST != null) {
                            switch (parentAST.getType()) {
                                case TokenTypes.ANNOTATION_DEF:
                                case TokenTypes.PACKAGE_DEF:
                                case TokenTypes.CLASS_DEF:
                                case TokenTypes.INTERFACE_DEF:
                                case TokenTypes.ENUM_DEF:
                                case TokenTypes.ENUM_CONSTANT_DEF:
                                case TokenTypes.CTOR_DEF:
                                case TokenTypes.METHOD_DEF:
                                case TokenTypes.PARAMETER_DEF:
                                case TokenTypes.VARIABLE_DEF:
                                    targetAST = parentAST;
                                    break;

                                default:
                                    // unexpected target type
                            }
                        }
                        break;

                    default:
                        // unexpected container type
                }
            }
            if (targetAST == null) {
                log(ast, "suppress.warnings.invalid.target");
                return;
            }

            // get text range of target
            final int firstLine = targetAST.getLineNo();
            final int firstColumn = targetAST.getColumnNo();
            final DetailAST nextAST = targetAST.getNextSibling();
            final int lastLine, lastColumn;
            if (nextAST != null) {
                lastLine = nextAST.getLineNo();
                lastColumn = nextAST.getColumnNo() - 1;
            }
            else {
                lastLine = Integer.MAX_VALUE;
                lastColumn = Integer.MAX_VALUE;
            }

            // add suppression entries for listed checks
            final List<Entry> entries = ENTRIES.get();
            if (entries != null) {
                for (String value : values) {
                    // strip off the checkstyle-only prefix if present
                    if (value.startsWith(CHECKSTYLE_PREFIX)) {
                        value = value.substring(CHECKSTYLE_PREFIX.length());
                    }
                    entries.add(new Entry(value, firstLine, firstColumn,
                        lastLine, lastColumn));
                }
            }
        }
    }

    /**
     * Returns the n'th child of an AST node.
     * @param ast the AST node to get the child of
     * @param index the index of the child to get
     * @return the n'th child of the given AST node, or {@code null} if none
     */
    private static DetailAST getNthChild(DetailAST ast, int index)
    {
        DetailAST child = ast.getFirstChild();
        if (child != null) {
            for (int i = 0; i < index && child != null; ++i) {
                child = child.getNextSibling();
            }
        }
        return child;
    }

    /**
     * Returns the Java identifier represented by an AST.
     * @param ast an AST node for an IDENT or DOT
     * @return the Java identifier represented by the given AST subtree
     * @throws IllegalArgumentException if the AST is invalid
     */
    private static String getIdentifier(DetailAST ast)
    {
        if (ast != null) {
            if (ast.getType() == TokenTypes.IDENT) {
                return ast.getText();
            }
            else if (ast.getType() == TokenTypes.DOT) {
                return getIdentifier(ast.getFirstChild()) + "."
                    + getIdentifier(ast.getLastChild());
            }
        }
        throw new IllegalArgumentException("Identifier AST expected: " + ast);
    }

    /**
     * Returns the literal string expression represented by an AST.
     * @param ast an AST node for an EXPR
     * @return the Java string represented by the given AST expression
     * @throws IllegalArgumentException if the AST is invalid
     */
    private static String getStringExpr(DetailAST ast)
    {
        if (ast != null && ast.getType() == TokenTypes.EXPR) {
            final DetailAST firstChild = ast.getFirstChild();
            switch (firstChild.getType()) {
                case TokenTypes.STRING_LITERAL:
                    // NOTE: escaped characters are not unescaped
                    final String quotedText = firstChild.getText();
                    return quotedText.substring(1, quotedText.length() - 1);
                case TokenTypes.IDENT:
                    return firstChild.getText();
                default:
                    throw new IllegalArgumentException("String literal AST expected: "
                            + firstChild);
            }
        }
        throw new IllegalArgumentException("Expression AST expected: " + ast);
    }

    /**
     * Returns the annotation values represented by an AST.
     * @param ast an AST node for an EXPR or ANNOTATION_ARRAY_INIT
     * @return the list of Java string represented by the given AST for an
     *         expression or annotation array initializer
     * @throws IllegalArgumentException if the AST is invalid
     */
    private static List<String> getAnnotationValues(DetailAST ast)
    {
        switch (ast.getType()) {
            case TokenTypes.EXPR:
                return ImmutableList.of(getStringExpr(ast));

            case TokenTypes.ANNOTATION_ARRAY_INIT:
                final List<String> valueList = Lists.newLinkedList();
                DetailAST childAST = ast.getFirstChild();
                while (childAST != null) {
                    if (childAST.getType() == TokenTypes.EXPR) {
                        valueList.add(getStringExpr(childAST));
                    }
                    childAST = childAST.getNextSibling();
                }
                return valueList;

            default:
        }
        throw new IllegalArgumentException(
            "Expression or annotation array initializer AST expected: " + ast);
    }
}
