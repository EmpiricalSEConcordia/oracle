package org.junit;

import java.lang.reflect.Array;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.internal.ArrayComparisonFailure;
import org.junit.internal.InexactComparisonCriteria;

/**
 * A set of assertion methods useful for writing tests. Only failed assertions
 * are recorded. These methods can be used directly:
 * <code>Assert.assertEquals(...)</code>, however, they read better if they
 * are referenced through static import:<br/>
 * 
 * <pre>
 * import static org.junit.Assert.*;
 *    ...
 *    assertEquals(...);
 * </pre>
 * 
 * @see AssertionError
 */
public class Assert {
	/**
	 * Protect constructor since it is a static only class
	 */
	protected Assert() {
	}

	/**
	 * Asserts that a condition is true. If it isn't it throws an
	 * {@link AssertionError} with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param condition
	 *            condition to be checked
	 */
	static public void assertTrue(String message, boolean condition) {
		if (!condition)
			fail(message);
	}

	/**
	 * Asserts that a condition is true. If it isn't it throws an
	 * {@link AssertionError} without a message.
	 * 
	 * @param condition
	 *            condition to be checked
	 */
	static public void assertTrue(boolean condition) {
		assertTrue(null, condition);
	}

	/**
	 * Asserts that a condition is false. If it isn't it throws an
	 * {@link AssertionError} with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param condition
	 *            condition to be checked
	 */
	static public void assertFalse(String message, boolean condition) {
		assertTrue(message, !condition);
	}

	/**
	 * Asserts that a condition is false. If it isn't it throws an
	 * {@link AssertionError} without a message.
	 * 
	 * @param condition
	 *            condition to be checked
	 */
	static public void assertFalse(boolean condition) {
		assertFalse(null, condition);
	}

	/**
	 * Fails a test with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @see AssertionError
	 */
	static public void fail(String message) {
		throw new AssertionError(message == null ? "" : message);
	}

	/**
	 * Fails a test with no message.
	 */
	static public void fail() {
		fail(null);
	}

	/**
	 * Asserts that two objects are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message. If
	 * <code>expected</code> and <code>actual</code> are <code>null</code>,
	 * they are considered equal.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expected
	 *            expected value
	 * @param actual
	 *            actual value
	 */
	static public void assertEquals(String message, Object expected,
			Object actual) {
		if (expected == null && actual == null)
			return;
		if (expected != null && isEquals(expected, actual))
			return;
		else if (expected instanceof String && actual instanceof String) {
			String cleanMessage= message == null ? "" : message;
			throw new ComparisonFailure(cleanMessage, (String) expected,
					(String) actual);
		} else
			failNotEquals(message, expected, actual);
	}

	private static boolean isEquals(Object expected, Object actual) {
		return expected.equals(actual);
	}

	/**
	 * Asserts that two objects are equal. If they are not, an
	 * {@link AssertionError} without a message is thrown. If
	 * <code>expected</code> and <code>actual</code> are <code>null</code>,
	 * they are considered equal.
	 * 
	 * @param expected
	 *            expected value
	 * @param actual
	 *            the value to check against <code>expected</code>
	 */
	static public void assertEquals(Object expected, Object actual) {
		assertEquals(null, expected, actual);
	}

	/**
	 * Asserts that two object arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message. If
	 * <code>expecteds</code> and <code>actuals</code> are <code>null</code>,
	 * they are considered equal.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            expected values.
	 * @param actuals
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            actual values
	 */
	public static void assertArrayEquals(String message, Object[] expecteds,
			Object[] actuals) throws ArrayComparisonFailure {
		internalArrayEquals(message, expecteds, actuals);
	}

	/**
	 * Asserts that two object arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown. If <code>expected</code> and
	 * <code>actual</code> are <code>null</code>, they are considered
	 * equal.
	 * 
	 * @param expecteds
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            expected values
	 * @param actuals
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            actual values
	 */
	public static void assertArrayEquals(Object[] expecteds, Object[] actuals) {
		assertArrayEquals(null, expecteds, actuals);
	}

