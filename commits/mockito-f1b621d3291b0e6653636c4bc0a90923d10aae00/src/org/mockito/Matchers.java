/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito;

import org.mockito.internal.matchers.*;
import org.mockito.internal.matchers.apachecommons.ReflectionEquals;
import org.mockito.internal.progress.HandyReturnValues;
import org.mockito.internal.progress.MockingProgress;
import org.mockito.internal.progress.ThreadSafeMockingProgress;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Allow flexible verification or stubbing. See also {@link AdditionalMatchers}.
 * <p>
 * {@link Mockito} extends Matchers so to get access to all matchers just import Mockito class statically.
 * <pre class="code"><code class="java">
 *  //stubbing using anyInt() argument matcher
 *  when(mockedList.get(anyInt())).thenReturn("element");
 *  
 *  //following prints "element"
 *  System.out.println(mockedList.get(999));
 *  
 *  //you can also verify using argument matcher
 *  verify(mockedList).get(anyInt());
 * </code></pre>
 * Scroll down to see all methods - full list of matchers.
 * <p>
 * <b>Warning:</b>
 * <p>
 * If you are using argument matchers, <b>all arguments</b> have to be provided by matchers.
 * <p>
 * E.g: (example shows verification but the same applies to stubbing):
 * <pre class="code"><code class="java">
 *   verify(mock).someMethod(anyInt(), anyString(), <b>eq("third argument")</b>);
 *   //above is correct - eq() is also an argument matcher
 *   
 *   verify(mock).someMethod(anyInt(), anyString(), <b>"third argument"</b>);
 *   //above is incorrect - exception will be thrown because third argument is given without argument matcher.
 * </code></pre>
 * <p>
 * Matcher methods like <code>anyObject()</code>, <code>eq()</code> <b>do not</b> return matchers.
 * Internally, they record a matcher on a stack and return a dummy value (usually null).
 * This implementation is due static type safety imposed by java compiler.
 * The consequence is that you cannot use <code>anyObject()</code>, <code>eq()</code> methods outside of verified/stubbed method.
 *
 * <h1>Custom Argument Matchers</h1>
 * 
 * Use {@link Matchers#argThat} method and pass an instance of {@link MockitoMatcher}.
 * <p>
 * Before you start implementing your own custom argument matcher, make sure you check out {@link ArgumentCaptor} api.
 * <p>
 * So, how to implement your own argument matcher?
 * First, you might want to subclass {@link MockitoMatcher}.
 * <p>
 * Example:
 * 
 * <pre class="code"><code class="java">
 *   class ListOfTwoElements implements MockitoMatcher&lt;List&gt; {
 *      public boolean matches(Object list) {
 *          return ((List) list).size() == 2;
 *      }
 *   }
 *   
 *   List mock = mock(List.class);
 *   
 *   when(mock.addAll(argThat(new ListOfTwoElements()))).thenReturn(true);
 *   
 *   mock.addAll(Arrays.asList("one", "two"));
 *   
 *   verify(mock).addAll(argThat(new ListOfTwoElements()));
 * </code></pre>
 * 
 * To keep it readable you may want to extract method, e.g:
 * <pre class="code"><code class="java">
 *   verify(mock).addAll(<b>argThat(new ListOfTwoElements())</b>);
 *   //becomes
 *   verify(mock).addAll(<b>listOfTwoElements()</b>);
 * </code></pre>
 *
 * <b>Warning:</b> Be reasonable with using complicated argument matching, especially custom argument matchers, as it can make the test less readable. 
 * Sometimes it's better to implement equals() for arguments that are passed to mocks 
 * (Mockito naturally uses equals() for argument matching). 
 * This can make the test cleaner. 
 * <p>
 * Also, <b>sometimes {@link ArgumentCaptor} may be a better fit</b> than custom matcher. 
 * For example, if custom argument matcher is not likely to be reused
 * or you just need it to assert on argument values to complete verification of behavior.
 */
@SuppressWarnings("unchecked")
public class Matchers {
    
    private static final MockingProgress MOCKING_PROGRESS = new ThreadSafeMockingProgress();

    /**
     * Any <code>boolean</code> or non-null <code>Boolean</code>
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>false</code>.
     */
    public static boolean anyBoolean() {
        return reportMatcher(new InstanceOf(Boolean.class)).returnFalse();
    }

    /**
     * Any <code>byte</code> or non-null <code>Byte</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>0</code>.
     */
    public static byte anyByte() {
        return reportMatcher(new InstanceOf(Byte.class)).returnZero();
    }

    /**
     * Any <code>char</code> or non-null <code>Character</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>0</code>.
     */
    public static char anyChar() {
        return reportMatcher(new InstanceOf(Character.class)).returnChar();
    }

    /**
     * Any int or non-null Integer.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>0</code>.
     */
    public static int anyInt() {
        return reportMatcher(new InstanceOf(Integer.class)).returnZero();
    }

    /**
     * Any <code>long</code> or non-null <code>Long</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>0</code>.
     */
    public static long anyLong() {
        return reportMatcher(new InstanceOf(Long.class)).returnZero();
    }

    /**
     * Any <code>float</code> or non-null <code>Float</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>0</code>.
     */
    public static float anyFloat() {
        return reportMatcher(new InstanceOf(Float.class)).returnZero();
    }

    /**
     * Any <code>double</code> or non-null <code>Double</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>0</code>.
     */
    public static double anyDouble() {
        return reportMatcher(new InstanceOf(Double.class)).returnZero();
    }

    /**
     * Any <code>short</code> or non-null <code>Short</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>0</code>.
     */
    public static short anyShort() {
        return reportMatcher(new InstanceOf(Short.class)).returnZero();
    }

    /**
     * Matches anything, including null.
     * <p>
     * This is an alias of: {@link #any()} and {@link #any(java.lang.Class)}
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>null</code>.
     */
    public static <T> T anyObject() {
        return (T) reportMatcher(Any.ANY).returnNull();
    }

    /**
     * Any vararg, meaning any number and values of arguments.
     * <p>
     * Example:
     * <pre class="code"><code class="java">
     *   //verification:
     *   mock.foo(1, 2);
     *   mock.foo(1, 2, 3, 4);
     *
     *   verify(mock, times(2)).foo(anyVararg());
     *
     *   //stubbing:
     *   when(mock.foo(anyVararg()).thenReturn(100);
     *
     *   //prints 100
     *   System.out.println(mock.foo(1, 2));
     *   //also prints 100
     *   System.out.println(mock.foo(1, 2, 3, 4));
     * </code></pre>
     * See examples in javadoc for {@link Matchers} class
     *
     * @return <code>null</code>.
     */
    public static <T> T anyVararg() {
        return (T) reportMatcher(AnyVararg.ANY_VARARG).returnNull();
    }
    
    /**
     * Matches any object, including nulls
     * <p>
     * This method doesn't do type checks with the given parameter, it is only there
     * to avoid casting in your code. This might however change (type checks could
     * be added) in a future major release.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * <p>
     * This is an alias of: {@link #any()} and {@link #anyObject()}
     * <p>
     * @return <code>null</code>.
     */
    public static <T> T any(Class<T> clazz) {
        return (T) reportMatcher(Any.ANY).returnFor(clazz);
    }
    
    /**
     * Matches anything, including nulls
     * <p>
     * Shorter alias to {@link Matchers#anyObject()}
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * <p>
     * This is an alias of: {@link #anyObject()} and {@link #any(java.lang.Class)}
     * <p>
     * @return <code>null</code>.
     */
    public static <T> T any() {
        return anyObject();
    }

    /**
     * Any non-null <code>String</code>
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return empty String ("")
     */
    public static String anyString() {
        return reportMatcher(new InstanceOf(String.class)).returnString();
    }
    
    /**
     * Any non-null <code>List</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return empty List.
     */
    public static List anyList() {
        return reportMatcher(new InstanceOf(List.class)).returnList();
    }
    
    /**
     * Generic friendly alias to {@link Matchers#anyList()}.
     * It's an alternative to &#064;SuppressWarnings("unchecked") to keep code clean of compiler warnings.
     * <p>
     * Any non-null <code>List</code>.
     * <p>
     * This method doesn't do type checks with the given parameter, it is only there
     * to avoid casting in your code. This might however change (type checks could
     * be added) in a future major release.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param clazz Type owned by the list to avoid casting
     * @return empty List.
     */
    public static <T> List<T> anyListOf(Class<T> clazz) {
        return anyList();
    }
    
    /**
     * Any non-null <code>Set</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     *
     * @return empty Set
     */
    public static Set anySet() {
        return reportMatcher(new InstanceOf(Set.class)).returnSet();
    }
    
    /**
     * Generic friendly alias to {@link Matchers#anySet()}.
     * It's an alternative to &#064;SuppressWarnings("unchecked") to keep code clean of compiler warnings.
     * <p>
     * Any non-null <code>Set</code>.
     * <p>
     * This method doesn't do type checks with the given parameter, it is only there
     * to avoid casting in your code. This might however change (type checks could
     * be added) in a future major release.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     *
     * @param clazz Type owned by the Set to avoid casting
     * @return empty Set
     */
    public static <T> Set<T> anySetOf(Class<T> clazz) {
        return anySet();
    }

    /**
     * Any non-null <code>Map</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return empty Map.
     */
    public static Map anyMap() {
        return reportMatcher(new InstanceOf(Map.class)).returnMap();
    }

    /**
     * Generic friendly alias to {@link Matchers#anyMap()}.
     * It's an alternative to &#064;SuppressWarnings("unchecked") to keep code clean of compiler warnings.
     * <p>
     * Any non-null <code>Map</code>.
     * <p>
     * This method doesn't do type checks with the given parameter, it is only there
     * to avoid casting in your code. This might however change (type checks could
     * be added) in a future major release.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     *
     * @param keyClazz Type of the map key to avoid casting
     * @param valueClazz Type of the value to avoid casting
     * @return empty Map.
     */
    public static <K, V>  Map<K, V> anyMapOf(Class<K> keyClazz, Class<V> valueClazz) {
        return anyMap();
    }
    
    /**
     * Any non-null <code>Collection</code>.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return empty Collection.
     */
    public static Collection anyCollection() {
        return reportMatcher(new InstanceOf(Collection.class)).returnList();
    }    
    
    /**
     * Generic friendly alias to {@link Matchers#anyCollection()}.
     * It's an alternative to &#064;SuppressWarnings("unchecked") to keep code clean of compiler warnings.
     * <p>
     * Any non-null <code>Collection</code>.
     * <p>
     * This method doesn't do type checks with the given parameter, it is only there
     * to avoid casting in your code. This might however change (type checks could
     * be added) in a future major release.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param clazz Type owned by the collection to avoid casting
     * @return empty Collection.
     */
    public static <T> Collection<T> anyCollectionOf(Class<T> clazz) {
        return anyCollection();
    }    

    /**
     * <code>Object</code> argument that implements the given class.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param <T>
     *            the accepted type.
     * @param clazz
     *            the class of the accepted type.
     * @return <code>null</code>.
     */
    public static <T> T isA(Class<T> clazz) {
        return reportMatcher(new InstanceOf(clazz)).<T>returnFor(clazz);
    }

    /**
     * <code>boolean</code> argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>0</code>.
     */
    public static boolean eq(boolean value) {
        return reportMatcher(new Equals(value)).returnFalse();
    }

    /**
     * <code>byte</code> argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>0</code>.
     */
    public static byte eq(byte value) {
        return reportMatcher(new Equals(value)).returnZero();
    }

    /**
     * <code>char</code> argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>0</code>.
     */
    public static char eq(char value) {
        return reportMatcher(new Equals(value)).returnChar();
    }

    /**
     * <code>double</code> argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>0</code>.
     */
    public static double eq(double value) {
        return reportMatcher(new Equals(value)).returnZero();
    }

    /**
     * <code>float</code> argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>0</code>.
     */
    public static float eq(float value) {
        return reportMatcher(new Equals(value)).returnZero();
    }
    
    /**
     * <code>int</code> argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>0</code>.
     */
    public static int eq(int value) {
        return reportMatcher(new Equals(value)).returnZero();
    }

    /**
     * <code>long</code> argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>0</code>.
     */
    public static long eq(long value) {
        return reportMatcher(new Equals(value)).returnZero();
    }

    /**
     * <code>short</code> argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>0</code>.
     */
    public static short eq(short value) {
        return reportMatcher(new Equals(value)).returnZero();
    }

    /**
     * Object argument that is equal to the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @return <code>null</code>.
     */
    public static <T> T eq(T value) {
        return (T) reportMatcher(new Equals(value)).<T>returnFor(value);
    }

    /**
     * Object argument that is reflection-equal to the given value with support for excluding
     * selected fields from a class.
     * <p>
     * This matcher can be used when equals() is not implemented on compared objects.
     * Matcher uses java reflection API to compare fields of wanted and actual object.
     * <p>
     * Works similarly to EqualsBuilder.reflectionEquals(this, other, exlucdeFields) from
     * apache commons library.
     * <p>
     * <b>Warning</b> The equality check is shallow!
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param value
     *            the given value.
     * @param excludeFields
     *            fields to exclude, if field does not exist it is ignored.
     * @return <code>null</code>.
     */
    public static <T> T refEq(T value, String... excludeFields) {
        return reportMatcher(new ReflectionEquals(value, excludeFields)).<T>returnNull();
    }
    
    /**
     * Object argument that is the same as the given value.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param <T>
     *            the type of the object, it is passed through to prevent casts.
     * @param value
     *            the given value.
     * @return <code>null</code>.
     */
    public static <T> T same(T value) {
        return (T) reportMatcher(new Same(value)).<T>returnFor(value);
    }

    /**
     * <code>null</code> argument.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>null</code>.
     */
    public static Object isNull() {
        return reportMatcher(Null.NULL).returnNull();
    }

    /**
     * <code>null</code> argument.
     * The class argument is provided to avoid casting.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     *
     * @param clazz Type to avoid casting
     * @return <code>null</code>.
     */
    public static <T> T isNull(Class<T> clazz) {
        return (T) reportMatcher(Null.NULL).returnNull();
    }

    /**
     * Not <code>null</code> argument.
     * <p>
     * alias to {@link Matchers#isNotNull()}
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>null</code>.
     */
    public static Object notNull() {
        return reportMatcher(NotNull.NOT_NULL).returnNull();
    }

    /**
     * Not <code>null</code> argument, not necessary of the given class.
     * The class argument is provided to avoid casting.
     * <p>
     * alias to {@link Matchers#isNotNull(Class)}
     * <p>
     * See examples in javadoc for {@link Matchers} class
     *
     * @param clazz Type to avoid casting
     * @return <code>null</code>.
     */
    public static <T> T notNull(Class<T> clazz) {
        return (T) reportMatcher(NotNull.NOT_NULL).returnNull();
    }
    
    /**
     * Not <code>null</code> argument.
     * <p>
     * alias to {@link Matchers#notNull()}
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @return <code>null</code>.
     */
    public static Object isNotNull() {
        return notNull();
    }

    /**
     * Not <code>null</code> argument, not necessary of the given class.
     * The class argument is provided to avoid casting.
     * <p>
     * alias to {@link Matchers#notNull(Class)}
     * <p>
     * See examples in javadoc for {@link Matchers} class
     *
     * @param clazz Type to avoid casting
     * @return <code>null</code>.
     */
    public static <T> T isNotNull(Class<T> clazz) {
        return notNull(clazz);
    }

    /**
     * <code>String</code> argument that contains the given substring.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param substring
     *            the substring.
     * @return empty String ("").
     */
    public static String contains(String substring) {
        return reportMatcher(new Contains(substring)).returnString();
    }

    /**
     * <code>String</code> argument that matches the given regular expression.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param regex
     *            the regular expression.
     * @return empty String ("").
     */
    public static String matches(String regex) {
        return reportMatcher(new Matches(regex)).returnString();
    }

    /**
     * <code>String</code> argument that ends with the given suffix.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param suffix
     *            the suffix.
     * @return empty String ("").
     */
    public static String endsWith(String suffix) {
        return reportMatcher(new EndsWith(suffix)).returnString();
    }

    /**
     * <code>String</code> argument that starts with the given prefix.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param prefix
     *            the prefix.
     * @return empty String ("").
     */
    public static String startsWith(String prefix) {
        return reportMatcher(new StartsWith(prefix)).returnString();
    }

    /**
     * Allows creating custom argument matchers.
     * <p>
     * In rare cases when the parameter is a primitive then you <b>*must*</b> use relevant intThat(), floatThat(), etc. method.
     * This way you will avoid <code>NullPointerException</code> during auto-unboxing.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>null</code>.
     */
    public static <T> T argThat(MockitoMatcher<T> matcher) {
        return reportMatcher(matcher).<T>returnNull();
    }
    
    /**
     * Allows creating custom <code>Character</code> argument matchers.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>0</code>.
     */
    public static char charThat(MockitoMatcher<Character> matcher) {
        return reportMatcher(matcher).returnChar();
    }
    
    /**
     * Allows creating custom <code>Boolean</code> argument matchers.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>false</code>.
     */
    public static boolean booleanThat(MockitoMatcher<Boolean> matcher) {
        return reportMatcher(matcher).returnFalse();
    }
    
    /**
     * Allows creating custom <code>Byte</code> argument matchers.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>0</code>.
     */
    public static byte byteThat(MockitoMatcher<Byte> matcher) {
        return reportMatcher(matcher).returnZero();
    }
    
    /**
     * Allows creating custom <code>Short</code> argument matchers.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>0</code>.
     */
    public static short shortThat(MockitoMatcher<Short> matcher) {
        return reportMatcher(matcher).returnZero();
    }
    
    /**
     * Allows creating custom <code>Integer</code> argument matchers.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>0</code>.
     */
    public static int intThat(MockitoMatcher<Integer> matcher) {
        return reportMatcher(matcher).returnZero();
    }

    /**
     * Allows creating custom <code>Long</code> argument matchers.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>0</code>.
     */
    public static long longThat(MockitoMatcher<Long> matcher) {
        return reportMatcher(matcher).returnZero();
    }
    
    /**
     * Allows creating custom <code>Float</code> argument matchers.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>0</code>.
     */
    public static float floatThat(MockitoMatcher<Float> matcher) {
        return reportMatcher(matcher).returnZero();
    }
    
    /**
     * Allows creating custom <code>Double</code> argument matchers.
     * <p>
     * See examples in javadoc for {@link Matchers} class
     * 
     * @param matcher decides whether argument matches
     * @return <code>0</code>.
     */
    public static double doubleThat(MockitoMatcher<Double> matcher) {
        return reportMatcher(matcher).returnZero();
    }

    private static HandyReturnValues reportMatcher(MockitoMatcher<?> matcher) {
        return MOCKING_PROGRESS.getArgumentMatcherStorage().reportMatcher(matcher);
    }
}
