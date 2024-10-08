/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.binding;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.util.ExtraMatchers.*;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.TestBase;
import org.mockito.exceptions.verification.ArgumentsAreDifferentException;
import org.mockito.exceptions.verification.VerifcationInOrderFailure;
import org.mockitousage.IMethods;

public class IncorectBindingPuzzleFixedTest extends TestBase {

    private Super mock;

    private void setMockWithDowncast(Super mock) {
        this.mock = mock;
    }

    private interface Super {
        void say(Object message);
    }

    private interface Sub extends Super {
        void say(String message);
    }

    private void say(Object message) {
        mock.say(message);
    }

    @Test
    public void shouldUseArgumentTypeWhenOverloadingPuzzleDetected() throws Exception {
        Sub sub = mock(Sub.class);
        setMockWithDowncast(sub);
        say("Hello");
        try {
            verify(sub).say("Hello");
            fail();
        } catch (ArgumentsAreDifferentException error) {
            //TODO this is no longer valid, because method is different not the args 
            String expected =
                "\n" +
                "Argument(s) are different!" +
                "\n" +
                "Method: Sub.say(...)" +
                "\n" +
                "All wanted arguments:" +
                "\n" +
                "    1: class java.lang.String";

            assertEquals(expected, error.getMessage());

            String expectedCause =
                "\n" +
                "All actual arguments:" +
                "\n" +
                "    1: class java.lang.Object";
            assertEquals(expectedCause, error.getCause().getMessage());
        }
    }

    @Ignore("that's a very edge case, don't care for it now")
    @Test
    public void shouldPrintArgumentTypeWhenOverloadingPuzzleDetectedByVerificationInOrder() throws Exception {
        IMethods mockTwo = mock(IMethods.class);
        mockTwo.simpleMethod();
        
        Sub sub = mock(Sub.class);
        setMockWithDowncast(sub);
        say("Hello");
        
        InOrder inOrder = inOrder(mock, mockTwo);
        inOrder.verify(mockTwo).simpleMethod();
        
        try {
            inOrder.verify(sub).say("Hello");
            fail();
        } catch (VerifcationInOrderFailure e) {
            assertThat(e, messageContains("Sub.say(class java.lang.String)"));
        }
    }

    @Test
    public void shouldUseArgumentTypeWhenMatcherUsed() throws Exception {
        Sub sub = mock(Sub.class);
        setMockWithDowncast(sub);
        say("Hello world");
        try {
            verify(sub).say(contains("world"));
            fail();
        } catch (ArgumentsAreDifferentException e) {
            assertThat(e, messageContains("1: class java.lang.String"));
            assertThat(e, causeMessageContains("1: class java.lang.Object"));
        }
    }
}