	/**
	 * Asserts that two byte arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            byte array with expected values.
	 * @param actuals
	 *            byte array with actual values
	 */
	public static void assertArrayEquals(String message, byte[] expecteds,
			byte[] actuals) throws ArrayComparisonFailure {
		internalArrayEquals(message, expecteds, actuals);
	}

	/**
	 * Asserts that two byte arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown.
	 * 
	 * @param expecteds
	 *            byte array with expected values.
	 * @param actuals
	 *            byte array with actual values
	 */
	public static void assertArrayEquals(byte[] expecteds, byte[] actuals) {
		assertArrayEquals(null, expecteds, actuals);
	}

	/**
	 * Asserts that two char arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            char array with expected values.
	 * @param actuals
	 *            char array with actual values
	 */
	public static void assertArrayEquals(String message, char[] expecteds,
			char[] actuals) throws ArrayComparisonFailure {
		internalArrayEquals(message, expecteds, actuals);
	}

	/**
	 * Asserts that two char arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown.
	 * 
	 * @param expecteds
	 *            char array with expected values.
	 * @param actuals
	 *            char array with actual values
	 */
	public static void assertArrayEquals(char[] expecteds, char[] actuals) {
		assertArrayEquals(null, expecteds, actuals);
	}

	/**
	 * Asserts that two short arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            short array with expected values.
	 * @param actuals
	 *            short array with actual values
	 */
	public static void assertArrayEquals(String message, short[] expecteds,
			short[] actuals) throws ArrayComparisonFailure {
		internalArrayEquals(message, expecteds, actuals);
	}

	/**
	 * Asserts that two short arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown.
	 * 
	 * @param expecteds
	 *            short array with expected values.
	 * @param actuals
	 *            short array with actual values
	 */
	public static void assertArrayEquals(short[] expecteds, short[] actuals) {
		assertArrayEquals(null, expecteds, actuals);
	}

	/**
	 * Asserts that two int arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            int array with expected values.
	 * @param actuals
	 *            int array with actual values
	 */
	public static void assertArrayEquals(String message, int[] expecteds,
			int[] actuals) throws ArrayComparisonFailure {
		internalArrayEquals(message, expecteds, actuals);
	}

	/**
	 * Asserts that two int arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown.
	 * 
	 * @param expecteds
	 *            int array with expected values.
	 * @param actuals
	 *            int array with actual values
	 */
	public static void assertArrayEquals(int[] expecteds, int[] actuals) {
		assertArrayEquals(null, expecteds, actuals);
	}

	/**
	 * Asserts that two long arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            long array with expected values.
	 * @param actuals
	 *            long array with actual values
	 */
	public static void assertArrayEquals(String message, long[] expecteds,
			long[] actuals) throws ArrayComparisonFailure {
		internalArrayEquals(message, expecteds, actuals);
	}
	
	/**
	 * Asserts that two long arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown.
	 * 
	 * @param expecteds
	 *            long array with expected values.
	 * @param actuals
	 *            long array with actual values
	 */
	public static void assertArrayEquals(long[] expecteds, long[] actuals) {
		assertArrayEquals(null, expecteds, actuals);
	}
	
	// TODO: fix javadoc
	/**
	 * Asserts that two double arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            long array with expected values.
	 * @param actuals
	 *            long array with actual values
	 */
	public static void assertArrayEquals(String message, double[] expecteds,
			double[] actuals, double delta) throws ArrayComparisonFailure {
		new InexactComparisonCriteria(delta).internalArrayEquals(message, expecteds, actuals);
	}

	/**
	 * Asserts that two double arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown.
	 * 
	 * @param expecteds
	 *            long array with expected values.
	 * @param actuals
	 *            long array with actual values
	 */
	public static void assertArrayEquals(double[] expecteds, double[] actuals, double delta) {
		assertArrayEquals(null, expecteds, actuals, delta);
	}

