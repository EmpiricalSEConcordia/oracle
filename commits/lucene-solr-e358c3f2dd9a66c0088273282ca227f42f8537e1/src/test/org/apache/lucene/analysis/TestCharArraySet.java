package org.apache.lucene.analysis;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.Version;


public class TestCharArraySet extends LuceneTestCase {
  
  static final String[] TEST_STOP_WORDS = {
    "a", "an", "and", "are", "as", "at", "be", "but", "by",
    "for", "if", "in", "into", "is", "it",
    "no", "not", "of", "on", "or", "such",
    "that", "the", "their", "then", "there", "these",
    "they", "this", "to", "was", "will", "with"
  };
  
  
  public void testRehash() throws Exception {
    CharArraySet cas = new CharArraySet(TEST_VERSION_CURRENT, 0, true);
    for(int i=0;i<TEST_STOP_WORDS.length;i++)
      cas.add(TEST_STOP_WORDS[i]);
    assertEquals(TEST_STOP_WORDS.length, cas.size());
    for(int i=0;i<TEST_STOP_WORDS.length;i++)
      assertTrue(cas.contains(TEST_STOP_WORDS[i]));
  }

  public void testNonZeroOffset() {
    String[] words={"Hello","World","this","is","a","test"};
    char[] findme="xthisy".toCharArray();   
    CharArraySet set=new CharArraySet(TEST_VERSION_CURRENT, 10,true);
    set.addAll(Arrays.asList(words));
    assertTrue(set.contains(findme, 1, 4));
    assertTrue(set.contains(new String(findme,1,4)));
    
    // test unmodifiable
    set = CharArraySet.unmodifiableSet(set);
    assertTrue(set.contains(findme, 1, 4));
    assertTrue(set.contains(new String(findme,1,4)));
  }
  
  public void testObjectContains() {
    CharArraySet set = new CharArraySet(TEST_VERSION_CURRENT, 10, true);
    Integer val = Integer.valueOf(1);
    set.add(val);
    assertTrue(set.contains(val));
    assertTrue(set.contains(new Integer(1))); // another integer
    assertTrue(set.contains("1"));
    assertTrue(set.contains(new char[]{'1'}));
    // test unmodifiable
    set = CharArraySet.unmodifiableSet(set);
    assertTrue(set.contains(val));
    assertTrue(set.contains(new Integer(1))); // another integer
    assertTrue(set.contains("1"));
    assertTrue(set.contains(new char[]{'1'}));
  }
  
  public void testClear(){
    CharArraySet set=new CharArraySet(TEST_VERSION_CURRENT, 10,true);
    set.addAll(Arrays.asList(TEST_STOP_WORDS));
    assertEquals("Not all words added", TEST_STOP_WORDS.length, set.size());
    set.clear();
    assertEquals("not empty", 0, set.size());
    for(int i=0;i<TEST_STOP_WORDS.length;i++)
      assertFalse(set.contains(TEST_STOP_WORDS[i]));
    set.addAll(Arrays.asList(TEST_STOP_WORDS));
    assertEquals("Not all words added", TEST_STOP_WORDS.length, set.size());
    for(int i=0;i<TEST_STOP_WORDS.length;i++)
      assertTrue(set.contains(TEST_STOP_WORDS[i]));
  }
  
