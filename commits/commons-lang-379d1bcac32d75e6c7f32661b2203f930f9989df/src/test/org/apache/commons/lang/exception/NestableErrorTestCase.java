/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowledgement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgement may appear in the software itself,
 *    if and wherever such third-party acknowledgements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package org.apache.commons.lang.exception;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Tests the org.apache.commons.lang.exception.NestableError class.
 *
 * @author <a href="mailto:steven@caswell.name">Steven Caswell</a>
 * @version $Id: NestableErrorTestCase.java,v 1.5 2003/08/18 02:22:27 bayard Exp $
 */
public class NestableErrorTestCase extends AbstractNestableTestCase {
    
    /**
     * Construct a new instance of
     * <code>NestableErrorTestCase</code>.
     *
     * @param name test case name
     */
    public NestableErrorTestCase(String name)
    {
        super(name);
    }

    /**
     * Sets up instance variables required by this test case.
     */
    public void setUp()
    {
    }

    /**
     * Returns the test suite
     *
     * @return the test suite
     */
    public static Test suite()
    {
        return new TestSuite(NestableErrorTestCase.class);
    }
    
    /**
     * Tears down instance variables required by this test case.
     */
    public void tearDown()
    {
    }

    /**
     * Command line entry point for running the test suite.
     *
     * @param args array of command line arguments
     */
    public static void main(String args[])
    {
        TestRunner.run(suite());
    }

    /**
     * @see AbstractNestableTestCase#getNestable()
     */
    public Nestable getNestable()
    {
        return new NestableError();
    }    
    
    /**
     * @see AbstractNestableTestCase#getNestable(Nestable)
     */
    public Nestable getNestable(Nestable n)
    {
        return new NestableError((Throwable) n);
    }
    
    /**
     * @see AbstractNestableTestCase#getNestable(String)
     */
    public Nestable getNestable(String msg)
    {
        return new NestableError(msg);
    }
    
    /**
     * @see AbstractNestableTestCase#getNestable(Throwable)
     */
    public Nestable getNestable(Throwable t)
    {
        return new NestableError(t);
    }
    
    /**
     * @see AbstractNestableTestCase#getNestable(String, Throwable)
     */
    public Nestable getNestable(String msg, Throwable t)
    {
        return new NestableError(msg, t);
    }
    
    /**
     * @see AbstractNestableTestCase#getNestable(String, Nestable)
     */
    public Nestable getNestable(String msg, Nestable n)
    {
        return new NestableError(msg, (Throwable) n);
    }
    
    /**
     * @see AbstractNestableTestCase#getTester1(Throwable)
     */
    public Nestable getTester1(Throwable t)
    {
        return new NestableErrorTester1(t);
    }
    
    /**
     * @see AbstractNestableTestCase#getTester1(Nestable)
     */
    public Nestable getTester1(Nestable n)
    {
        return new NestableErrorTester1((Throwable) n);
    }
    
    /**
     * @see AbstractNestableTestCase#getTester1(String, Throwable)
     */
    public Nestable getTester1(String msg, Throwable t)
    {
        return new NestableErrorTester1(msg, t);
    }
    
    /**
     * @see AbstractNestableTestCase#getTester1(String, Nestable)
     */
    public Nestable getTester1(String msg, Nestable n)
    {
        return new NestableErrorTester1(msg, (Throwable) n);
    }
    
    /**
     * @see AbstractNestableTestCase#getTester1Class()
     */
    public Class getTester1Class()
    {
        return NestableErrorTester1.class;
    }
    
    /**
     * @see AbstractNestableTestCase#getTester2(String, Throwable)
     */
    public Nestable getTester2(String msg, Throwable t)
    {
        return new NestableErrorTester2(msg, t);
    }
    
    /**
     * @see AbstractNestableTestCase#getTester2(String, Nestable)
     */
    public Nestable getTester2(String msg, Nestable n)
    {
        return new NestableErrorTester2(msg, (Throwable) n);
    }
    
    /**
     * @see AbstractNestableTestCase#getTester2Class()
     */
    public Class getTester2Class()
    {
        return NestableErrorTester2.class;
    }
    
    /**
     * @see AbstractNestableTestCase#getThrowable(String)
     */
    public Throwable getThrowable(String msg)
    {
        return new Error(msg);
    }
    
    /**
     * @see AbstractNestableTestCase#getThrowableClass()
     */
    public Class getThrowableClass()
    {
        return Error.class;
    }
    
}

/**
 * First nestable tester implementation for use in test cases.
 */
class NestableErrorTester1 extends NestableError
{
    public NestableErrorTester1()
    {
        super();
    }

    public NestableErrorTester1(String reason, Throwable cause)
    {
        super(reason, cause);
    }
    
    public NestableErrorTester1(String reason)
    {
        super(reason);
    }
    
    public NestableErrorTester1(Throwable cause)
    {
        super(cause);
    }
    
}

/**
 * Second nestable tester implementation for use in test cases.
 */
class NestableErrorTester2 extends NestableError
{
    public NestableErrorTester2()
    {
        super();
    }
    
    public NestableErrorTester2(String reason, Throwable cause)
    {
        super(reason, cause);
    }
    
    public NestableErrorTester2(String reason)
    {
        super(reason);
    }
    
    public NestableErrorTester2(Throwable cause)
    {
        super(cause);
    }
    
}
