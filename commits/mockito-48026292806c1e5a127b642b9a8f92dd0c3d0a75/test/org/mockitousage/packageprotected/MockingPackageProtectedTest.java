/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.packageprotected;

import org.junit.Test;
import org.mockito.TestBase;
import static org.mockito.Mockito.*;

public class MockingPackageProtectedTest extends TestBase {

    static class Foo {};
    
    class Bar {};
    
    @Test
    public void shouldMockPackageProtectedClasses() {
        mock(PackageProtected.class);
        mock(Foo.class);
        mock(Bar.class);
    }
}