  public void testModifyOnUnmodifiable(){
    CharArraySet set=new CharArraySet(TEST_VERSION_CURRENT, 10, true);
    set.addAll(Arrays.asList(TEST_STOP_WORDS));
    final int size = set.size();
    set = CharArraySet.unmodifiableSet(set);
    assertEquals("Set size changed due to unmodifiableSet call" , size, set.size());
    String NOT_IN_SET = "SirGallahad";
    assertFalse("Test String already exists in set", set.contains(NOT_IN_SET));
    
    try{
      set.add(NOT_IN_SET.toCharArray());  
      fail("Modified unmodifiable set");
    }catch (UnsupportedOperationException e) {
      // expected
      assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
      assertEquals("Size of unmodifiable set has changed", size, set.size());
    }
    
    try{
      set.add(NOT_IN_SET);  
      fail("Modified unmodifiable set");
    }catch (UnsupportedOperationException e) {
      // expected
      assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
      assertEquals("Size of unmodifiable set has changed", size, set.size());
    }
    
    try{
      set.add(new StringBuilder(NOT_IN_SET));  
      fail("Modified unmodifiable set");
    }catch (UnsupportedOperationException e) {
      // expected
      assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
      assertEquals("Size of unmodifiable set has changed", size, set.size());
    }
    
    try{
      set.clear();  
      fail("Modified unmodifiable set");
    }catch (UnsupportedOperationException e) {
      // expected
      assertFalse("Changed unmodifiable set", set.contains(NOT_IN_SET));
      assertEquals("Size of unmodifiable set has changed", size, set.size());
    }
    try{
      set.add((Object) NOT_IN_SET);  
      fail("Modified unmodifiable set");
    }catch (UnsupportedOperationException e) {
      // expected
      assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
      assertEquals("Size of unmodifiable set has changed", size, set.size());
    }
    
    // This test was changed in 3.1, as a contains() call on the given Collection using the "correct" iterator's
    // current key (now a char[]) on a Set<String> would not hit any element of the CAS and therefor never call
    // remove() on the iterator
    try{
      set.removeAll(new CharArraySet(TEST_VERSION_CURRENT, Arrays.asList(TEST_STOP_WORDS), true));  
      fail("Modified unmodifiable set");
    }catch (UnsupportedOperationException e) {
      // expected
      assertEquals("Size of unmodifiable set has changed", size, set.size());
    }
    
    try{
      set.retainAll(new CharArraySet(TEST_VERSION_CURRENT, Arrays.asList(NOT_IN_SET), true));  
      fail("Modified unmodifiable set");
    }catch (UnsupportedOperationException e) {
      // expected
      assertEquals("Size of unmodifiable set has changed", size, set.size());
    }
    
    try{
      set.addAll(Arrays.asList(new String[]{NOT_IN_SET}));  
      fail("Modified unmodifiable set");
    }catch (UnsupportedOperationException e) {
      // expected
      assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
    }
    
    for (int i = 0; i < TEST_STOP_WORDS.length; i++) {
      assertTrue(set.contains(TEST_STOP_WORDS[i]));  
    }
  }
  
  public void testUnmodifiableSet(){
    CharArraySet set = new CharArraySet(TEST_VERSION_CURRENT, 10,true);
    set.addAll(Arrays.asList(TEST_STOP_WORDS));
    set.add(Integer.valueOf(1));
    final int size = set.size();
    set = CharArraySet.unmodifiableSet(set);
    assertEquals("Set size changed due to unmodifiableSet call" , size, set.size());
    for (String stopword : TEST_STOP_WORDS) {
      assertTrue(set.contains(stopword));
    }
    assertTrue(set.contains(Integer.valueOf(1)));
    assertTrue(set.contains("1"));
    assertTrue(set.contains(new char[]{'1'}));
    
    try{
      CharArraySet.unmodifiableSet(null);
      fail("can not make null unmodifiable");
    }catch (NullPointerException e) {
      // expected
    }
  }
  
