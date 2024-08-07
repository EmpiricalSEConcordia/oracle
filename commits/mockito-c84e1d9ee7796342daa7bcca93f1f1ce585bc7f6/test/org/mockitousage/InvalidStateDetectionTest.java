/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.stubVoid;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.TestBase;
import org.mockito.StateResetter;
import org.mockito.exceptions.misusing.UnfinishedStubbingException;
import org.mockito.exceptions.misusing.UnfinishedVerificationException;

/**
 * invalid state happens if:
 * 
 *    -unfinished stubbing
 *    -unfinished stubVoid
 *    -stubbing without actual method call
 *    -verify without actual method call
 *    
 * we should aim to detect invalid state in following scenarios:
 * 
 *    -on method call on mock
 *    -on verify
 *    -on verifyZeroInteractions
 *    -on verifyNoMoreInteractions
 *    -on verify in order
 *    -on stub
 *    -on stubVoid
 */
@SuppressWarnings("unchecked")
public class InvalidStateDetectionTest extends TestBase {

    private IMethods mock;

    @Before
    @After
    public void resetState() {
        StateResetter.reset();
        mock = mock(IMethods.class);
    }
    
    @Test
    public void shouldDetectUnfinishedStubbing() {
        stub(mock.simpleMethod());
        detects(new OnMethodCallOnMock(), UnfinishedStubbingException.class);

        stub(mock.simpleMethod());
        detects(new OnStub(), UnfinishedStubbingException.class);
        
        stub(mock.simpleMethod());
        detects(new OnStubVoid(), UnfinishedStubbingException.class);
        
        stub(mock.simpleMethod());
        detects(new OnVerify(), UnfinishedStubbingException.class);
        
        stub(mock.simpleMethod());
        detects(new OnVerifyInOrder(), UnfinishedStubbingException.class);
        
        stub(mock.simpleMethod());
        detects(new OnVerifyZeroInteractions(), UnfinishedStubbingException.class);
        
        stub(mock.simpleMethod());
        detects(new OnVerifyNoMoreInteractions(), UnfinishedStubbingException.class);
    }
    
    @Test
    public void shouldDetectUnfinishedStubbingVoid() {
        stubVoid(mock);
        detects(new OnMethodCallOnMock(), UnfinishedStubbingException.class);
        
        stubVoid(mock);
        detects(new OnStub(), UnfinishedStubbingException.class);
        
        stubVoid(mock);
        detects(new OnStubVoid(), UnfinishedStubbingException.class);
        
        stubVoid(mock);
        detects(new OnVerify(), UnfinishedStubbingException.class);
        
        stubVoid(mock);
        detects(new OnVerifyInOrder(), UnfinishedStubbingException.class);
        
        stubVoid(mock);
        detects(new OnVerifyZeroInteractions(), UnfinishedStubbingException.class);
        
        stubVoid(mock);
        detects(new OnVerifyNoMoreInteractions(), UnfinishedStubbingException.class);
    }
    
    @Test
    public void shouldDetectUnfinishedVerification() {
        verify(mock);
        detects(new OnStub(), UnfinishedVerificationException.class);
        
        verify(mock);
        detects(new OnStubVoid(), UnfinishedVerificationException.class);
        
        verify(mock);
        detects(new OnVerify(), UnfinishedVerificationException.class);
        
        verify(mock);
        detects(new OnVerifyInOrder(), UnfinishedVerificationException.class);
        
        verify(mock);
        detects(new OnVerifyZeroInteractions(), UnfinishedVerificationException.class);
        
        verify(mock);
        detects(new OnVerifyNoMoreInteractions(), UnfinishedVerificationException.class);
    }
    
    @Test
    public void shouldCorrectStateAfterDetectingUnfinishedStubbing() {
        stubVoid(mock).toThrow(new RuntimeException());
        
        try {
            stubVoid(mock).toThrow(new RuntimeException()).on().oneArg(true);
            fail();
        } catch (UnfinishedStubbingException e) {}
        
        stubVoid(mock).toThrow(new RuntimeException()).on().oneArg(true);
        try {
            mock.oneArg(true);
            fail();
        } catch (RuntimeException e) {}
    }
    
    @Test
    public void shouldCorrectStateAfterDetectingUnfinishedVerification() {
        mock.simpleMethod();
        verify(mock);
        
        try {
            verify(mock).simpleMethod();
            fail();
        } catch (UnfinishedVerificationException e) {}
        
        verify(mock).simpleMethod();
    }
    
    private static interface DetectsInvalidState {
        void detect(IMethods mock);
    }
    
    private static class OnVerify implements DetectsInvalidState {
        public void detect(IMethods mock) {
            verify(mock);
        }
    }
    
    private static class OnVerifyInOrder implements DetectsInvalidState {
        public void detect(IMethods mock) {
            inOrder(mock).verify(mock);
        }
    }
    
    private static class OnVerifyZeroInteractions implements DetectsInvalidState {
        public void detect(IMethods mock) {
            verifyZeroInteractions(mock);
        }
    }
    
    private static class OnVerifyNoMoreInteractions implements DetectsInvalidState {
        public void detect(IMethods mock) {
            verifyNoMoreInteractions(mock);
        }
    }    
    
    private static class OnStub implements DetectsInvalidState {
        public void detect(IMethods mock) {
            stub(mock);
        }
    }
    
    private static class OnStubVoid implements DetectsInvalidState {
        public void detect(IMethods mock) {
            stubVoid(mock);
        }
    }
    
    private static class OnMethodCallOnMock implements DetectsInvalidState {
        public void detect(IMethods mock) {
            mock.simpleMethod();
        }
    }
    
    private void detects(DetectsInvalidState detector, Class expected) {
        try {
            detector.detect(mock);
            fail("Should throw an exception");
        } catch (Exception e) {
            assertEquals(expected, e.getClass());
        }
    }
}
