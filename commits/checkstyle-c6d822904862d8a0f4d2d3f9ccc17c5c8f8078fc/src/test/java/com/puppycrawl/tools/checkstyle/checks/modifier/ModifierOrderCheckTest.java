////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2018 the original author or authors.
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

package com.puppycrawl.tools.checkstyle.checks.modifier;

import static com.puppycrawl.tools.checkstyle.checks.modifier.ModifierOrderCheck.MSG_ANNOTATION_ORDER;
import static com.puppycrawl.tools.checkstyle.checks.modifier.ModifierOrderCheck.MSG_MODIFIER_ORDER;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Assert;
import org.junit.Test;

import com.puppycrawl.tools.checkstyle.AbstractModuleTestSupport;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.CommonUtils;

public class ModifierOrderCheckTest
    extends AbstractModuleTestSupport {

    @Override
    protected String getPackageLocation() {
        return "com/puppycrawl/tools/checkstyle/checks/modifier/modifierorder";
    }

    @Test
    public void testGetRequiredTokens() {
        final ModifierOrderCheck checkObj = new ModifierOrderCheck();
        final int[] expected = {TokenTypes.MODIFIERS};
        assertArrayEquals("Default required tokens are invalid",
            expected, checkObj.getRequiredTokens());
    }

    @Test
    public void testIt() throws Exception {
        final DefaultConfiguration checkConfig =
            createModuleConfig(ModifierOrderCheck.class);
        final String[] expected = {
            "14:10: " + getCheckMessage(MSG_MODIFIER_ORDER, "final"),
            "18:12: " + getCheckMessage(MSG_MODIFIER_ORDER, "private"),
            "24:14: " + getCheckMessage(MSG_MODIFIER_ORDER, "private"),
            "34:13: " + getCheckMessage(MSG_ANNOTATION_ORDER, "@MyAnnotation2"),
            "39:13: " + getCheckMessage(MSG_ANNOTATION_ORDER, "@MyAnnotation2"),
            "49:35: " + getCheckMessage(MSG_ANNOTATION_ORDER, "@MyAnnotation4"),
            "157:14: " + getCheckMessage(MSG_MODIFIER_ORDER, "default"),
        };
        verify(checkConfig, getPath("InputModifierOrderIt.java"), expected);
    }

    @Test
    public void testDefaultMethods()
            throws Exception {
        final DefaultConfiguration checkConfig =
                createModuleConfig(ModifierOrderCheck.class);
        final String[] expected = CommonUtils.EMPTY_STRING_ARRAY;
        verify(checkConfig, getPath("InputModifierOrderDefaultMethods.java"), expected);
    }

    @Test
    public void testGetDefaultTokens() {
        final ModifierOrderCheck modifierOrderCheckObj = new ModifierOrderCheck();
        final int[] actual = modifierOrderCheckObj.getDefaultTokens();
        final int[] expected = {TokenTypes.MODIFIERS};
        final int[] unexpectedArray = {
            TokenTypes.MODIFIERS,
            TokenTypes.OBJBLOCK,
        };
        assertArrayEquals("Default tokens are invalid", expected, actual);
        final int[] unexpectedEmptyArray = CommonUtils.EMPTY_INT_ARRAY;
        Assert.assertNotSame("Default tokens should not be empty array",
                unexpectedEmptyArray, actual);
        Assert.assertNotSame("Invalid default tokens", unexpectedArray, actual);
        Assert.assertNotNull("Default tokens should not be null", actual);
    }

    @Test
    public void testGetAcceptableTokens() {
        final ModifierOrderCheck modifierOrderCheckObj = new ModifierOrderCheck();
        final int[] actual = modifierOrderCheckObj.getAcceptableTokens();
        final int[] expected = {TokenTypes.MODIFIERS};
        final int[] unexpectedArray = {
            TokenTypes.MODIFIERS,
            TokenTypes.OBJBLOCK,
        };
        assertArrayEquals("Default acceptable tokens are invalid", expected, actual);
        final int[] unexpectedEmptyArray = CommonUtils.EMPTY_INT_ARRAY;
        Assert.assertNotSame("Default tokens should not be empty array",
                unexpectedEmptyArray, actual);
        Assert.assertNotSame("Invalid acceptable tokens", unexpectedArray, actual);
        Assert.assertNotNull("Acceptable tokens should not be null", actual);
    }

    @Test
    public void testSkipTypeAnnotations() throws Exception {
        // Type Annotations are available only in Java 8
        // We skip type annotations from validation
        // See https://github.com/checkstyle/checkstyle/issues/903#issuecomment-172228013
        final DefaultConfiguration checkConfig = createModuleConfig(ModifierOrderCheck.class);
        final String[] expected = {
            "103:13: " + getCheckMessage(MSG_ANNOTATION_ORDER, "@MethodAnnotation"),
        };
        verify(checkConfig, getNonCompilablePath("InputModifierOrderTypeAnnotations.java"),
            expected);
    }

    @Test
    public void testAnnotationOnAnnotationDeclaration() throws Exception {
        final DefaultConfiguration checkConfig = createModuleConfig(ModifierOrderCheck.class);
        final String[] expected = {
            "3:8: " + getCheckMessage(MSG_ANNOTATION_ORDER, "@InterfaceAnnotation"),
        };
        verify(checkConfig, getPath("InputModifierOrderAnnotationDeclaration.java"), expected);
    }
}
