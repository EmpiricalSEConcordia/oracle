////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2004  Oliver Burn
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
package com.puppycrawl.tools.checkstyle.checks.indentation;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Handler for a list of statements.
 *
 * @author jrichard
 */
public class SlistHandler extends BlockParentHandler
{
    /**
     * Construct an instance of this handler with the given indentation check,
     * abstract syntax tree, and parent handler.
     *
     * @param aIndentCheck   the indentation check
     * @param aAst           the abstract syntax tree
     * @param aParent        the parent handler
     */
    public SlistHandler(IndentationCheck aIndentCheck,
        DetailAST aAst, ExpressionHandler aParent)
    {
        super(aIndentCheck, "block", aAst, aParent);
    }

    /**
     * Indentation level suggested for a child element. Children don't have
     * to respect this, but most do.
     *
     * @param aChild  child AST (so suggestion level can differ based on child
     *                  type)
     *
     * @return suggested indentation for child
     */
    public IndentLevel suggestedChildLevel(ExpressionHandler aChild)
    {
        // this is:
        //  switch (var) {
        //     case 3: {
        //        break;
        //     }
        //  }
        //  ... the case SLIST is followed by a user-created SLIST and
        //  preceded by a switch

        // if our parent is a block handler we want to be transparent
        if ((getParent() instanceof BlockParentHandler
                && !(getParent() instanceof SlistHandler))
            || (getParent() instanceof CaseHandler
                && aChild instanceof SlistHandler))
        {
            return getParent().suggestedChildLevel(aChild);
        }
        else {
            return super.suggestedChildLevel(aChild);
        }
    }

    /**
     * Get the child element that is not a list of statements.
     *
     * @return the non-list child element
     */
    protected DetailAST getNonlistChild()
    {
        // blocks always have either block children or they are transparent
        // and aren't checking children at all.  In the later case, the
        // superclass will want to check single children, so when it
        // does tell it we have none.
        return null;
    }

    /**
     * Get the child element representing the list of statements.
     *
     * @return the statement list child
     */
    protected DetailAST getListChild()
    {
        return getMainAst();
    }

    /**
     * Get the left curly brace portion of the expression we are handling.
     *
     * @return the left curly brace expression
     */
    protected DetailAST getLCurly()
    {
        return getMainAst();
    }

    /**
     * Get the right curly brace portion of the expression we are handling.
     *
     * @return the right curly brace expression
     */
    protected DetailAST getRCurly()
    {
        return getMainAst().findFirstToken(TokenTypes.RCURLY);
    }

    /**
     * There is no top level expression for this handler.
     *
     * @return null
     */
    protected DetailAST getToplevelAST()
    {
        return null;
    }

    /**
     * Determine if the expression we are handling has a block parent.
     *
     * @return true if it does, false otherwise
     */
    private boolean hasBlockParent()
    {
        final int parentType = getMainAst().getParent().getType();
        return (parentType == TokenTypes.LITERAL_IF)
            || (parentType == TokenTypes.LITERAL_FOR)
            || (parentType == TokenTypes.LITERAL_WHILE)
            || (parentType == TokenTypes.LITERAL_DO)
            || (parentType == TokenTypes.LITERAL_ELSE)
            || (parentType == TokenTypes.LITERAL_TRY)
            || (parentType == TokenTypes.LITERAL_CATCH)
            || (parentType == TokenTypes.LITERAL_FINALLY)
            || (parentType == TokenTypes.CTOR_DEF)
            || (parentType == TokenTypes.METHOD_DEF)
            || (parentType == TokenTypes.STATIC_INIT);
    }

    /**
     * Check the indentation of the expression we are handling.
     */
    public void checkIndentation()
    {
        // only need to check this if parent is not
        // an if, else, while, do, ctor, method
        if (hasBlockParent()) {
            return;
        }
        super.checkIndentation();
    }
}
