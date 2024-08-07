////////////////////////////////////////////////////////////////////////////////
// Test case file for checkstyle.
// Created: 2001
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle;

/**
 * Tests having inner types
 * @author Oliver Burn
 **/
class InputInner
{
    // Ignore - two errors
    class InnerInner2
    {
        // Ignore
        public int fData;
    }

    // Ignore - 2 errors
    interface InnerInterface2
    {
        // Ignore - should be all upper case
        String data = "zxzc";

        // Ignore
        class InnerInterfaceInnerClass
        {
            // Ignore - need Javadoc and made private
            public int rData;

            /** needs to be made private unless allowProtected. */
            protected int protectedVariable;

            /** needs to be made private unless allowPackage. */
            int packageVariable;
        }
    }
}
