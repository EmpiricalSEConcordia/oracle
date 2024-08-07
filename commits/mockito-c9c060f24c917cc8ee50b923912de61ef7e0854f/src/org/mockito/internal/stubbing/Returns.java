/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.stubbing;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class Returns implements Answer<Object> {

    private final Object value;

    public Returns(Object value) {
        this.value = value;
    }

    public Object answer(InvocationOnMock invocation) throws Throwable {
        return value;
    }

    public String printReturnType() {
        return value.getClass().getSimpleName();
    }

    public Class<?> getReturnType() {
        return value.getClass();
    }

    public boolean returnsNull() {
        return value == null;
    }
    
    @Override
    public String toString() {
        return "Returns: " + value;
    }
}