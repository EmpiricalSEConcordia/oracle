package org.apache.lucene.analysis;

/**
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.util.English;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Set;


public class TestStopFilter extends BaseTokenStreamTestCase {

  private final static boolean VERBOSE = false;
  
  // other StopFilter functionality is already tested by TestStopAnalyzer

  public void testExactCase() throws IOException {
    StringReader reader = new StringReader("Now is The Time");
    String[] stopWords = new String[] { "is", "the", "Time" };
    TokenStream stream = new StopFilter(false, new WhitespaceTokenizer(reader), stopWords);
    final TermAttribute termAtt = stream.getAttribute(TermAttribute.class);
    assertTrue(stream.incrementToken());
    assertEquals("Now", termAtt.term());
    assertTrue(stream.incrementToken());
    assertEquals("The", termAtt.term());
    assertFalse(stream.incrementToken());
  }

  public void testIgnoreCase() throws IOException {
    StringReader reader = new StringReader("Now is The Time");
    String[] stopWords = new String[] { "is", "the", "Time" };
    TokenStream stream = new StopFilter(false, new WhitespaceTokenizer(reader), stopWords, true);
    final TermAttribute termAtt = stream.getAttribute(TermAttribute.class);
    assertTrue(stream.incrementToken());
    assertEquals("Now", termAtt.term());
    assertFalse(stream.incrementToken());
  }

  public void testStopFilt() throws IOException {
    StringReader reader = new StringReader("Now is The Time");
    String[] stopWords = new String[] { "is", "the", "Time" };
    Set stopSet = StopFilter.makeStopSet(stopWords);
    TokenStream stream = new StopFilter(false, new WhitespaceTokenizer(reader), stopSet);
    final TermAttribute termAtt = stream.getAttribute(TermAttribute.class);
    assertTrue(stream.incrementToken());
    assertEquals("Now", termAtt.term());
    assertTrue(stream.incrementToken());
    assertEquals("The", termAtt.term());
    assertFalse(stream.incrementToken());
  }

  /**
   * Test Position increments applied by StopFilter with and without enabling this option.
   */
  public void testStopPositons() throws IOException {
    StringBuilder sb = new StringBuilder();
    ArrayList a = new ArrayList();
    for (int i=0; i<20; i++) {
      String w = English.intToEnglish(i).trim();
      sb.append(w).append(" ");
      if (i%3 != 0) a.add(w);
    }
    log(sb.toString());
    String stopWords[] = (String[]) a.toArray(new String[0]);
    for (int i=0; i<a.size(); i++) log("Stop: "+stopWords[i]);
    Set stopSet = StopFilter.makeStopSet(stopWords);
    // with increments
    StringReader reader = new StringReader(sb.toString());
    StopFilter stpf = new StopFilter(false, new WhitespaceTokenizer(reader), stopSet);
    doTestStopPositons(stpf,true);
    // without increments
    reader = new StringReader(sb.toString());
    stpf = new StopFilter(false, new WhitespaceTokenizer(reader), stopSet);
    doTestStopPositons(stpf,false);
    // with increments, concatenating two stop filters
    ArrayList a0 = new ArrayList();
    ArrayList a1 = new ArrayList();
    for (int i=0; i<a.size(); i++) {
      if (i%2==0) { 
        a0.add(a.get(i));
      } else {
        a1.add(a.get(i));
      }
    }
    String stopWords0[] = (String[]) a0.toArray(new String[0]);
    for (int i=0; i<a0.size(); i++) log("Stop0: "+stopWords0[i]);
    String stopWords1[] = (String[]) a1.toArray(new String[0]);
    for (int i=0; i<a1.size(); i++) log("Stop1: "+stopWords1[i]);
    Set stopSet0 = StopFilter.makeStopSet(stopWords0);
    Set stopSet1 = StopFilter.makeStopSet(stopWords1);
    reader = new StringReader(sb.toString());
    StopFilter stpf0 = new StopFilter(false, new WhitespaceTokenizer(reader), stopSet0); // first part of the set
    stpf0.setEnablePositionIncrements(true);
    StopFilter stpf01 = new StopFilter(false, stpf0, stopSet1); // two stop filters concatenated!
    doTestStopPositons(stpf01,true);
  }
  
  private void doTestStopPositons(StopFilter stpf, boolean enableIcrements) throws IOException {
    log("---> test with enable-increments-"+(enableIcrements?"enabled":"disabled"));
    stpf.setEnablePositionIncrements(enableIcrements);
    TermAttribute termAtt = stpf.getAttribute(TermAttribute.class);
    PositionIncrementAttribute posIncrAtt = stpf.getAttribute(PositionIncrementAttribute.class);
    for (int i=0; i<20; i+=3) {
      assertTrue(stpf.incrementToken());
      log("Token "+i+": "+stpf);
      String w = English.intToEnglish(i).trim();
      assertEquals("expecting token "+i+" to be "+w,w,termAtt.term());
      assertEquals("all but first token must have position increment of 3",enableIcrements?(i==0?1:3):1,posIncrAtt.getPositionIncrement());
    }
    assertFalse(stpf.incrementToken());
  }
  
  // print debug info depending on VERBOSE
  private static void log(String s) {
    if (VERBOSE) {
      System.out.println(s);
    }
  }
}
