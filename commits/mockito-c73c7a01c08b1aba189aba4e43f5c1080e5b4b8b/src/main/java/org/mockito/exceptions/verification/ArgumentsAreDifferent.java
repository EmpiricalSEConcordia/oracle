/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */

package org.mockito.exceptions.verification;

import static org.mockito.internal.util.StringUtil.removeFirstLine;

import org.mockito.exceptions.base.MockitoAssertionError;

public class ArgumentsAreDifferent extends MockitoAssertionError {

    private static final long serialVersionUID = 1L;

    public ArgumentsAreDifferent(String message) {
        super(message);
    }
    
    @Override
    public String toString() {
        return removeFirstLine(super.toString());
    }
}