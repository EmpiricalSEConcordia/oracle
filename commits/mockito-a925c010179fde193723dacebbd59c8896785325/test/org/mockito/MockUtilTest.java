/*
 * Copyright (c) 2007 Mockito contributors 
 * This program is made available under the terms of the MIT License.
 */
package org.mockito;

import static org.junit.Assert.*;
import static org.mockito.internal.MockUtil.*;

import java.util.*;

import net.sf.cglib.proxy.*;

import org.junit.Test;
import org.mockito.exceptions.misusing.NotAMockException;
import org.mockito.exceptions.parents.MockitoException;

public class MockUtilTest extends RequiresValidState {

    @SuppressWarnings("unchecked")
    @Test 
    public void shouldGetControl() {
        List mock = Mockito.mock(List.class);
        assertNotNull(getControl(mock));
    }

    @Test 
    public void shouldScreamWhenEnhancedButNotAMockPassed() {
        Object o = Enhancer.create(ArrayList.class, NoOp.INSTANCE);
        try {
            getControl(o);
            fail();
        } catch (NotAMockException e) {}
    }

    @Test (expected=NotAMockException.class)
    public void shouldScreamWhenNotAMockPassed() {
        getControl("");
    }
    
    @Test (expected=MockitoException.class)
    public void shouldScreamWhenNullPassed() {
        getControl(null);
    }
    
    @Test (expected=NotAMockException.class)
    public void shouldValidateMock() {
        validateMock("");
    }
    
    @Test (expected=MockitoException.class)
    public void shouldScreamWhenNullPassedToValidation() {
        validateMock(null);
    }
}