	/**
	 * Asserts that two object arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message. If
	 * <code>expecteds</code> and <code>actuals</code> are <code>null</code>,
	 * they are considered equal.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            expected values.
	 * @param actuals
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            actual values
	 */
	private static void internalArrayEquals(String message, Object expecteds,
			Object actuals) throws ArrayComparisonFailure {
		if (expecteds == actuals)
			return;
		String header= message == null ? "" : message + ": ";
		int expectedsLength= assertArraysAreSameLength(expecteds, actuals,
				header);

		for (int i= 0; i < expectedsLength; i++) {
			Object expected= Array.get(expecteds, i);
			Object actual= Array.get(actuals, i);
			// TODO (Nov 6, 2008 12:58:55 PM): Is this a DUP?
			if (isArray(expected) && isArray(actual)) {
				try {
					internalArrayEquals(message, expected, actual);
				} catch (ArrayComparisonFailure e) {
					e.addDimension(i);
					throw e;
				}
			} else
				try {
					assertEquals(expected, actual);
				} catch (AssertionError e) {
					throw new ArrayComparisonFailure(header, e, i);
				}
		}
	}
	

	public static int assertArraysAreSameLength(Object expecteds,
			Object actuals, String header) {
		if (expecteds == null)
			fail(header + "expected array was null");
		if (actuals == null)
			fail(header + "actual array was null");
		int actualsLength= Array.getLength(actuals);
		int expectedsLength= Array.getLength(expecteds);
		if (actualsLength != expectedsLength)
			fail(header + "array lengths differed, expected.length="
					+ expectedsLength + " actual.length=" + actualsLength);
		return expectedsLength;
	}

	public static boolean isArray(Object expected) {
		return expected != null && expected.getClass().isArray();
	}

	/**
	 * Asserts that two doubles or floats are equal to within a positive delta.
	 * If they are not, an {@link AssertionError} is thrown with the given
	 * message. If the expected value is infinity then the delta value is
	 * ignored. NaNs are considered equal:
	 * <code>assertEquals(Double.NaN, Double.NaN, *)</code> passes
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expected
	 *            expected value
	 * @param actual
	 *            the value to check against <code>expected</code>
	 * @param delta
	 *            the maximum delta between <code>expected</code> and
	 *            <code>actual</code> for which both numbers are still
	 *            considered equal.
	 */
	static public void assertEquals(String message, double expected,
			double actual, double delta) {
		if (Double.compare(expected, actual) == 0)
			return;
		if (!(Math.abs(expected - actual) <= delta))
			failNotEquals(message, new Double(expected), new Double(actual));
	}

	/**
	 * Asserts that two longs are equal. If they are not, an
	 * {@link AssertionError} is thrown.
	 * 
	 * @param expected
	 *            expected long value.
	 * @param actual
	 *            actual long value
	 */
	static public void assertEquals(long expected, long actual) {
		assertEquals(null, expected, actual);
	}

	/**
	 * Asserts that two longs are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expected
	 *            long expected value.
	 * @param actual
	 *            long actual value
	 */
	static public void assertEquals(String message, long expected, long actual) {
		assertEquals(message, (Long) expected, (Long) actual);
	}

	/**
	 * @deprecated Use
	 *             <code>assertEquals(double expected, double actual, double epsilon)</code>
	 *             instead
	 */
	@Deprecated
	static public void assertEquals(double expected, double actual) {
		assertEquals(null, expected, actual);
	}

	/**
	 * @deprecated Use
	 *             <code>assertEquals(String message, double expected, double actual, double epsilon)</code>
	 *             instead
	 */
	@Deprecated
	static public void assertEquals(String message, double expected,
			double actual) {
		fail("Use assertEquals(expected, actual, delta) to compare floating-point numbers");
	}

