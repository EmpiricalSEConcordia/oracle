////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2014  Oliver Burn
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

package com.puppycrawl.tools.checkstyle.checks.imports;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.checks.AbstractOptionCheck;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <ul>
 * <li>groups imports: ensures that groups of imports come in a specific order
 * (e.g., java. comes first, javax. comes second, then everything else)</li>
 * <li>adds a separation between groups : ensures that a blank line sit between
 * each group</li>
 * <li>sorts imports inside each group: ensures that imports within each group
 * are in lexicographic order</li>
 * <li>sorts according to case: ensures that the comparison between import is
 * case sensitive</li>
 * <li>groups static imports: ensures that static imports are at the top (or the
 * bottom) of all the imports, or above (or under) each group, or are treated
 * like non static imports (@see {@link ImportOrderOption}</li>
 * </ul>
 *
 * <p>
 * Example:
 * </p>
 *
 * <pre>
 *  &lt;module name=&quot;ImportOrder&quot;&gt;
 *    &lt;property name=&quot;groups&quot; value=&quot;java,javax&quot;/&gt;
 *    &lt;property name=&quot;ordered&quot; value=&quot;true&quot;/&gt;
 *    &lt;property name=&quot;caseSensitive&quot; value=&quot;false&quot;/&gt;
 *    &lt;property name=&quot;option&quot; value=&quot;above&quot;/&gt;
 *  &lt;/module&gt;
 * </pre>
 *
 * <p>
 * Group descriptions enclosed in slashes are interpreted as regular
 * expressions. If multiple groups match, the one matching a longer
 * substring of the imported name will take precedence, with ties
 * broken first in favor of earlier matches and finally in favor of
 * the first matching group.
 * </p>
 *
 * <p>
 * There is always a wildcard group to which everything not in a named group
 * belongs. If an import does not match a named group, the group belongs to
 * this wildcard group. The wildcard group position can be specified using the
 * {@code *} character.
 * </p>
 *
 * <p>
 * Defaults:
 * </p>
 * <ul>
 * <li>import groups: none</li>
 * <li>separation: false</li>
 * <li>ordered: true</li>
 * <li>case sensitive: true</li>
 * <li>static import: under</li>
 * </ul>
 *
 * <p>
 * Compatible with Java 1.5 source.
 * </p>
 *
 * @author Bill Schneider
 * @author o_sukhodolsky
 * @author David DIDIER
 * @author Steve McKay
 */
public class ImportOrderCheck
    extends AbstractOptionCheck<ImportOrderOption>
{

    /** the special wildcard that catches all remaining groups. */
    private static final String WILDCARD_GROUP_NAME = "*";

    /** List of import groups specified by the user. */
    private Pattern[] groups = new Pattern[0];
    /** Require imports in group be separated. */
    private boolean separated;
    /** Require imports in group. */
    private boolean ordered = true;
    /** Should comparison be case sensitive. */
    private boolean caseSensitive = true;

    /** Last imported group. */
    private int lastGroup;
    /** Line number of last import. */
    private int lastImportLine;
    /** Name of last import. */
    private String lastImport;
    /** If last import was static. */
    private boolean lastImportStatic;
    /** Whether there was any imports. */
    private boolean beforeFirstImport;

    /**
     * Groups static imports under each group.
     */
    public ImportOrderCheck()
    {
        super(ImportOrderOption.UNDER, ImportOrderOption.class);
    }

    /**
     * Sets the list of package groups and the order they should occur in the
     * file.
     *
     * @param packageGroups a comma-separated list of package names/prefixes.
     */
    public void setGroups(String[] packageGroups)
    {
        groups = new Pattern[packageGroups.length];

        for (int i = 0; i < packageGroups.length; i++) {
            String pkg = packageGroups[i];
            Pattern grp;

            // if the pkg name is the wildcard, make it match zero chars
            // from any name, so it will always be used as last resort.
            if (WILDCARD_GROUP_NAME.equals(pkg)) {
                grp = Pattern.compile(""); // matches any package
            }
            else if (pkg.startsWith("/")) {
                if (!pkg.endsWith("/")) {
                    throw new IllegalArgumentException("Invalid group");
                }
                pkg = pkg.substring(1, pkg.length() - 1);
                grp = Pattern.compile(pkg);
            }
            else {
                if (!pkg.endsWith(".")) {
                    pkg = pkg + ".";
                }
                grp = Pattern.compile("^" + Pattern.quote(pkg));
            }

            groups[i] = grp;
        }
    }

    /**
     * Sets whether or not imports should be ordered within any one group of
     * imports.
     *
     * @param ordered
     *            whether lexicographic ordering of imports within a group
     *            required or not.
     */
    public void setOrdered(boolean ordered)
    {
        this.ordered = ordered;
    }

    /**
     * Sets whether or not groups of imports must be separated from one another
     * by at least one blank line.
     *
     * @param separated
     *            whether groups should be separated by oen blank line.
     */
    public void setSeparated(boolean separated)
    {
        this.separated = separated;
    }

    /**
     * Sets whether string comparison should be case sensitive or not.
     *
     * @param caseSensitive
     *            whether string comparison should be case sensitive.
     */
    public void setCaseSensitive(boolean caseSensitive)
    {
        this.caseSensitive = caseSensitive;
    }

    @Override
    public int[] getDefaultTokens()
    {
        return new int[] {TokenTypes.IMPORT, TokenTypes.STATIC_IMPORT};
    }

    @Override
    public void beginTree(DetailAST rootAST)
    {
        lastGroup = Integer.MIN_VALUE;
        lastImportLine = Integer.MIN_VALUE;
        lastImport = "";
        lastImportStatic = false;
        beforeFirstImport = true;
    }

    @Override
    public void visitToken(DetailAST ast)
    {
        final FullIdent ident;
        final boolean isStatic;

        if (ast.getType() == TokenTypes.IMPORT) {
            ident = FullIdent.createFullIdentBelow(ast);
            isStatic = false;
        }
        else {
            ident = FullIdent.createFullIdent(ast.getFirstChild()
                    .getNextSibling());
            isStatic = true;
        }

        switch (getAbstractOption()) {
        case TOP:
            if (!isStatic && lastImportStatic) {
                lastGroup = Integer.MIN_VALUE;
                lastImport = "";
            }
            // no break;

        case ABOVE:
            // previous non-static but current is static
            doVisitToken(ident, isStatic, (!lastImportStatic && isStatic));
            break;

        case INFLOW:
            // previous argument is useless here
            doVisitToken(ident, isStatic, true);
            break;

        case BOTTOM:
            if (isStatic && !lastImportStatic) {
                lastGroup = Integer.MIN_VALUE;
                lastImport = "";
            }
            // no break;

        case UNDER:
            // previous static but current is non-static
            doVisitToken(ident, isStatic, (lastImportStatic && !isStatic));
            break;

        default:
            break;
        }

        lastImportLine = ast.findFirstToken(TokenTypes.SEMI).getLineNo();
        lastImportStatic = isStatic;
        beforeFirstImport = false;
    }

    /**
     * Shares processing...
     *
     * @param ident the import to process.
     * @param isStatic whether the token is static or not.
     * @param previous previous non-static but current is static (above), or
     *                  previous static but current is non-static (under).
     */
    private void doVisitToken(FullIdent ident, boolean isStatic,
            boolean previous)
    {
        if (ident != null) {
            final String name = ident.getText();
            final int groupIdx = getGroupNumber(name);
            final int line = ident.getLineNo();

            if (groupIdx > lastGroup) {
                if (!beforeFirstImport && separated) {
                    // This check should be made more robust to handle
                    // comments and imports that span more than one line.
                    if ((line - lastImportLine) < 2) {
                        log(line, "import.separation", name);
                    }
                }
            }
            else if (groupIdx == lastGroup) {
                doVisitTokenInSameGroup(isStatic, previous, name, line);
            }
            else {
                log(line, "import.ordering", name);
            }

            lastGroup = groupIdx;
            lastImport = name;
        }
    }

    /**
     * Shares processing...
     *
     * @param isStatic whether the token is static or not.
     * @param previous previous non-static but current is static (above), or
     *    previous static but current is non-static (under).
     * @param name the name of the current import.
     * @param line the line of the current import.
     */
    private void doVisitTokenInSameGroup(boolean isStatic,
            boolean previous, String name, int line)
    {
        if (!ordered) {
            return;
        }

        if (getAbstractOption().equals(ImportOrderOption.INFLOW)) {
            // out of lexicographic order
            if (compare(lastImport, name, caseSensitive) > 0) {
                log(line, "import.ordering", name);
            }
        }
        else {
            final boolean shouldFireError =
                // current and previous static or current and
                // previous non-static
                (!(lastImportStatic ^ isStatic)
                &&
                // and out of lexicographic order
                (compare(lastImport, name, caseSensitive) > 0))
                ||
                // previous non-static but current is static (above)
                // or
                // previous static but current is non-static (under)
                previous;

            if (shouldFireError) {
                log(line, "import.ordering", name);
            }
        }
    }

    /**
     * Finds out what group the specified import belongs to.
     *
     * @param name the import name to find.
     * @return group number for given import name.
     */
    private int getGroupNumber(String name)
    {
        int bestIndex = groups.length;
        int bestLength = -1;
        int bestPos = 0;

        // find out what group this belongs in
        // loop over groups and get index
        for (int i = 0; i < groups.length; i++) {
            final Matcher matcher = groups[i].matcher(name);
            while (matcher.find()) {
                final int length = matcher.end() - matcher.start();
                if ((length > bestLength)
                    || ((length == bestLength) && (matcher.start() < bestPos)))
                {
                    bestIndex = i;
                    bestLength = length;
                    bestPos = matcher.start();
                }
            }
        }

        return bestIndex;
    }

    /**
     * Compares two strings.
     *
     * @param string1
     *            the first string.
     * @param string2
     *            the second string.
     * @param caseSensitive
     *            whether the comparison is case sensitive.
     * @return the value <code>0</code> if string1 is equal to string2; a value
     *         less than <code>0</code> if string1 is lexicographically less
     *         than the string2; and a value greater than <code>0</code> if
     *         string1 is lexicographically greater than string2.
     */
    private int compare(String string1, String string2,
            boolean caseSensitive)
    {
        if (caseSensitive) {
            return string1.compareTo(string2);
        }

        return string1.compareToIgnoreCase(string2);
    }
}
