/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.util.List;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Scott Andrews
 * @author Juergen Hoeller
 */
public class Spr7283Tests {

    @Test
    public void testListWithInconsistentElementType() {
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spr7283.xml", getClass());
		List list = ctx.getBean("list", List.class);
		assertEquals(2, list.size());
		assertTrue(list.get(0) instanceof A);
		assertTrue(list.get(1) instanceof B);
    }


    public static class A {
    	public A() {}
    }

    public static class B {
    	public B() {}
    }

}
