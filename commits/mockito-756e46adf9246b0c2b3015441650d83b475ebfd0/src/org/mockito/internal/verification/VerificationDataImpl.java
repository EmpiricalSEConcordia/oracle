/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.verification;

import java.util.List;

import org.mockito.exceptions.Reporter;
import org.mockito.internal.invocation.InvocationImpl;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.stubbing.InvocationContainer;
import org.mockito.internal.util.ObjectMethodsGuru;
import org.mockito.internal.verification.api.VerificationData;

public class VerificationDataImpl implements VerificationData {

    private final InvocationMatcher wanted;
    private final InvocationContainer invocations;

    public VerificationDataImpl(InvocationContainer invocations, InvocationMatcher wanted) {
        this.invocations = invocations;
        this.wanted = wanted;
        this.assertWantedIsVerifiable();
    }

    public List<InvocationImpl> getAllInvocations() {
        return invocations.getInvocations();
    }

    public InvocationMatcher getWanted() {
        return wanted;
    }

    void assertWantedIsVerifiable() {
        if (wanted == null) {
            return;
        }
        ObjectMethodsGuru o = new ObjectMethodsGuru();
        if (o.isToString(wanted.getMethod())) {
            new Reporter().cannotVerifyToString();
        }
    }
}