	/**
	 * Asserts that two doubles or floats are equal to within a positive delta.
	 * If they are not, an {@link AssertionError} is thrown. If the expected
	 * value is infinity then the delta value is ignored.NaNs are considered
	 * equal: <code>assertEquals(Double.NaN, Double.NaN, *)</code> passes
	 * 
	 * @param expected
	 *            expected value
	 * @param actual
	 *            the value to check against <code>expected</code>
	 * @param delta
	 *            the maximum delta between <code>expected</code> and
	 *            <code>actual</code> for which both numbers are still
	 *            considered equal.
	 */
	static public void assertEquals(double expected, double actual, double delta) {
		assertEquals(null, expected, actual, delta);
	}

	/**
	 * Asserts that an object isn't null. If it is an {@link AssertionError} is
	 * thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param object
	 *            Object to check or <code>null</code>
	 */
	static public void assertNotNull(String message, Object object) {
		assertTrue(message, object != null);
	}

	/**
	 * Asserts that an object isn't null. If it is an {@link AssertionError} is
	 * thrown.
	 * 
	 * @param object
	 *            Object to check or <code>null</code>
	 */
	static public void assertNotNull(Object object) {
		assertNotNull(null, object);
	}

	/**
	 * Asserts that an object is null. If it is not, an {@link AssertionError}
	 * is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param object
	 *            Object to check or <code>null</code>
	 */
	static public void assertNull(String message, Object object) {
		assertTrue(message, object == null);
	}

	/**
	 * Asserts that an object is null. If it isn't an {@link AssertionError} is
	 * thrown.
	 * 
	 * @param object
	 *            Object to check or <code>null</code>
	 */
	static public void assertNull(Object object) {
		assertNull(null, object);
	}

	/**
	 * Asserts that two objects refer to the same object. If they are not, an
	 * {@link AssertionError} is thrown with the given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expected
	 *            the expected object
	 * @param actual
	 *            the object to compare to <code>expected</code>
	 */
	static public void assertSame(String message, Object expected, Object actual) {
		if (expected == actual)
			return;
		failNotSame(message, expected, actual);
	}

	/**
	 * Asserts that two objects refer to the same object. If they are not the
	 * same, an {@link AssertionError} without a message is thrown.
	 * 
	 * @param expected
	 *            the expected object
	 * @param actual
	 *            the object to compare to <code>expected</code>
	 */
	static public void assertSame(Object expected, Object actual) {
		assertSame(null, expected, actual);
	}

	/**
	 * Asserts that two objects do not refer to the same object. If they do
	 * refer to the same object, an {@link AssertionError} is thrown with the
	 * given message.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param unexpected
	 *            the object you don't expect
	 * @param actual
	 *            the object to compare to <code>unexpected</code>
	 */
	static public void assertNotSame(String message, Object unexpected,
			Object actual) {
		if (unexpected == actual)
			failSame(message);
	}

	/**
	 * Asserts that two objects do not refer to the same object. If they do
	 * refer to the same object, an {@link AssertionError} without a message is
	 * thrown.
	 * 
	 * @param unexpected
	 *            the object you don't expect
	 * @param actual
	 *            the object to compare to <code>unexpected</code>
	 */
	static public void assertNotSame(Object unexpected, Object actual) {
		assertNotSame(null, unexpected, actual);
	}

	static private void failSame(String message) {
		String formatted= "";
		if (message != null)
			formatted= message + " ";
		fail(formatted + "expected not same");
	}

	static private void failNotSame(String message, Object expected,
			Object actual) {
		String formatted= "";
		if (message != null)
			formatted= message + " ";
		fail(formatted + "expected same:<" + expected + "> was not:<" + actual
				+ ">");
	}

	static private void failNotEquals(String message, Object expected,
			Object actual) {
		fail(format(message, expected, actual));
	}

	static String format(String message, Object expected, Object actual) {
		String formatted= "";
		if (message != null && !message.equals(""))
			formatted= message + " ";
		String expectedString= String.valueOf(expected);
		String actualString= String.valueOf(actual);
		if (expectedString.equals(actualString))
			return formatted + "expected: "
					+ formatClassAndValue(expected, expectedString)
					+ " but was: " + formatClassAndValue(actual, actualString);
		else
			return formatted + "expected:<" + expectedString + "> but was:<"
					+ actualString + ">";
	}

