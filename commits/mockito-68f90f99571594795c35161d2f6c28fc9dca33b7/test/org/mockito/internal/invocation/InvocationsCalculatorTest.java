package org.mockito.internal.invocation;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.mockito.RequiresValidState;
import org.mockito.exceptions.parents.HasStackTrace;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.InvocationsCalculator;
import org.mockito.internal.progress.OngoingVerifyingMode;


public class InvocationsCalculatorTest extends RequiresValidState {
    
    private InvocationsCalculator calculator;
    private Invocation simpleMethodInvocation;
    private Invocation simpleMethodInvocationTwo;
    private Invocation differentMethodInvocation;

    @Before
    public void setup() throws Exception {
        simpleMethodInvocation = new InvocationBuilder().simpleMethod().seq(1).toInvocation();
        simpleMethodInvocationTwo = new InvocationBuilder().simpleMethod().seq(2).toInvocation();
        differentMethodInvocation = new InvocationBuilder().differentMethod().seq(3).toInvocation();
        calculator = new InvocationsCalculator(Arrays.asList(simpleMethodInvocation, simpleMethodInvocationTwo, differentMethodInvocation));
    }
    
    @Test
    public void shouldGetFirstUnverifiedInvocation() throws Exception {
        assertSame(simpleMethodInvocation, calculator.getFirstUnverified());
        
        simpleMethodInvocationTwo.markVerified();
        simpleMethodInvocation.markVerified();
        
        assertSame(differentMethodInvocation, calculator.getFirstUnverified());
        
        differentMethodInvocation.markVerified();
        assertNull(calculator.getFirstUnverified());
    }
    
    @Test
    public void shouldGetFirstUndesiredWhenWantedNumberOfTimesIsZero() throws Exception {
        HasStackTrace firstUndesired = calculator.getFirstUndesiredInvocationStackTrace(new InvocationMatcher(simpleMethodInvocation), OngoingVerifyingMode.times(0));
        HasStackTrace expected = simpleMethodInvocation.getStackTrace();
        assertSame(firstUndesired, expected);
    }
    
    @Test
    public void shouldGetFirstUndesiredWhenWantedNumberOfTimesIsOne() throws Exception {
        HasStackTrace firstUndesired = calculator.getFirstUndesiredInvocationStackTrace(new InvocationMatcher(simpleMethodInvocation), OngoingVerifyingMode.times(1));
        HasStackTrace expected = simpleMethodInvocationTwo.getStackTrace();
        assertSame(firstUndesired, expected);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void shouldBreakWhenThereAreNoUndesiredInvocations() throws Exception {
        calculator.getFirstUndesiredInvocationStackTrace(new InvocationMatcher(simpleMethodInvocation), OngoingVerifyingMode.times(2));
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void shouldBreakWhenWantedInvocationsFigureIsBigger() throws Exception {
        calculator.getFirstUndesiredInvocationStackTrace(new InvocationMatcher(simpleMethodInvocation), OngoingVerifyingMode.times(100));
    }
}
