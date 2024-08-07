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
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.api.Utils;

import org.apache.commons.beanutils.ConversionException;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

/**
 * Detects uncommented main methods. Basically detects
 * any main method, since if it is detectable
 * that means it is uncommented.
 *
 * <pre class="body">
 * &lt;module name=&quot;UncommentedMain&quot;/&gt;
 * </pre>
 *
 * @author Michael Yui
 * @author o_sukhodolsky
 */
public class UncommentedMainCheck
    extends Check
{
    /** the pattern to exclude classes from the check */
    private String mExcludedClasses = "^$";
    /** compiled regexp to exclude classes from check */
    private RE mExcludedClassesRE = Utils.createRE(mExcludedClasses);
    /** current class name */
    private String mCurrentClass;
    /** current package */
    private FullIdent mPackage;
    /** class definition depth */
    private int mClassDepth;

    /**
     * Set the excluded classes pattern.
     * @param aExcludedClasses a <code>String</code> value
     * @throws ConversionException unable to parse aExcludedClasses
     */
    public void setExcludedClasses(String aExcludedClasses)
        throws ConversionException
    {
        try {
            mExcludedClasses = aExcludedClasses;
            mExcludedClassesRE = Utils.getRE(mExcludedClasses);
        }
        catch (RESyntaxException e) {
            throw new ConversionException("unable to parse "
                                          + mExcludedClasses,
                                          e);
        }
    }

    /** @see Check */
    public int[] getDefaultTokens()
    {
        return new int[] {
            TokenTypes.METHOD_DEF,
            TokenTypes.CLASS_DEF,
            TokenTypes.PACKAGE_DEF,
        };
    }

    /** @see Check */
    public int[] getRequiredTokens()
    {
        return getDefaultTokens();
    }

    /** @see Check */
    public void beginTree(DetailAST aRootAST)
    {
        mPackage = FullIdent.createFullIdent(null);
        mCurrentClass = null;
        mClassDepth = 0;
    }

    /** @see Check */
    public void leaveToken(DetailAST aAst)
    {
        if (aAst.getType() == TokenTypes.CLASS_DEF) {
            if (mClassDepth == 1) {
                mCurrentClass = null;
            }
            mClassDepth--;
        }
    }

    /** @see Check */
    public void visitToken(DetailAST aAst)
    {
        switch (aAst.getType()) {
        case TokenTypes.PACKAGE_DEF:
            visitPackageDef(aAst);
            break;
        case TokenTypes.CLASS_DEF:
            visitClassDef(aAst);
            break;
        case TokenTypes.METHOD_DEF:
            visitMethodDef(aAst);
            break;
        default:
            throw new IllegalStateException(aAst.toString());
        }
    }

    /**
     * Sets current package.
     * @param aPackage node for package definition
     */
    private void visitPackageDef(DetailAST aPackage)
    {
        mPackage =
            FullIdent.createFullIdent((DetailAST) aPackage.getFirstChild());
    }

    /**
     * If not inner class then change current class name.
     * @param aClass node for class definition
     */
    private void visitClassDef(DetailAST aClass)
    {
        // we are not use inner classes because they can not
        // have static methods
        if (mClassDepth == 0) {
            DetailAST ident = aClass.findFirstToken(TokenTypes.IDENT);
            mCurrentClass = mPackage.getText() + "." + ident.getText();
            mClassDepth++;
        }
        return;
    }

    /**
     * Checks method definition if this is
     * <code>public static void main(String[])</code>.
     * @param aMethod method definition node
     */
    private void visitMethodDef(DetailAST aMethod)
    {
        if (mClassDepth != 1) {
            // method in inner class or in interface definition
            return;
        }

        if (checkClassName()
            && checkName(aMethod)
            && checkModifiers(aMethod)
            && checkType(aMethod)
            && checkParams(aMethod))
        {
            log(aMethod.getLineNo(), "uncommented.main");
        }
    }

    /**
     * Checks that current class is not excluded
     * @return true if check passed, false otherwise
     */
    private boolean checkClassName()
    {
        return !mExcludedClassesRE.match(mCurrentClass);
    }

    /**
     * Checks that method name is @quot;main@quot;.
     * @param aMethod the METHOD_DEF node
     * @return true if check passed, false otherwise
     */
    private boolean checkName(DetailAST aMethod)
    {
        DetailAST ident = aMethod.findFirstToken(TokenTypes.IDENT);
        return "main".equals(ident.getText());
    }

    /**
     * Checks that method has final and static modifiers.
     * @param aMethod the METHOD_DEF node
     * @return true if check passed, false otherwise
     */
    private boolean checkModifiers(DetailAST aMethod)
    {
        DetailAST modifiers = aMethod.findFirstToken(TokenTypes.MODIFIERS);

        return modifiers.branchContains(TokenTypes.LITERAL_PUBLIC)
            && modifiers.branchContains(TokenTypes.LITERAL_STATIC);
    }

    /**
     * Checks that return type is <code>void</code>.
     * @param aMethod the METHOD_DEF node
     * @return true if check passed, false otherwise
     */
    private boolean checkType(DetailAST aMethod)
    {
        DetailAST type =
            (DetailAST) aMethod.findFirstToken(TokenTypes.TYPE).getFirstChild();
        return type.getType() == TokenTypes.LITERAL_VOID;
    }

    /**
     * Checks that method has only <code>String[]</code> param
     * @param aMethod the METHOD_DEF node
     * @return true if check passed, false otherwise
     */
    private boolean checkParams(DetailAST aMethod)
    {
        DetailAST params = aMethod.findFirstToken(TokenTypes.PARAMETERS);
        if (params.getChildCount() != 1) {
            return false;
        }
        DetailAST paramType = ((DetailAST) params.getFirstChild())
            .findFirstToken(TokenTypes.TYPE);
        DetailAST arrayDecl =
            paramType.findFirstToken(TokenTypes.ARRAY_DECLARATOR);
        if (arrayDecl == null) {
            return false;
        }

        DetailAST arrayType = (DetailAST) arrayDecl.getFirstChild();

        if (arrayType.getType() == TokenTypes.IDENT
            || arrayType.getType() == TokenTypes.DOT)
        {
            FullIdent type = FullIdent.createFullIdent(arrayType);
            return ("String".equals(type.getText())
                    || "java.lang.String".equals(type.getText()));
        }

        return false;
    }
}
