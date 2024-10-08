////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2002  Oliver Burn
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

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * <p>
 * Checks that there is no whitespace after a token.
 * More specifically, it checks that it is not followed by whitespace,
 * or (if linebreaks are allowed) all characters on the line after are
 * whitespace. To allow linebreaks afer a token, set property
 * allowLineBreaks to true.
 * </p>
  * <p> By default the check will check the following operators:
 *  {@link TokenTypes#ARRAY_INIT ARRAY_INIT},
 *  {@link TokenTypes#BNOT BNOT},
 *  {@link TokenTypes#DEC DEC},
 *  {@link TokenTypes#DOT DOT},
 *  {@link TokenTypes#INC INC},
 *  {@link TokenTypes#LNOT LNOT},
 *  {@link TokenTypes#UNARY_MINUS UNARY_MINUS},
 *  {@link TokenTypes#UNARY_PLUS UNARY_PLUS}.
 * </p>
 * <p> 
 * An example of how to configure the check is:
 * </p>
 * <pre>
 * &lt;config name="NoWhitespaceAfterCheck"/&gt;
 * </pre> 
 * <p> An example of how to configure the check to allow linebreaks after
 * a {@link TokenTypes#DOT DOT} token is:
 * <pre>
 * &lt;config name="NoWhitespaceAfterCheck"&gt;
 *     &lt;property name="tokens" value="DOT"/&gt;
 *     &lt;property name="allowLineBreaks" value="true"/&gt;
 * &lt;/config&gt;
 * </pre>
 * @author Rick Giles
 * @author lkuehne
 * @version 1.0
 */
public class NoWhitespaceAfterCheck
    extends Check
{
    /** Whether whitespace is allowed if the AST is at a linebreak */
    private boolean mAllowLineBreaks = true;

    /** @see com.puppycrawl.tools.checkstyle.api.Check */
    public int[] getDefaultTokens()
    {
        return new int[] {
            TokenTypes.ARRAY_INIT,
            TokenTypes.INC,
            TokenTypes.DEC,
            TokenTypes.UNARY_MINUS,
            TokenTypes.UNARY_PLUS,
            TokenTypes.BNOT,
            TokenTypes.LNOT,
            TokenTypes.DOT,
        };
    }
    
    /** @see com.puppycrawl.tools.checkstyle.api.Check */
    public void visitToken(DetailAST aAST)
    {
        final String[] lines = getLines();
        final String line = lines[aAST.getLineNo() - 1];
        final int after = aAST.getColumnNo() + aAST.getText().length();

        if (after >= line.length()
            || Character.isWhitespace(line.charAt(after)))
        {
            boolean flag = !mAllowLineBreaks;
            for (int i = after + 1; !flag && i < line.length(); i++) {
                if (!Character.isWhitespace(line.charAt(i))) {
                    flag = true;
                }
            }
            if (flag) {
                log(aAST.getLineNo(), after, "ws.followed", aAST.getText());
            }
        }
    }

    /**
     * Control whether whitespace is flagged at linebreaks.
     * @param aAllowLineBreaks whether whitespace should be
     * flagged at linebreaks.
     */
    public void setAllowLineBreaks(boolean aAllowLineBreaks)
    {
        mAllowLineBreaks = aAllowLineBreaks;
    }
}
