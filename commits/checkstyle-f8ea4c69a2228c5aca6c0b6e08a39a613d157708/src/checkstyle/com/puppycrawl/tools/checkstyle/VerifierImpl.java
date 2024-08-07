////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001  Oliver Burn
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle;

import antlr.Token;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

/**
 * Simple implementation of the Verifier interface. Should really get details
 * of the rules from a configuration file, rather than being hard coded.
 * @author <a href="mailto:oliver@puppycrawl.com">Oliver Burn</a>
 **/
class VerifierImpl
    implements Verifier
{
    // {{{ Data declarations
    /** the pattern to match Javadoc tags that take an argument **/
    private static final String MATCH_JAVADOC_ARG_PAT
        = "@(throws|exception|param)\\s+(\\S+)\\s+\\S";
    /** compiled regexp to match Javadoc tags that take an argument **/
    private static final RE MATCH_JAVADOC_ARG = createRE(MATCH_JAVADOC_ARG_PAT);

    /** the pattern to match Javadoc tags with no argument **/
    private static final String MATCH_JAVADOC_NOARG_PAT
        = "@(return|see|author)\\s+\\S";
    /** compiled regexp to match Javadoc tags with no argument **/
    private static final RE MATCH_JAVADOC_NOARG
        = createRE(MATCH_JAVADOC_NOARG_PAT);

    /** the pattern to match author tag **/
    private static final String MATCH_JAVADOC_AUTHOR_PAT = "@author\\s+\\S";
    /** compiled regexp to match author tag **/
    private static final RE MATCH_JAVADOC_AUTHOR
        = createRE(MATCH_JAVADOC_AUTHOR_PAT);


    ////////////////////////////////////////////////////////////////////////////
    // Member variables
    ////////////////////////////////////////////////////////////////////////////

    /** stack tracking the type of block currently in **/
    private final Stack mInInterface = new Stack();

    /** stack tracking the visibility scope currently in **/
    private final Stack mInScope = new Stack();

    /** tracks the level of block definitions for methods **/
    private int mMethodBlockLevel = 0;

    /** the messages being logged **/
    private final List mMessages = new ArrayList();

    /** the lines of the file being checked **/
    private String[] mLines;

    /** name of the package the file is in **/
    private String mPkgName;

    /** map of the Javadoc comments indexed on the last line of the comment.
     * The hack is it assumes that there is only one Javadoc comment per line.
     **/
    private final Map mComments = new HashMap();

    /** the set of imports (no line number) **/
    private final Set mImports = new HashSet();

    /** the identifiers used **/
    private final Set mReferenced = new HashSet();

    /** configuration for checking **/
    private final Configuration mConfig;

    // }}}

    // {{{ Constructors
    ////////////////////////////////////////////////////////////////////////////
    // Constructor methods
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs the object.
     * @param aConfig the configuration to use for checking
     **/
    VerifierImpl(Configuration aConfig)
    {
        mConfig = aConfig;
    }

    // }}}

    // {{{ Interface verifier methods
    ////////////////////////////////////////////////////////////////////////////
    // Interface Verifier methods
    ////////////////////////////////////////////////////////////////////////////

    /** @see Verifier **/
    public LineText[] getMessages()
    {
        checkImports();
        Collections.sort(mMessages);
        return (LineText[]) mMessages.toArray(new LineText[0]);
    }

    /** @see Verifier **/
    public void clearMessages()
    {
        mLines = null;
        mPkgName = null;
        mInInterface.clear();
        mInScope.clear();
        mMessages.clear();
        mComments.clear();
        mImports.clear();
        mReferenced.clear();
        mMethodBlockLevel = 0;
    }

    /** @see Verifier **/
    public void setLines(String[] aLines)
    {
        mLines = aLines;

        checkHeader();

        // Iterate over the lines looking for long lines and tabs.
        for (int i = 0; i < mLines.length; i++) {
            // check for long line, but possibly allow imports
            if ((mLines[i].length() > mConfig.getMaxLineLength()) &&
                !(mConfig.isIgnoreImportLength() &&
                  mLines[i].trim().startsWith("import")))
            {
                log(i + 1,
                    "line longer than " + mConfig.getMaxLineLength() +
                    " characters");
            }

            if (!mConfig.isAllowTabs() && (mLines[i].indexOf('\t') != -1)) {
                log(i + 1, "line contains a tab character");
            }
        }

        // Check excessive number of lines
        if (mLines.length > mConfig.getMaxFileLength()) {
            log(1,
                "file length is " + mLines.length + " lines (max allowed is " +
                mConfig.getMaxFileLength() + ").");
        }
    }


    /** @see Verifier **/
    public void verifyMethodJavadoc(MyModifierSet aMods,
                                    MyCommonAST aReturnType,
                                    MethodSignature aSig)
    {
        // Always verify the parameters are ok
        for (Iterator it = aSig.getParams().iterator(); it.hasNext(); ) {
            verifyParameter((LineText) it.next());
        }


        // now check the javadoc
        final Scope methodScope =
            inInterfaceBlock() ? Scope.PUBLIC : aMods.getVisibilityScope();

        if (!inCheckScope(methodScope)) {
            return; // no need to really check anything
        }

        // Calculate line number. Unfortunately aReturnType does not contain a
        // valid line number
        final int lineNo = (aMods.size() > 0)
            ? aMods.getFirstLineNo()
            : aSig.getLineNo();

        final boolean isFunction = (aReturnType == null)
            ? false
            : !"void".equals(aReturnType.getText().trim());

        final String[] jd = getJavadocBefore(lineNo - 1);
        if (jd == null) {
            log(lineNo, "method is missing a Javadoc comment.");
        }
        else {
            final List tags = getMethodTags(jd, lineNo - 1);
            // Check for only one @see tag
            if ((tags.size() != 1) ||
                !((JavadocTag) tags.get(0)).isSeeTag())
            {
                checkParamTags(tags, aSig.getParams());
                checkThrowsTags(tags, aSig.getThrows());
                if (isFunction) {
                    checkReturnTag(tags, lineNo);
                }

                // Dump out all unused tags
                final Iterator it = tags.iterator();
                while (it.hasNext()) {
                    final JavadocTag jt = (JavadocTag) it.next();
                    if (!jt.isSeeTag()) {
                        log(jt.getLineNo(), "Unused Javadoc tag.");
                    }
                }
            }
        }
    }


    /** @see Verifier **/
    public void verifyType(MyModifierSet aMods, MyCommonAST aType)
    {
        if (!mConfig.getTypeRegexp().match(aType.getText())) {
            log(aType.getLineNo(),
                "type name '" + aType.getText() +
                "' must match pattern '" + mConfig.getTypePat() + "'.");
        }

        //
        // Only Javadoc testing below
        //
        final Scope typeScope =
            inInterfaceBlock() ? Scope.PUBLIC : aMods.getVisibilityScope();

        if (!inCheckScope(typeScope)) {
            return; // no need to really check anything
        }

        final int lineNo = (aMods.size() > 0)
            ? aMods.getFirstLineNo()
            : aType.getLineNo();

        final String[] jd = getJavadocBefore(lineNo - 1);
        if (jd == null) {
            log(lineNo, "type is missing a Javadoc comment.");
        }
        else if (!mConfig.isAllowNoAuthor() &&
                 mInScope.size() == 0 && // don't check author for inner classes
                 (MATCH_JAVADOC_AUTHOR.grep(jd).length == 0))
        {
            log(lineNo, "type Javadoc comment is missing an @author tag.");
        }
    }


    /** @see Verifier **/
    public void verifyVariable(MyVariable aVar)
    {
        if (inMethodBlock()) {
            return;
        }

        final Scope declaredScope =
            aVar.getModifierSet().getVisibilityScope();
        final Scope variableScope =
            inInterfaceBlock() ? Scope.PUBLIC : declaredScope;

        if (inCheckScope(variableScope) &&
            getJavadocBefore(aVar.getLineNo() - 1) == null)
        {
            log(aVar.getLineNo(),
                "variable '" + aVar.getText() + "' missing Javadoc.");
        }

        // Check correct format
        if (inInterfaceBlock()) {
            // The only declarations allowed in interfaces are all static final,
            // even if not declared that way.
            checkVariable(aVar,
                          mConfig.getStaticFinalRegexp(),
                          mConfig.getStaticFinalPat());
        }
        else {
            final MyModifierSet mods = aVar.getModifierSet();

            if (mods.containsStatic()) {
                if (mods.containsFinal()) {
                    // Handle the serialVersionUID constant which is used for
                    // Serialization. Cannot enforce rules on it. :-)
                    if (!"serialVersionUID".equals(aVar.getText())) {
                        checkVariable(aVar,
                                      mConfig.getStaticFinalRegexp(),
                                      mConfig.getStaticFinalPat());
                    }
                }
                else {
                    if (mods.containsPrivate()) {
                        checkVariable(aVar,
                                      mConfig.getStaticRegexp(),
                                      mConfig.getStaticPat());
                }
                    else {
                        log(aVar.getLineNo(),
                            "variable '" + aVar.getText() +
                            "' must be private and have accessor methods.");
                    }
                }
            }
            else {
                // These are the non-static variables
                if (mods.containsPrivate() ||
                    (mConfig.isAllowProtected() && mods.containsProtected()))
                {
                    checkVariable(aVar,
                                  mConfig.getMemberRegexp(),
                                  mConfig.getMemberPat());
                }
                else if (mods.containsPublic() &&
                         mConfig.getPublicMemberRegexp().match(aVar.getText()))
                {
                    // silently allow
                }
                else {
                    log(aVar.getLineNo(),
                        "variable '" + aVar.getText() +
                        "' must be private and have accessor methods.");
                }
            }
        }
    }

    /** @see Verifier **/
    public void verifyParameter(LineText aParam)
    {
        if (!mConfig.getParamRegexp().match(aParam.getText())) {
            log(aParam.getLineNo(),
                "parameter '" + aParam.getText() +
                "' must match pattern '" + mConfig.getParamPat() + "'.");
        }
    }

    /** @see Verifier **/
    public void reportNeedBraces(Token aToken)
    {
        if (mConfig.isIgnoreBraces()) {
            return;
        }

        log(aToken.getLine(),
            "'" + aToken.getText() + "' construct must use '{}'s.");
    }

    /** @see Verifier **/
    public void verifySurroundingWS(MyCommonAST aAST)
    {
        // Guard to handle an unusable AST
        if (aAST.getLineNo() == 0) {
            return;
        }

        if (mConfig.isIgnoreWhitespace()) {
            return;
        }

        final String line = mLines[aAST.getLineNo() - 1];
        final int before = aAST.getColumnNo() - 1;
        final int after = aAST.getColumnNo() + aAST.getText().length();
        if ((before >= 0) &&
            !Character.isWhitespace(line.charAt(before)))
        {
            log(aAST.getLineNo(), "'" + aAST.getText() +
                "' is not preceeded with whitespace.");
        }
        else if ((after < line.length()) &&
                 !Character.isWhitespace(line.charAt(after)))
        {
            log(aAST.getLineNo(), "'" + aAST.getText() +
                "' is not proceeded with whitespace.");
        }
    }

    /** @see Verifier **/
    public void verifyWSAroundEnd(int aLineNo, int aColNo, String aText)
    {
        verifyWSAroundBegin(aLineNo, aColNo - aText.length(), aText);
    }

    /** @see Verifier **/
    public void verifyWSAroundBegin(int aLineNo, int aColNo, String aText)
    {
        if (mConfig.isIgnoreWhitespace()) {
            return;
        }

        final String line = mLines[aLineNo - 1];
        final int before = aColNo - 2;
        final int after = aColNo + aText.length() - 1;

        if ((before >= 0) && !Character.isWhitespace(line.charAt(before))) {
            log(aLineNo, "'" + aText + "' is not preceeded with whitespace.");
        }

        if ((after < line.length()) &&
            !Character.isWhitespace(line.charAt(after)))
        {
            log(aLineNo, "'" + aText + "' is not proceeded with whitespace.");
        }
    }

    /** @see Verifier **/
    public void verifyNoWSAfter(MyCommonAST aAST)
    {
        if (mConfig.isIgnoreWhitespace()) {
            return;
        }

        final String line = mLines[aAST.getLineNo() - 1];
        final int after = aAST.getColumnNo() + aAST.getText().length();
        if ((after >= line.length()) ||
            Character.isWhitespace(line.charAt(after)))
        {
            log(aAST.getLineNo(),
                "'" + aAST.getText() + "' is proceeded with whitespace.");
        }
    }

    /** @see Verifier **/
    public void verifyNoWSBefore(MyCommonAST aAST)
    {
        if (mConfig.isIgnoreWhitespace()) {
            return;
        }

        final String line = mLines[aAST.getLineNo() - 1];
        final int before = aAST.getColumnNo() - 1;
        if ((before < 0) || Character.isWhitespace(line.charAt(before))) {
            log(aAST.getLineNo(),
                "'" + aAST.getText() + "' is preceeded with whitespace.");
        }
    }

    /** @see Verifier **/
    public void verifyWSAfter(int aLineNo, int aColNo, MyToken aConstruct)
    {
        if (mConfig.isIgnoreWhitespace() ||
            ((MyToken.CAST == aConstruct) && mConfig.isIgnoreCastWhitespace()))
        {
            return;
        }

        final String line = mLines[aLineNo - 1];
        if ((aColNo < line.length()) &&
            !Character.isWhitespace(line.charAt(aColNo)))
        {
            log(aLineNo,
                aConstruct.getText() + " needs to be followed by whitespace.");
        }
    }

    /** @see Verifier **/
    public void verifyMethodLength(int aLineNo, int aLength)
    {
        if (aLength > mConfig.getMaxMethodLength()) {
            log(aLineNo,
                "method length is " + aLength + " lines (max allowed is " +
                mConfig.getMaxMethodLength() + ").");
        }
    }

    /** @see Verifier **/
    public void verifyConstructorLength(int aLineNo, int aLength)
    {
        if (aLength > mConfig.getMaxConstructorLength()) {
            log(aLineNo,
                "constructor length is " + aLength + " lines (max allowed is " +
                mConfig.getMaxConstructorLength() + ").");
        }
    }

    /** @see Verifier **/
    public void reportCppComment(int aLineNo, int aColNo)
    {
        // nop
    }

    /** @see Verifier **/
    public void reportCComment(int aStartLineNo, int aStartColNo,
                               int aEndLineNo, int aEndColNo)
    {
        if (mLines[aStartLineNo - 1].indexOf("/**", aStartColNo) != -1) {
            final String[] cc =
                extractCComment(aStartLineNo, aStartColNo,
                                aEndLineNo, aEndColNo);
            mComments.put(new Integer(aEndLineNo - 1), cc);
        }
    }

    /** @see Verifier **/
    public void reportReference(String aType)
    {
        mReferenced.add(aType);
    }

    /** @see Verifier **/
    public void reportPackageName(String aName)
    {
        mPkgName = aName;
    }

    /** @see Verifier **/
    public void reportImport(int aLineNo, String aType)
    {
        if (!mConfig.isIgnoreImports()) {
            // Check for a duplicate import
            final Iterator it = mImports.iterator();
            while (it.hasNext()) {
                final LineText lt = (LineText) it.next();
                if (aType.equals(lt.getText())) {
                    log(aLineNo,
                        "Duplicate import to line " + lt.getLineNo() + ".");
                }
            }
            // Add to list to check for duplicates and usage
            mImports.add(new LineText(aLineNo, aType));
        }
    }

    /** @see Verifier **/
    public void reportStarImport(int aLineNo, String aPkg)
    {
        if (!mConfig.isIgnoreImports()) {
            log(aLineNo, "Avoid using the '.*' form of import.");
            mImports.add(new LineText(aLineNo, aPkg));
        }
    }

    /** @see Verifier **/
    public void reportStartTypeBlock(Scope aScope, boolean aIsInterface)
    {
        mInScope.push(aScope);
        mInInterface.push(aIsInterface ? Boolean.TRUE : Boolean.FALSE);
    }

    /** @see Verifier **/
    public void reportEndTypeBlock()
    {
        mInScope.pop();
        mInInterface.pop();
    }

    /** @see Verifier **/
    public void reportStartMethodBlock()
    {
        mMethodBlockLevel++;
    }

    /** @see Verifier **/
    public void reportEndMethodBlock()
    {
        mMethodBlockLevel--;
    }

    // }}}

    // {{{ Private methods
    ////////////////////////////////////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Checks if aScope is a part of the Source code where we have
     * to verify correct javadoc.
     * @param aScope a <code>Scope</code> value
     * @return if a Scope is a part of the Source code where we have
     * to verify correct javadoc.
     */
    private boolean inCheckScope(Scope aScope)
    {
        final Scope configScope = mConfig.getJavadocScope();
        boolean retVal = aScope.isIn(configScope);

        // Need to handle where the scope of the enclosing type is not
        // in the scope to be checked. For example:
        // class Outer {
        //     public class Inner {
        //     }
        // }
        //
        // If the scope we are checking is "protected", then even though
        // Inner is public, we do not require Javadoc because Outer does
        // not require it.

        // to implement this we search up the scope stack
        // that all stack elements are also in configScope

        final Iterator scopeIterator = mInScope.iterator();
        while (retVal && scopeIterator.hasNext()) {
            final Scope stackScope = (Scope) scopeIterator.next();
            retVal = stackScope.isIn(configScope);
        }
        return retVal;
    }



    /**
     * Helper method to create a regular expression. Will exit if unable to
     * create the object.
     * @param aPattern the pattern to match
     * @return a created regexp object
     **/
    private static RE createRE(String aPattern)
    {
        RE retVal = null;
        try {
            retVal = new RE(aPattern);
        }
        catch (RESyntaxException e) {
            System.out.println("Failed to initialise regexp expression " +
                               aPattern);
            e.printStackTrace(System.out);
            System.exit(1);
        }
        return retVal;
    }

    /**
     * Logs a message to be reported.
     * @param aLineNo the line number associated with the message
     * @param aMsg the message to log
     **/
    private void log(int aLineNo, String aMsg)
    {
        mMessages.add(new LineText(aLineNo, aMsg));
    }


    /**
     * Checks that a variable confirms to a specified regular expression. Logs
     * a message if it does not.
     * @param aVar the variable to check
     * @param aRegexp the regexp to match against
     * @param aPattern text representation of regexp
     **/
    private void checkVariable(MyVariable aVar, RE aRegexp, String aPattern)
    {
        if (!aRegexp.match(aVar.getText())) {
            log(aVar.getLineNo(),
                "variable '" + aVar.getText() +
                "' must match pattern '" + aPattern + "'.");
        }
    }

    /**
     * Returns the specified C comment as a String array.
     * @return C comment as a array
     * @param aStartLineNo the starting line number
     * @param aStartColNo the starting column number
     * @param aEndLineNo the ending line number
     * @param aEndColNo the ending column number
     **/
    private String[] extractCComment(int aStartLineNo, int aStartColNo,
                                     int aEndLineNo, int aEndColNo)
    {
        String[] retVal;
        if (aStartLineNo == aEndLineNo) {
            retVal = new String[1];
            retVal[0] = mLines[aStartLineNo - 1].substring(aStartColNo,
                                                           aEndColNo + 1);
        }
        else {
            retVal = new String[aEndLineNo - aStartLineNo + 1];
            retVal[0] = mLines[aStartLineNo - 1].substring(aStartColNo);
            for (int i = aStartLineNo; i < aEndLineNo; i++) {
                retVal[i - aStartLineNo + 1] = mLines[i];
            }
            retVal[retVal.length - 1] =
                mLines[aEndLineNo - 1].substring(0, aEndColNo + 1);
        }
        return retVal;
    }

    /**
     * Returns the Javadoc comment before the specified line. null is none.
     * @return the Javadoc comment, or <code>null</code> if none
     * @param aLineNo the line number to check before
     **/
    private String[] getJavadocBefore(int aLineNo)
    {
        int lineNo = aLineNo - 1;

        // skip blank lines
        while ((lineNo > 0) && lineIsBlank(lineNo)) {
            lineNo--;
        }

        return (String[]) mComments.get(new Integer(lineNo));
    }

    /**
     * Checks if the specified line is blank.
     * @param aLineNo the line number to check
     * @return if the specified line consists only of tabs and spaces.
     **/
    private boolean lineIsBlank(int aLineNo)
    {
        // possible improvement: avoid garbage creation in trim()
        return "".equals(mLines[aLineNo].trim());
    }

    /**
     * Returns the tags in a javadoc comment. Only finds throws, exception,
     * param, return and see tags.
     * @return the tags found
     * @param aLines the Javadoc comment
     * @param aLastLineNo the line number of the last line in the Javadoc
     *                    comment
     **/
    private List getMethodTags(String[] aLines, int aLastLineNo)
    {
        final List tags = new ArrayList();
        int currentLine = aLastLineNo - aLines.length;
        for (int i = 0; i < aLines.length; i++) {
            currentLine++;
            if (MATCH_JAVADOC_ARG.match(aLines[i])) {
                tags.add(new JavadocTag(currentLine,
                                        MATCH_JAVADOC_ARG.getParen(1),
                                        MATCH_JAVADOC_ARG.getParen(2)));
            }
            else if (MATCH_JAVADOC_NOARG.match(aLines[i])) {
                tags.add(new JavadocTag(currentLine,
                                        MATCH_JAVADOC_NOARG.getParen(1)));
            }
        }
        return tags;
    }

    /**
     * Checks a set of tags for matching parameters.
     * @param aTags the tags to check
     * @param aParams the parameters to check
     **/
    private void checkParamTags(List aTags, List aParams)
    {
        // Loop over the tags, checking to see they exist in the params.
        final ListIterator tagIt = aTags.listIterator();
        while (tagIt.hasNext()) {
            final JavadocTag tag = (JavadocTag) tagIt.next();

            if (!tag.isParamTag()) {
                continue;
            }

            tagIt.remove();

            // Loop looking for matching param
            boolean found = false;
            final ListIterator paramIt = aParams.listIterator();
            while (paramIt.hasNext()) {
                final LineText param = (LineText) paramIt.next();
                if (param.getText().equals(tag.getArg1())) {
                    found = true;
                    paramIt.remove();
                    break;
                }
            }

            // Handle extra JavadocTag
            if (!found) {
                log(tag.getLineNo(),
                    "Unused @param tag for '" + tag.getArg1() + "'.");
            }
        }

        // Now dump out all parameters without tags
        final ListIterator paramIt = aParams.listIterator();
        while (paramIt.hasNext()) {
            final LineText param = (LineText) paramIt.next();
            log(param.getLineNo(),
                "Expected @param tag for '" + param.getText() + "'.");
        }
    }

    /**
     * Checks for only one return tag. All return tags will be removed from the
     * supplied list.
     * @param aTags the tags to check
     * @param aLineNo the line number of the expected tag
     **/
    private void checkReturnTag(List aTags, int aLineNo)
    {
        // Loop over tags finding return tags. After the first one, report an
        // error.
        boolean found = false;
        final ListIterator it = aTags.listIterator();
        while (it.hasNext()) {
            final JavadocTag jt = (JavadocTag) it.next();
            if (jt.isReturnTag()) {
                if (found) {
                    log(jt.getLineNo(), "Duplicate @return tag.");
                }
                found = true;
                it.remove();
            }
        }

        // Handle there being no @return tags
        if (!found) {
            log(aLineNo, "Expected an @return tag.");
        }
    }


    /**
     * Checks a set of tags for matching throws.
     * @param aTags the tags to check
     * @param aThrows the throws to check
     **/
    private void checkThrowsTags(List aTags, List aThrows)
    {
        // Loop over the tags, checking to see they exist in the throws.
        final ListIterator tagIt = aTags.listIterator();
        while (tagIt.hasNext()) {
            final JavadocTag tag = (JavadocTag) tagIt.next();

            if (!tag.isThrowsTag()) {
                continue;
            }

            tagIt.remove();

            // Loop looking for matching throw
            boolean found = false;
            final ListIterator throwIt = aThrows.listIterator();
            while (throwIt.hasNext()) {
                final LineText t = (LineText) throwIt.next();
                if (t.getText().equals(tag.getArg1())) {
                    found = true;
                    throwIt.remove();
                    break;
                }
            }

            // Handle extra JavadocTag
            if (!found) {
                log(tag.getLineNo(),
                    "Unused @throws tag for '" + tag.getArg1() + "'.");
            }
        }

        // Now dump out all throws without tags
        final ListIterator throwIt = aThrows.listIterator();
        while (throwIt.hasNext()) {
            final LineText t = (LineText) throwIt.next();
            log(t.getLineNo(),
                "Expected @throws tag for '" + t.getText() + "'.");
        }
    }


    /** checks that a file contains a valid header **/
    private void checkHeader()
    {
        if (mConfig.getHeaderLines().length > mLines.length) {
            log(1, "Missing a header - not enough lines in file.");
        }
        else {
            for (int i = 0; i < mConfig.getHeaderLines().length; i++) {
                if ((i != (mConfig.getHeaderIgnoreLineNo() - 1)) &&
                    !mConfig.getHeaderLines()[i].equals(mLines[i]))
                {
                    log(i + 1,
                        "Line does not match expected header line of '" +
                        mConfig.getHeaderLines()[i] + "'.");
                    break; // stop checking
                }
            }
        }
    }

    /**
     * @return the class name from a fully qualified name
     * @param aType the fully qualified name
     */
    private String basename(String aType)
    {
        final int i = aType.lastIndexOf(".");
        return (i == -1) ? aType : aType.substring(i + 1);
    }

    /** Check the imports that are unused or unrequired. **/
    private void checkImports()
    {
        if (mConfig.isIgnoreImports()) {
            return;
        }

        // Loop checking imports
        final Iterator it = mImports.iterator();
        while (it.hasNext()) {
            final LineText imp = (LineText) it.next();

            if (fromPackage(imp.getText(), "java.lang")) {
                log(imp.getLineNo(),
                    "Redundant import from the java.lang package.");
            }
            else if (fromPackage(imp.getText(), mPkgName)) {
                log(imp.getLineNo(), "Redundant import from the same package.");
            }
            else if (!imp.getText().endsWith(".*") &&
                     !mReferenced.contains(basename(imp.getText())))
            {
                log(imp.getLineNo(), "Unused import - " + imp.getText());
            }
        }
    }

    /** @return whether currently in an interface block **/
    private boolean inInterfaceBlock()
    {
        return (!mInInterface.empty() &&
                Boolean.TRUE.equals(mInInterface.peek()));
    }

    /** @return whether currently in a method block **/
    private boolean inMethodBlock()
    {
        return (mMethodBlockLevel > 0);
    }

    /**
     * Determines in an import statement is for types from a specified package.
     * @param aImport the import name
     * @param aPkg the package name
     * @return whether from the package
     */
    private static boolean fromPackage(String aImport, String aPkg)
    {
        boolean retVal = false;
        if (aPkg == null) {
            // If not package, then check for no package in the import.
            retVal = (aImport.indexOf('.') == -1);
        }
        else {
            final int index = aImport.lastIndexOf('.');
            if (index != -1) {
                final String front = aImport.substring(0, index);
                retVal = front.equals(aPkg);
            }
        }
        return retVal;
    }

    // }}}
}
