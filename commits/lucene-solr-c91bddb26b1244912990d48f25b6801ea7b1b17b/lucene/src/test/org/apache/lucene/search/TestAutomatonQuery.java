package org.apache.lucene.search;

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

import java.io.IOException;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.BasicOperations;

public class TestAutomatonQuery extends LuceneTestCase {
  private IndexSearcher searcher;
  
  private final String FN = "field";
  
  public void setUp() throws Exception {
    super.setUp();
    RAMDirectory directory = new RAMDirectory();
    IndexWriter writer = new IndexWriter(directory, new MockAnalyzer(), true,
        IndexWriter.MaxFieldLength.LIMITED);
    Document doc = new Document();
    Field titleField = new Field("title", "some title", Field.Store.NO,
        Field.Index.ANALYZED);
    Field field = new Field(FN, "this is document one 2345", Field.Store.NO,
        Field.Index.ANALYZED);
    Field footerField = new Field("footer", "a footer", Field.Store.NO,
        Field.Index.ANALYZED);
    doc.add(titleField);
    doc.add(field);
    doc.add(footerField);
    writer.addDocument(doc);
    field.setValue("some text from doc two a short piece 5678.91");
    writer.addDocument(doc);
    field.setValue("doc three has some different stuff"
        + " with numbers 1234 5678.9 and letter b");
    writer.addDocument(doc);
    writer.optimize();
    writer.close();
    searcher = new IndexSearcher(directory, true);
  }
  
  public void tearDown() throws Exception {
    searcher.close();
    super.tearDown();
  }
  
  private Term newTerm(String value) {
    return new Term(FN, value);
  }
  
  private int automatonQueryNrHits(AutomatonQuery query) throws IOException {
    return searcher.search(query, 5).totalHits;
  }
  
  private void assertAutomatonHits(int expected, Automaton automaton)
      throws IOException {
    AutomatonQuery query = new AutomatonQuery(newTerm("bogus"), automaton);
    
    query.setRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_QUERY_REWRITE);
    assertEquals(expected, automatonQueryNrHits(query));
    
    query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
    assertEquals(expected, automatonQueryNrHits(query));
    
    query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
    assertEquals(expected, automatonQueryNrHits(query));
    
    query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
    assertEquals(expected, automatonQueryNrHits(query));
  }
  
  /**
   * Test some very simple automata.
   */
  public void testBasicAutomata() throws IOException {
    assertAutomatonHits(0, BasicAutomata.makeEmpty());
    assertAutomatonHits(0, BasicAutomata.makeEmptyString());
    assertAutomatonHits(2, BasicAutomata.makeAnyChar());
    assertAutomatonHits(3, BasicAutomata.makeAnyString());
    assertAutomatonHits(2, BasicAutomata.makeString("doc"));
    assertAutomatonHits(1, BasicAutomata.makeChar('a'));
    assertAutomatonHits(2, BasicAutomata.makeCharRange('a', 'b'));
    assertAutomatonHits(2, BasicAutomata.makeInterval(1233, 2346, 0));
    assertAutomatonHits(1, BasicAutomata.makeInterval(0, 2000, 0));
    assertAutomatonHits(2, BasicOperations.union(BasicAutomata.makeChar('a'),
        BasicAutomata.makeChar('b')));
    assertAutomatonHits(0, BasicOperations.intersection(BasicAutomata
        .makeChar('a'), BasicAutomata.makeChar('b')));
    assertAutomatonHits(1, BasicOperations.minus(BasicAutomata.makeCharRange('a', 'b'), 
        BasicAutomata.makeChar('a')));
  }
  
  /**
   * Test that a nondeterministic automaton works correctly. (It should will be
   * determinized)
   */
  public void testNFA() throws IOException {
    // accept this or three, the union is an NFA (two transitions for 't' from
    // initial state)
    Automaton nfa = BasicOperations.union(BasicAutomata.makeString("this"),
        BasicAutomata.makeString("three"));
    assertAutomatonHits(2, nfa);
  }
  
  public void testEquals() {
    AutomatonQuery a1 = new AutomatonQuery(newTerm("foobar"), BasicAutomata
        .makeString("foobar"));
    // reference to a1
    AutomatonQuery a2 = a1;
    // same as a1 (accepts the same language, same term)
    AutomatonQuery a3 = new AutomatonQuery(newTerm("foobar"), BasicOperations
        .concatenate(BasicAutomata.makeString("foo"), BasicAutomata
            .makeString("bar")));
    // different than a1 (same term, but different language)
    AutomatonQuery a4 = new AutomatonQuery(newTerm("foobar"), BasicAutomata
        .makeString("different"));
    // different than a1 (different term, same language)
    AutomatonQuery a5 = new AutomatonQuery(newTerm("blah"), BasicAutomata
        .makeString("foobar"));
    
    assertEquals(a1, a2);
    
    assertEquals(a1, a3);
    
    assertEquals(a1.toString(), a3.toString());
    
    // different class
    AutomatonQuery w1 = new WildcardQuery(newTerm("foobar"));
    // different class
    AutomatonQuery w2 = new RegexpQuery(newTerm("foobar"));
    
    assertFalse(a1.equals(w1));
    assertFalse(a1.equals(w2));
    assertFalse(w1.equals(w2));
    assertFalse(a1.equals(a4));
    assertFalse(a1.equals(a5));
    assertFalse(a1.equals(null));
  }
  
  /**
   * Test that rewriting to a single term works as expected, preserves
   * MultiTermQuery semantics.
   */
  public void testRewriteSingleTerm() throws IOException {
    AutomatonQuery aq = new AutomatonQuery(newTerm("bogus"), BasicAutomata
        .makeString("piece"));
    assertTrue(aq.getTermsEnum(searcher.getIndexReader()) instanceof SingleTermsEnum);
    assertEquals(1, automatonQueryNrHits(aq));
  }
  
  /**
   * Test that rewriting to a prefix query works as expected, preserves
   * MultiTermQuery semantics.
   */
  public void testRewritePrefix() throws IOException {
    Automaton pfx = BasicAutomata.makeString("do");
    pfx.expandSingleton(); // expand singleton representation for testing
    Automaton prefixAutomaton = BasicOperations.concatenate(pfx, BasicAutomata
        .makeAnyString());
    AutomatonQuery aq = new AutomatonQuery(newTerm("bogus"), prefixAutomaton);
    assertTrue(aq.getTermsEnum(searcher.getIndexReader()) instanceof PrefixTermsEnum);
    assertEquals(3, automatonQueryNrHits(aq));
  }
  
  /**
   * Test handling of the empty language
   */
  public void testEmptyOptimization() throws IOException {
    AutomatonQuery aq = new AutomatonQuery(newTerm("bogus"), BasicAutomata
        .makeEmpty());
    // not yet available: assertTrue(aq.getEnum(searcher.getIndexReader())
    // instanceof EmptyTermEnum);
    assertSame(TermsEnum.EMPTY, aq.getTermsEnum(searcher.getIndexReader()));
    assertEquals(0, automatonQueryNrHits(aq));
  }
}