  public void testSupplementaryChars() {
    String missing = "Term %s is missing in the set";
    String falsePos = "Term %s is in the set but shouldn't";
    // for reference see
    // http://unicode.org/cldr/utility/list-unicodeset.jsp?a=[[%3ACase_Sensitive%3DTrue%3A]%26[^[\u0000-\uFFFF]]]&esc=on
    String[] upperArr = new String[] {"Abc\ud801\udc1c",
        "\ud801\udc1c\ud801\udc1cCDE", "A\ud801\udc1cB"};
    String[] lowerArr = new String[] {"abc\ud801\udc44",
        "\ud801\udc44\ud801\udc44cde", "a\ud801\udc44b"};
    CharArraySet set = new CharArraySet(TEST_VERSION_CURRENT, Arrays.asList(TEST_STOP_WORDS), true);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertTrue(String.format(missing, lowerArr[i]), set.contains(lowerArr[i]));
    }
    set = new CharArraySet(TEST_VERSION_CURRENT, Arrays.asList(TEST_STOP_WORDS), false);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, lowerArr[i]), set.contains(lowerArr[i]));
    }
  }
  
  public void testSingleHighSurrogate() {
    String missing = "Term %s is missing in the set";
    String falsePos = "Term %s is in the set but shouldn't";
    String[] upperArr = new String[] { "ABC\uD800", "ABC\uD800EfG",
        "\uD800EfG", "\uD800\ud801\udc1cB" };

    String[] lowerArr = new String[] { "abc\uD800", "abc\uD800efg",
        "\uD800efg", "\uD800\ud801\udc44b" };
    CharArraySet set = new CharArraySet(TEST_VERSION_CURRENT, Arrays
        .asList(TEST_STOP_WORDS), true);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertTrue(String.format(missing, lowerArr[i]), set.contains(lowerArr[i]));
    }
    set = new CharArraySet(TEST_VERSION_CURRENT, Arrays.asList(TEST_STOP_WORDS),
        false);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, upperArr[i]), set
          .contains(lowerArr[i]));
    }
  }
  
  /**
   * @deprecated remove this test when lucene 3.0 "broken unicode 4" support is
   *             no longer needed.
   */
  @Deprecated
  public void testSupplementaryCharsBWCompat() {
    String missing = "Term %s is missing in the set";
    String falsePos = "Term %s is in the set but shouldn't";
    // for reference see
    // http://unicode.org/cldr/utility/list-unicodeset.jsp?a=[[%3ACase_Sensitive%3DTrue%3A]%26[^[\u0000-\uFFFF]]]&esc=on
    String[] upperArr = new String[] {"Abc\ud801\udc1c",
        "\ud801\udc1c\ud801\udc1cCDE", "A\ud801\udc1cB"};
    String[] lowerArr = new String[] {"abc\ud801\udc44",
        "\ud801\udc44\ud801\udc44cde", "a\ud801\udc44b"};
    CharArraySet set = new CharArraySet(Version.LUCENE_30, Arrays.asList(TEST_STOP_WORDS), true);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, lowerArr[i]), set.contains(lowerArr[i]));
    }
    set = new CharArraySet(Version.LUCENE_30, Arrays.asList(TEST_STOP_WORDS), false);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, lowerArr[i]), set.contains(lowerArr[i]));
    }
  }

  /**
   * @deprecated remove this test when lucene 3.0 "broken unicode 4" support is
   *             no longer needed.
   */
  @Deprecated
  public void testSingleHighSurrogateBWComapt() {
    String missing = "Term %s is missing in the set";
    String falsePos = "Term %s is in the set but shouldn't";
    String[] upperArr = new String[] { "ABC\uD800", "ABC\uD800EfG",
        "\uD800EfG", "\uD800\ud801\udc1cB" };

    String[] lowerArr = new String[] { "abc\uD800", "abc\uD800efg",
        "\uD800efg", "\uD800\ud801\udc44b" };
    CharArraySet set = new CharArraySet(Version.LUCENE_30, Arrays
        .asList(TEST_STOP_WORDS), true);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      if (i == lowerArr.length - 1)
        assertFalse(String.format(falsePos, lowerArr[i]), set
            .contains(lowerArr[i]));
      else
        assertTrue(String.format(missing, lowerArr[i]), set
            .contains(lowerArr[i]));
    }
    set = new CharArraySet(Version.LUCENE_30, Arrays.asList(TEST_STOP_WORDS),
        false);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, lowerArr[i]), set
          .contains(lowerArr[i]));
    }
  }
  
  public void testCopyCharArraySetBWCompat() {
    CharArraySet setIngoreCase = new CharArraySet(TEST_VERSION_CURRENT, 10, true);
    CharArraySet setCaseSensitive = new CharArraySet(TEST_VERSION_CURRENT, 10, false);

    List<String> stopwords = Arrays.asList(TEST_STOP_WORDS);
    List<String> stopwordsUpper = new ArrayList<String>();
    for (String string : stopwords) {
      stopwordsUpper.add(string.toUpperCase());
    }
    setIngoreCase.addAll(Arrays.asList(TEST_STOP_WORDS));
    setIngoreCase.add(Integer.valueOf(1));
    setCaseSensitive.addAll(Arrays.asList(TEST_STOP_WORDS));
    setCaseSensitive.add(Integer.valueOf(1));

    CharArraySet copy = CharArraySet.copy(setIngoreCase);
    CharArraySet copyCaseSens = CharArraySet.copy(setCaseSensitive);

    assertEquals(setIngoreCase.size(), copy.size());
    assertEquals(setCaseSensitive.size(), copy.size());

    assertTrue(copy.containsAll(stopwords));
    assertTrue(copy.containsAll(stopwordsUpper));
    assertTrue(copyCaseSens.containsAll(stopwords));
    for (String string : stopwordsUpper) {
      assertFalse(copyCaseSens.contains(string));
    }
    // test adding terms to the copy
    List<String> newWords = new ArrayList<String>();
    for (String string : stopwords) {
      newWords.add(string+"_1");
    }
    copy.addAll(newWords);
    
    assertTrue(copy.containsAll(stopwords));
    assertTrue(copy.containsAll(stopwordsUpper));
    assertTrue(copy.containsAll(newWords));
    // new added terms are not in the source set
    for (String string : newWords) {
      assertFalse(setIngoreCase.contains(string));  
      assertFalse(setCaseSensitive.contains(string));  

    }
  }
  
  /**
   * Test the static #copy() function with a CharArraySet as a source
   */
  public void testCopyCharArraySet() {
    CharArraySet setIngoreCase = new CharArraySet(TEST_VERSION_CURRENT, 10, true);
    CharArraySet setCaseSensitive = new CharArraySet(TEST_VERSION_CURRENT, 10, false);

    List<String> stopwords = Arrays.asList(TEST_STOP_WORDS);
    List<String> stopwordsUpper = new ArrayList<String>();
    for (String string : stopwords) {
      stopwordsUpper.add(string.toUpperCase());
    }
    setIngoreCase.addAll(Arrays.asList(TEST_STOP_WORDS));
    setIngoreCase.add(Integer.valueOf(1));
    setCaseSensitive.addAll(Arrays.asList(TEST_STOP_WORDS));
    setCaseSensitive.add(Integer.valueOf(1));

    CharArraySet copy = CharArraySet.copy(TEST_VERSION_CURRENT, setIngoreCase);
    CharArraySet copyCaseSens = CharArraySet.copy(TEST_VERSION_CURRENT, setCaseSensitive);

    assertEquals(setIngoreCase.size(), copy.size());
    assertEquals(setCaseSensitive.size(), copy.size());

    assertTrue(copy.containsAll(stopwords));
    assertTrue(copy.containsAll(stopwordsUpper));
    assertTrue(copyCaseSens.containsAll(stopwords));
    for (String string : stopwordsUpper) {
      assertFalse(copyCaseSens.contains(string));
    }
    // test adding terms to the copy
    List<String> newWords = new ArrayList<String>();
    for (String string : stopwords) {
      newWords.add(string+"_1");
    }
    copy.addAll(newWords);
    
    assertTrue(copy.containsAll(stopwords));
    assertTrue(copy.containsAll(stopwordsUpper));
    assertTrue(copy.containsAll(newWords));
    // new added terms are not in the source set
    for (String string : newWords) {
      assertFalse(setIngoreCase.contains(string));  
      assertFalse(setCaseSensitive.contains(string));  

    }
  }
  
  /**
   * Test the static #copy() function with a JDK {@link Set} as a source
   */
  public void testCopyJDKSet() {
    Set<String> set = new HashSet<String>();

    List<String> stopwords = Arrays.asList(TEST_STOP_WORDS);
    List<String> stopwordsUpper = new ArrayList<String>();
    for (String string : stopwords) {
      stopwordsUpper.add(string.toUpperCase());
    }
    set.addAll(Arrays.asList(TEST_STOP_WORDS));

    CharArraySet copy = CharArraySet.copy(TEST_VERSION_CURRENT, set);

    assertEquals(set.size(), copy.size());
    assertEquals(set.size(), copy.size());

    assertTrue(copy.containsAll(stopwords));
    for (String string : stopwordsUpper) {
      assertFalse(copy.contains(string));
    }
    
    List<String> newWords = new ArrayList<String>();
    for (String string : stopwords) {
      newWords.add(string+"_1");
    }
    copy.addAll(newWords);
    
    assertTrue(copy.containsAll(stopwords));
    assertTrue(copy.containsAll(newWords));
    // new added terms are not in the source set
    for (String string : newWords) {
      assertFalse(set.contains(string));  
    }
  }
  
  /**
   * Tests a special case of {@link CharArraySet#copy(Version, Set)} where the
   * set to copy is the {@link CharArraySet#EMPTY_SET}
   */
  public void testCopyEmptySet() {
    assertSame(CharArraySet.EMPTY_SET, 
        CharArraySet.copy(TEST_VERSION_CURRENT, CharArraySet.EMPTY_SET));
  }

  /**
   * Smoketests the static empty set
   */
  public void testEmptySet() {
    assertEquals(0, CharArraySet.EMPTY_SET.size());
    
    assertTrue(CharArraySet.EMPTY_SET.isEmpty());
    for (String stopword : TEST_STOP_WORDS) {
      assertFalse(CharArraySet.EMPTY_SET.contains(stopword));
    }
    assertFalse(CharArraySet.EMPTY_SET.contains((Object) "foo"));
    assertFalse(CharArraySet.EMPTY_SET.contains((Object) "foo".toCharArray()));
    assertFalse(CharArraySet.EMPTY_SET.contains("foo".toCharArray(),0,3));
  }
  
  /**
   * Test for NPE
   */
  public void testContainsWithNull() {
    CharArraySet set = new CharArraySet(TEST_VERSION_CURRENT, 1, true);
    try {
      set.contains((char[]) null, 0, 10);
      fail("null value must raise NPE");
    } catch (NullPointerException e) {}
    try {
      set.contains((CharSequence) null);
      fail("null value must raise NPE");
    } catch (NullPointerException e) {}
    try {
      set.contains((Object) null);
      fail("null value must raise NPE");
    } catch (NullPointerException e) {}
  }
  
  @Deprecated @SuppressWarnings("unchecked")
  public void testIterator() {
    HashSet<String> hset = new HashSet<String>();
    hset.addAll(Arrays.asList(TEST_STOP_WORDS));

    assertTrue("in 3.0 version, iterator should be CharArraySetIterator",
      ((Iterator) CharArraySet.copy(Version.LUCENE_30, hset).iterator()) instanceof CharArraySet.CharArraySetIterator);

    CharArraySet set = CharArraySet.copy(TEST_VERSION_CURRENT, hset);
    assertFalse("in current version, iterator should not be CharArraySetIterator",
      ((Iterator) set.iterator()) instanceof CharArraySet.CharArraySetIterator);
    
    Iterator<String> it = set.stringIterator();
    assertTrue(it instanceof CharArraySet.CharArraySetIterator);
    while (it.hasNext()) {
      // as the set returns String instances, this must work:
      assertTrue(hset.contains(it.next()));
      try {
        it.remove();
        fail("remove() should not work on CharArraySetIterator");
      } catch (UnsupportedOperationException uoe) {
        // pass
      }
    }
  }
  
  public void testToString() {
    CharArraySet set = CharArraySet.copy(TEST_VERSION_CURRENT, Collections.singleton("test"));
    assertEquals("[test]", set.toString());
    set.add("test2");
    assertTrue(set.toString().contains(", "));
    
    set = CharArraySet.copy(Version.LUCENE_30, Collections.singleton("test"));
    assertEquals("[test]", set.toString());
    set.add("test2");
    assertTrue(set.toString().contains(", "));
  }
}
