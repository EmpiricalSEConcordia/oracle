/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.exceptions.verification;

import org.mockito.exceptions.base.MockitoAssertionError;

public class InvocationDiffersFromActual extends MockitoAssertionError {

    private static final long serialVersionUID = 1L;

    public InvocationDiffersFromActual(String message, Throwable cause) {
        super(message, cause);
    }
}
