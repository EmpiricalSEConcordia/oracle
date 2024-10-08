/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.expression.spel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.BindingPoint;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.antlr.SpelAntlrExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Testcases showing the common scenarios/use-cases for picking up the expression language support.
 * The first test shows very basic usage, just drop it in and go.  By 'standard infrastructure', it means:<br>
 * <ul>
 * <li>The context classloader is used (so, the default classpath)
 * <li>Some basic type converters are included
 * <li>properties/methods/constructors are discovered and invoked using reflection
 * </ul>
 * The scenarios after that then how how to plug in extensions:<br>
 * <ul>
 * <li>Adding entries to the classpath that will be used to load types and define well known 'imports'
 * <li>Defining variables that are then accessible in the expression
 * <li>Changing the root context object against which non-qualified references are resolved
 * <li>Registering java methods as functions callable from the expression
 * <li>Adding a basic property resolver
 * <li>Adding an advanced (better performing) property resolver
 * <li>Adding your own type converter to support conversion between any types you like
 * </ul>
 * 
 * @author Andy Clement
 */
public class ExpressionLanguageScenarioTests extends ExpressionTestCase {

	/**
	 * Scenario: using the standard infrastructure and running simple expression evaluation.
	 */
	public void testScenario_UsingStandardInfrastructure() {
		try {
			// Create a parser
			SpelAntlrExpressionParser parser = new SpelAntlrExpressionParser();
			// Parse an expression
			Expression expr = parser.parseExpression("new String('hello world')");
			// Evaluate it using a 'standard' context
			Object value = expr.getValue();
			// They are reusable
			value = expr.getValue();
				
			assertEquals("hello world", value);
			assertEquals(String.class, value.getClass());
		} catch (EvaluationException ee) {
			ee.printStackTrace();
			fail("Unexpected Exception: " + ee.getMessage());
		} catch (ParseException pe) {
			pe.printStackTrace();
			fail("Unexpected Exception: " + pe.getMessage());
		}
	}

	/**
	 * Scenario: using the standard context but adding your own variables
	 */
	public void testScenario_DefiningVariablesThatWillBeAccessibleInExpressions() throws Exception {
		// Create a parser
		SpelAntlrExpressionParser parser = new SpelAntlrExpressionParser();
		// Use the standard evaluation context
		StandardEvaluationContext ctx = new StandardEvaluationContext();
		ctx.setVariable("favouriteColour","blue");
		List<Integer> primes = new ArrayList<Integer>();
		primes.addAll(Arrays.asList(2,3,5,7,11,13,17));
		ctx.setVariable("primes",primes);
		
		Expression expr = parser.parseExpression("#favouriteColour");
		Object value = expr.getValue(ctx);
		assertEquals("blue", value);

		expr = parser.parseExpression("#primes.get(1)");
		value = expr.getValue(ctx);
		assertEquals(3, value);

		// all prime numbers > 10 from the list (using selection ?{...})
		expr = parser.parseExpression("#primes.?[#this>10]");
		value = expr.getValue(ctx);
		assertEquals("[11, 13, 17]", value.toString());			
	}

	
	static class TestClass {
		public String str;
		private int property;
		public int getProperty() { return property; }
		public void setProperty(int i) { property = i; }
	}
	
	/**
	 * Scenario: using your own root context object
	 */
	public void testScenario_UsingADifferentRootContextObject() throws Exception {
		// Create a parser
		SpelAntlrExpressionParser parser = new SpelAntlrExpressionParser();
		// Use the standard evaluation context
		StandardEvaluationContext ctx = new StandardEvaluationContext();

		TestClass tc = new TestClass();
		tc.setProperty(42);
		tc.str = "wibble";
		ctx.setRootObject(tc);
		
		// read it, set it, read it again
		Expression expr = parser.parseExpression("str");
		Object value = expr.getValue(ctx);
		assertEquals("wibble", value);			
		expr = parser.parseExpression("str");
		expr.setValue(ctx, "wobble");
		expr = parser.parseExpression("str");
		value = expr.getValue(ctx);
		assertEquals("wobble", value);
		// or using assignment within the expression
		expr = parser.parseExpression("str='wabble'");
		value = expr.getValue(ctx);
		expr = parser.parseExpression("str");
		value = expr.getValue(ctx);
		assertEquals("wabble", value);
		
		// private property will be accessed through getter()
		expr = parser.parseExpression("property");
		value = expr.getValue(ctx);
		assertEquals(42, value);

		// ... and set through setter
		expr = parser.parseExpression("property=4");
		value = expr.getValue(ctx);
		expr = parser.parseExpression("property");
		value = expr.getValue(ctx);
		assertEquals(4,value);
	}
	
	public static String repeat(String s) { return s+s; }

