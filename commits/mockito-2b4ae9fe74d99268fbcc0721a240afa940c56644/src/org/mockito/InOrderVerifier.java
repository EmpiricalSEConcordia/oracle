/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito;

import java.util.LinkedList;
import java.util.List;

import org.mockito.exceptions.Reporter;
import org.mockito.internal.verification.VerificationMode;
import org.mockito.internal.verification.VerificationModeDecoder;
import org.mockito.internal.verification.VerificationModeImpl;

/**
 * Allows verifying in order. This class should not be exposed, hence default access.
 */
class InOrderVerifier implements InOrder {
    
    private final Reporter reporter = new Reporter();
    private final List<Object> mocksToBeVerifiedInOrder = new LinkedList<Object>();
    
    public InOrderVerifier(List<Object> mocksToBeVerifiedInOrder) {
        this.mocksToBeVerifiedInOrder.addAll(mocksToBeVerifiedInOrder);
    }

    public <T> T verify(T mock) {
        return this.verify(mock, VerificationModeImpl.times(1));
    }
    
    public <T> T verify(T mock, VerificationMode mode) {
        if (!mocksToBeVerifiedInOrder.contains(mock)) {
            reporter.inOrderRequiresFamiliarMock();
        }
        Integer wantedCount = mode.wantedCount();
        if (new VerificationModeDecoder(mode).atLeastMode()) {
            return Mockito.verify(mock, VerificationModeImpl.inOrderAtLeast(wantedCount, mocksToBeVerifiedInOrder));
        } else {
            return Mockito.verify(mock, VerificationModeImpl.inOrder(wantedCount, mocksToBeVerifiedInOrder));            
        }
    }
}