	private static String formatClassAndValue(Object value, String valueString) {
		String className= value == null ? "null" : value.getClass().getName();
		return className + "<" + valueString + ">";
	}

	/**
	 * Asserts that two object arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown with the given message. If
	 * <code>expecteds</code> and <code>actuals</code> are <code>null</code>,
	 * they are considered equal.
	 * 
	 * @param message
	 *            the identifying message for the {@link AssertionError} (<code>null</code>
	 *            okay)
	 * @param expecteds
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            expected values.
	 * @param actuals
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            actual values
	 * @deprecated use assertArrayEquals
	 */
	@Deprecated
	public static void assertEquals(String message, Object[] expecteds,
			Object[] actuals) {
		assertArrayEquals(message, expecteds, actuals);
	}

	/**
	 * Asserts that two object arrays are equal. If they are not, an
	 * {@link AssertionError} is thrown. If <code>expected</code> and
	 * <code>actual</code> are <code>null</code>, they are considered
	 * equal.
	 * 
	 * @param expecteds
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            expected values
	 * @param actuals
	 *            Object array or array of arrays (multi-dimensional array) with
	 *            actual values
	 * @deprecated use assertArrayEquals
	 */
	@Deprecated
	public static void assertEquals(Object[] expecteds, Object[] actuals) {
		assertArrayEquals(expecteds, actuals);
	}

	/**
	 * Asserts that <code>actual</code> satisfies the condition specified by
	 * <code>matcher</code>. If not, an {@link AssertionError} is thrown with
	 * information about the matcher and failing value. Example:
	 * 
	 * <pre>
	 *   assertThat(0, is(1)); // fails:
	 *     // failure message:
	 *     // expected: is &lt;1&gt; 
	 *     // got value: &lt;0&gt;
	 *   assertThat(0, is(not(1))) // passes
	 * </pre>
	 * 
	 * @param <T>
	 *            the static type accepted by the matcher (this can flag obvious
	 *            compile-time problems such as {@code assertThat(1, is("a"))}
	 * @param actual
	 *            the computed value being compared
	 * @param matcher
	 *            an expression, built of {@link Matcher}s, specifying allowed
	 *            values
	 * 
	 * @see org.hamcrest.CoreMatchers
	 * @see org.junit.matchers.JUnitMatchers
	 */
	public static <T> void assertThat(T actual, Matcher<T> matcher) {
		assertThat("", actual, matcher);
	}

	/**
	 * Asserts that <code>actual</code> satisfies the condition specified by
	 * <code>matcher</code>. If not, an {@link AssertionError} is thrown with
	 * the reason and information about the matcher and failing value. Example:
	 * 
	 * <pre>
	 * :
	 *   assertThat(&quot;Help! Integers don't work&quot;, 0, is(1)); // fails:
	 *     // failure message:
	 *     // Help! Integers don't work
	 *     // expected: is &lt;1&gt; 
	 *     // got value: &lt;0&gt;
	 *   assertThat(&quot;Zero is one&quot;, 0, is(not(1))) // passes
	 * </pre>
	 * 
	 * @param reason
	 *            additional information about the error
	 * @param <T>
	 *            the static type accepted by the matcher (this can flag obvious
	 *            compile-time problems such as {@code assertThat(1, is("a"))}
	 * @param actual
	 *            the computed value being compared
	 * @param matcher
	 *            an expression, built of {@link Matcher}s, specifying allowed
	 *            values
	 * 
	 * @see org.hamcrest.CoreMatchers
	 * @see org.junit.matchers.JUnitMatchers
	 */
	public static <T> void assertThat(String reason, T actual,
			Matcher<T> matcher) {
		if (!matcher.matches(actual)) {
			Description description= new StringDescription();
			description.appendText(reason);
			description.appendText("\nExpected: ");
			matcher.describeTo(description);
			description.appendText("\n     got: ").appendValue(actual)
					.appendText("\n");
			throw new java.lang.AssertionError(description.toString());
		}
	}
}