	/**
	 * Scenario: using your own java methods and calling them from the expression
	 */
	public void testScenario_RegisteringJavaMethodsAsFunctionsAndCallingThem() throws SecurityException, NoSuchMethodException {
		try {
			// Create a parser
			SpelAntlrExpressionParser parser = new SpelAntlrExpressionParser();
			// Use the standard evaluation context
			StandardEvaluationContext ctx = new StandardEvaluationContext();
			ctx.registerFunction("repeat",ExpressionLanguageScenarioTests.class.getDeclaredMethod("repeat",String.class));
			
			Expression expr = parser.parseExpression("#repeat('hello')");
			Object value = expr.getValue(ctx);
			assertEquals("hellohello", value);

		} catch (EvaluationException ee) {
			ee.printStackTrace();
			fail("Unexpected Exception: " + ee.getMessage());
		} catch (ParseException pe) {
			pe.printStackTrace();
			fail("Unexpected Exception: " + pe.getMessage());
		}
	}
	
	/**
	 * Scenario: add a property resolver that will get called in the resolver chain, this one only supports reading.
	 */
	public void testScenario_AddingYourOwnPropertyResolvers_1() throws Exception {
		// Create a parser
		SpelAntlrExpressionParser parser = new SpelAntlrExpressionParser();
		// Use the standard evaluation context
		StandardEvaluationContext ctx = new StandardEvaluationContext();

		ctx.addPropertyAccessor(new FruitColourAccessor());
		Expression expr = parser.parseExpression("orange");
		Object value = expr.getValue(ctx);
		assertEquals(Color.orange, value);

		try {
			expr.setValue(ctx, Color.blue);
			fail("Should not be allowed to set oranges to be blue !");
		} catch (SpelException ee) {
			assertEquals(ee.getMessageUnformatted(), SpelMessages.PROPERTY_OR_FIELD_NOT_WRITABLE_ON_NULL);
		}
	}

	public void testScenario_AddingYourOwnPropertyResolvers_2() throws Exception {
		// Create a parser
		SpelAntlrExpressionParser parser = new SpelAntlrExpressionParser();
		// Use the standard evaluation context
		StandardEvaluationContext ctx = new StandardEvaluationContext();

		ctx.addPropertyAccessor(new VegetableColourAccessor());
		Expression expr = parser.parseExpression("pea");
		Object value = expr.getValue(ctx);
		assertEquals(Color.green, value);

		try {
			expr.setValue(ctx, Color.blue);
			fail("Should not be allowed to set peas to be blue !");
		}
		catch (SpelException ee) {
			assertEquals(ee.getMessageUnformatted(), SpelMessages.PROPERTY_OR_FIELD_NOT_WRITABLE_ON_NULL);
		}
	}


	/**
	 * Regardless of the current context object, or root context object, this resolver can tell you what colour a fruit is !
	 * It only supports property reading, not writing.  To support writing it would need to override canWrite() and write()
	 */
	private static class FruitColourAccessor implements PropertyAccessor {

		private static Map<String,Color> propertyMap = new HashMap<String,Color>();
		private static BindingPoint mapElementTypeDescriptor = BindingPoint.valueOf(Color.class);

		static {
			propertyMap.put("banana",Color.yellow);
			propertyMap.put("apple",Color.red);
			propertyMap.put("orange",Color.orange);
		}

		/**
		 * Null means you might be able to read any property, if an earlier property resolver hasn't beaten you to it
		 */
		public Class<?>[] getSpecificTargetClasses() {
			return null;
		}

		public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
			return propertyMap.containsKey(name);
		}

		public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
			return new TypedValue(propertyMap.get(name),mapElementTypeDescriptor);
		}

		public boolean canWrite(EvaluationContext context, Object target, String name) throws AccessException {
			return false;
		}

		public void write(EvaluationContext context, Object target, String name, Object newValue)
				throws AccessException {
		}

	}


	/**
	 * Regardless of the current context object, or root context object, this resolver can tell you what colour a vegetable is !
	 * It only supports property reading, not writing.
	 */
	private static class VegetableColourAccessor implements PropertyAccessor {

		private static Map<String,Color> propertyMap = new HashMap<String,Color>();

		static {
			propertyMap.put("carrot",Color.orange);
			propertyMap.put("pea",Color.green);
		}

		/**
		 * Null means you might be able to read any property, if an earlier property resolver hasn't beaten you to it
		 */
		public Class<?>[] getSpecificTargetClasses() {
			return null;
		}

		public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
			return propertyMap.containsKey(name);
		}

		public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
			return new TypedValue(propertyMap.get(name),BindingPoint.valueOf(Color.class));
		}

		public boolean canWrite(EvaluationContext context, Object target, String name) throws AccessException {
			return false;
		}

		public void write(EvaluationContext context, Object target, String name, Object newValue) throws AccessException {
		}
		
	}
}
