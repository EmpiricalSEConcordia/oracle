package com.puppycrawl.tools.checkstyle;

import com.puppycrawl.tools.checkstyle.checks.IllegalImportCheck;

public class IllegalImportCheckTest
extends BaseCheckTestCase
{
    public IllegalImportCheckTest(String aName)
    {
        super(aName);
    }

    public void testWithSupplied()
        throws Exception
    {
        final CheckConfiguration checkConfig = new CheckConfiguration();
        checkConfig.setClassname(IllegalImportCheck.class.getName());
        checkConfig.addProperty("illegalPkgs", "java.io");
        final Checker c = createChecker(checkConfig);
        final String fname = getPath("InputImport.java");
        final String[] expected = {
            "9:1: Import from illegal package - java.io.*.",
        };
        verify(c, fname, expected);
    }

    public void testWithDefault()
        throws Exception
    {
        final CheckConfiguration checkConfig = new CheckConfiguration();
        checkConfig.setClassname(IllegalImportCheck.class.getName());
        final Checker c = createChecker(checkConfig);
        final String fname = getPath("InputImport.java");
        final String[] expected = {
            "15:1: Import from illegal package - sun.net.ftpclient.FtpClient.",
        };
        verify(c, fname, expected);
    }
}
