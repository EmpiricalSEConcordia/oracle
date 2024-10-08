/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */

package org.mockito.internal.invocation;

import java.lang.reflect.Method;
import java.nio.charset.CharacterCodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.invocation.realmethod.RealMethod;
import org.mockito.internal.matchers.ArrayEquals;
import org.mockito.internal.matchers.Equals;
import org.mockitousage.IMethods;
import org.mockitoutil.TestBase;

@SuppressWarnings({"unchecked"})
public class InvocationTest extends TestBase {

    private Invocation invocation;

    @Before
    public void setup() throws Exception {
        invocation = new InvocationBuilder().args(" ").mock("mock").toInvocation();
    }

    @Test
    public void shouldKnowIfIsEqualTo() {
        Invocation equal =                  new InvocationBuilder().args(" ").mock("mock").toInvocation();
        Invocation nonEqual =               new InvocationBuilder().args("X").mock("mock").toInvocation();
        Invocation withNewStringInstance =  new InvocationBuilder().args(new String(" ")).mock("mock").toInvocation();

        assertFalse(invocation.equals(null));
        assertFalse(invocation.equals(""));
        assertTrue(invocation.equals(equal));
        assertFalse(invocation.equals(nonEqual));
        assertTrue(invocation.equals(withNewStringInstance));
    }
    
    @Test
    public void shouldEqualToNotConsiderSequenceNumber() {
        Invocation equal = new InvocationBuilder().args(" ").mock("mock").seq(2).toInvocation();
        
        assertTrue(invocation.equals(equal));
        assertTrue(invocation.getSequenceNumber() != equal.getSequenceNumber());
    }
    
    @Test
    public void shouldBeACitizenOfHashes() {
        Map map = new HashMap();
        map.put(invocation, "one");
        assertEquals("one", map.get(invocation));
    }
    
    @Test
    public void shouldPrintMethodName() {
        invocation = new InvocationBuilder().toInvocation();
        assertEquals("iMethods.simpleMethod();", invocation.toString());
    }
    
    @Test
    public void shouldPrintMethodArgs() {
        invocation = new InvocationBuilder().args("foo").toInvocation();
        assertThat(invocation.toString(), endsWith("simpleMethod(\"foo\");"));
    }
    
    @Test
    public void shouldPrintMethodIntegerArgAndString() {
        invocation = new InvocationBuilder().args("foo", 1).toInvocation();
        assertThat(invocation.toString(), endsWith("simpleMethod(\"foo\", 1);"));
    }
    
    @Test
    public void shouldPrintNull() {
        invocation = new InvocationBuilder().args((String) null).toInvocation();
        assertThat(invocation.toString(), endsWith("simpleMethod(null);"));
    }
    
    @Test
    public void shouldPrintArray() {
        invocation = new InvocationBuilder().method("oneArray").args(new int[] { 1, 2, 3 }).toInvocation();
        assertThat(invocation.toString(), endsWith("oneArray([1, 2, 3]);"));
    }
    
    @Test
    public void shouldPrintNullIfArrayIsNull() throws Exception {
        Method m = IMethods.class.getMethod("oneArray", Object[].class);
        invocation = new InvocationBuilder().method(m).args((Object) null).toInvocation();
        assertThat(invocation.toString(), endsWith("oneArray(null);"));
    }
    
    @Test
    public void shouldPrintArgumentsInMultilinesWhenGetsTooBig() {
        invocation = new InvocationBuilder().args("veeeeery long string that makes it ugly in one line", 1).toInvocation();
        assertThat(invocation.toString(), endsWith( 
                "simpleMethod(" +
                "\n" +
                "    \"veeeeery long string that makes it ugly in one line\"," +
                "\n" +
                "    1" +
                "\n" +
                ");"));
    }
    
    @Test
    public void shouldTransformArgumentsToMatchers() throws Exception {
        Invocation i = new InvocationBuilder().args("foo", new String[] {"bar"}).toInvocation();
        List matchers = i.argumentsToMatchers();

        assertEquals(2, matchers.size());
        assertEquals(Equals.class, matchers.get(0).getClass());
        assertEquals(ArrayEquals.class, matchers.get(1).getClass());
    }
    
    @Test
    public void shouldKnowValidThrowables() throws Exception {
        Invocation invocation = new InvocationBuilder().method("canThrowException").toInvocation();
        assertFalse(invocation.isValidException(new Exception()));
        assertTrue(invocation.isValidException(new CharacterCodingException()));
    }
    
    class Foo {
        public String bark() {
            return "woof";
        }
    }
    
    @Test
    public void shouldBeAbleToCallRealMethod() throws Throwable {
        //when
        Invocation invocation = invocationOf(Foo.class, "bark", new RealMethod() {
            public Object invoke(Object target, Object[] arguments) throws Throwable {
                return new Foo().bark();
            }});
        //then
        assertEquals("woof", invocation.callRealMethod());
    }
    
    @Test
    public void shouldScreamWhenCallingRealMethodOnInterface() throws Throwable {
        //given
        Invocation invocationOnInterface = new InvocationBuilder().toInvocation();

        try {
            //when
            invocationOnInterface.callRealMethod();
            //then
            fail();
        } catch(MockitoException e) {}
    }
}