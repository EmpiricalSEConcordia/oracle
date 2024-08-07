/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.verification;

import java.util.LinkedList;
import java.util.List;

import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.InvocationsFinder;
import org.mockito.internal.progress.VerificationModeImpl;

class InvocationsFinderStub extends InvocationsFinder {
    
    Invocation similarToReturn;
    final List<Invocation> actualToReturn = new LinkedList<Invocation>();
    List<Invocation> invocations;
    Invocation firstUnverifiedToReturn;
    final List<Invocation> firstUnverifiedChunkToReturn = new LinkedList<Invocation>();

    @Override public List<Invocation> findInvocations(List<Invocation> invocations, InvocationMatcher wanted,
            VerificationModeImpl mode) {
        this.invocations = invocations;
        return actualToReturn;
    }
    
    @Override public Invocation findSimilarInvocation(List<Invocation> invocations, InvocationMatcher wanted, VerificationModeImpl mode) {
        this.invocations = invocations;
        return similarToReturn;
    }
    
    @Override public Invocation findFirstUnverified(List<Invocation> invocations) {
        this.invocations = invocations;
        return firstUnverifiedToReturn;
    }
    
    @Override public List<Invocation> findFirstUnverifiedChunk(List<Invocation> invocations, InvocationMatcher wanted) {
        return firstUnverifiedChunkToReturn;
    }
}