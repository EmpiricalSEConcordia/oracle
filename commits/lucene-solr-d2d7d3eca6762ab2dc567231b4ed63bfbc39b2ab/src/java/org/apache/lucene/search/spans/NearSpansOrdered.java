package org.apache.lucene.search.spans;

/**
 * Copyright 2006 The Apache Software Foundation
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

import java.io.IOException;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.lucene.index.IndexReader;

/** A Spans that is formed from the ordered subspans of a SpanNearQuery
 * where the subspans do not overlap and have a maximum slop between them.
 * <p>
 * The formed spans only contains minimum slop matches.<br>
 * The matching slop is computed from the distance(s) between
 * the non overlapping matching Spans.<br>
 * Successive matches are always formed from the successive Spans
 * of the SpanNearQuery.
 * <p>
 * The formed spans may contain overlaps when the slop is at least 1.
 * For example, when querying using
 * <pre>t1 t2 t3</pre>
 * with slop at least 1, the fragment:
 * <pre>t1 t2 t1 t3 t2 t3</pre>
 * matches twice:
 * <pre>t1 t2 .. t3      </pre>
 * <pre>      t1 .. t2 t3</pre>
 */
class NearSpansOrdered implements Spans {
  private final int allowedSlop;
  private boolean firstTime = true;
  private boolean more = false;

  /** The spans in the same order as the SpanNearQuery */
  private final Spans[] subSpans;

  /** Indicates that all subSpans have same doc() */
  private boolean inSameDoc = false;

  private int matchDoc = -1;
  private int matchStart = -1;
  private int matchEnd = -1;

  private final Spans[] subSpansByDoc;
  private final Comparator spanDocComparator = new Comparator() {
    public int compare(Object o1, Object o2) {
      return ((Spans)o1).doc() - ((Spans)o2).doc();
    }
  };
  
  private SpanNearQuery query;

  public NearSpansOrdered(SpanNearQuery spanNearQuery, IndexReader reader)
  throws IOException {
    if (spanNearQuery.getClauses().length < 2) {
      throw new IllegalArgumentException("Less than 2 clauses: "
                                         + spanNearQuery);
    }
    allowedSlop = spanNearQuery.getSlop();
    SpanQuery[] clauses = spanNearQuery.getClauses();
    subSpans = new Spans[clauses.length];
    subSpansByDoc = new Spans[clauses.length];
    for (int i = 0; i < clauses.length; i++) {
      subSpans[i] = clauses[i].getSpans(reader);
      subSpansByDoc[i] = subSpans[i]; // used in toSameDoc()
    }
    query = spanNearQuery; // kept for toString() only.
  }

  // inherit javadocs
  public int doc() { return matchDoc; }

  // inherit javadocs
  public int start() { return matchStart; }

  // inherit javadocs
  public int end() { return matchEnd; }

  // inherit javadocs
  public boolean next() throws IOException {
    if (firstTime) {
      firstTime = false;
      for (int i = 0; i < subSpans.length; i++) {
        if (! subSpans[i].next()) {
          more = false;
          return false;
        }
      }
      more = true;
    }
    return advanceAfterOrdered();
  }

  // inherit javadocs
  public boolean skipTo(int target) throws IOException {
    if (firstTime) {
      firstTime = false;
      for (int i = 0; i < subSpans.length; i++) {
        if (! subSpans[i].skipTo(target)) {
          more = false;
          return false;
        }
      }
      more = true;
    } else if (more && (subSpans[0].doc() < target)) {
      if (subSpans[0].skipTo(target)) {
        inSameDoc = false;
      } else {
        more = false;
        return false;
      }
    }
    return advanceAfterOrdered();
  }
  
  /** Advances the subSpans to just after an ordered match with a minimum slop
   * that is smaller than the slop allowed by the SpanNearQuery.
   * @return true iff there is such a match.
   */
  private boolean advanceAfterOrdered() throws IOException {
    while (more && (inSameDoc || toSameDoc())) {
      if (stretchToOrder() && shrinkToAfterShortestMatch()) {
        return true;
      }
    }
    return false; // no more matches
  }


  /** Advance the subSpans to the same document */
  private boolean toSameDoc() throws IOException {
    Arrays.sort(subSpansByDoc, spanDocComparator);
    int firstIndex = 0;
    int maxDoc = subSpansByDoc[subSpansByDoc.length - 1].doc();
    while (subSpansByDoc[firstIndex].doc() != maxDoc) {
      if (! subSpansByDoc[firstIndex].skipTo(maxDoc)) {
        more = false;
        inSameDoc = false;
        return false;
      }
      maxDoc = subSpansByDoc[firstIndex].doc();
      if (++firstIndex == subSpansByDoc.length) {
        firstIndex = 0;
      }
    }
    for (int i = 0; i < subSpansByDoc.length; i++) {
      assert (subSpansByDoc[i].doc() == maxDoc)
             : " NearSpansOrdered.toSameDoc() spans " + subSpansByDoc[0]
                                 + "\n at doc " + subSpansByDoc[i].doc()
                                 + ", but should be at " + maxDoc;
    }
    inSameDoc = true;
    return true;
  }
  
  /** Check whether two Spans in the same document are ordered.
   * @param spans1 
   * @param spans2 
   * @return true iff spans1 starts before spans2
   *              or the spans start at the same position,
   *              and spans1 ends before spans2.
   */
  static final boolean docSpansOrdered(Spans spans1, Spans spans2) {
    assert spans1.doc() == spans2.doc() : "doc1 " + spans1.doc() + " != doc2 " + spans2.doc();
    int start1 = spans1.start();
    int start2 = spans2.start();
    /* Do not call docSpansOrdered(int,int,int,int) to avoid invoking .end() : */
    return (start1 == start2) ? (spans1.end() < spans2.end()) : (start1 < start2);
  }

  /** Like {@link #docSpansOrdered(Spans,Spans)}, but use the spans
   * starts and ends as parameters.
   */
  private static final boolean docSpansOrdered(int start1, int end1, int start2, int end2) {
    return (start1 == start2) ? (end1 < end2) : (start1 < start2);
  }

  /** Order the subSpans within the same document by advancing all later spans
   * after the previous one.
   */
  private boolean stretchToOrder() throws IOException {
    matchDoc = subSpans[0].doc();
    for (int i = 1; inSameDoc && (i < subSpans.length); i++) {
      while (! docSpansOrdered(subSpans[i-1], subSpans[i])) {
        if (! subSpans[i].next()) {
          inSameDoc = false;
          more = false;
          break;
        } else if (matchDoc != subSpans[i].doc()) {
          inSameDoc = false;
          break;
        }
      }
    }
    return inSameDoc;
  }

  /** The subSpans are ordered in the same doc, so there is a possible match.
   * Compute the slop while making the match as short as possible by advancing
   * all subSpans except the last one in reverse order.
   */
  private boolean shrinkToAfterShortestMatch() throws IOException {
    matchStart = subSpans[subSpans.length - 1].start();
    matchEnd = subSpans[subSpans.length - 1].end();
    int matchSlop = 0;
    int lastStart = matchStart;
    int lastEnd = matchEnd;
    for (int i = subSpans.length - 2; i >= 0; i--) {
      Spans prevSpans = subSpans[i];
      int prevStart = prevSpans.start();
      int prevEnd = prevSpans.end();
      while (true) { // Advance prevSpans until after (lastStart, lastEnd)
        if (! prevSpans.next()) {
          inSameDoc = false;
          more = false;
          break; // Check remaining subSpans for final match.
        } else if (matchDoc != prevSpans.doc()) {
          inSameDoc = false; // The last subSpans is not advanced here.
          break; // Check remaining subSpans for last match in this document.
        } else {
          int ppStart = prevSpans.start();
          int ppEnd = prevSpans.end(); // Cannot avoid invoking .end()
          if (! docSpansOrdered(ppStart, ppEnd, lastStart, lastEnd)) {
            break; // Check remaining subSpans.
          } else { // prevSpans still before (lastStart, lastEnd)
            prevStart = ppStart;
            prevEnd = ppEnd;
          }
        }
      }
      assert prevStart <= matchStart;
      if (matchStart > prevEnd) { // Only non overlapping spans add to slop.
        matchSlop += (matchStart - prevEnd);
      }
      /* Do not break on (matchSlop > allowedSlop) here to make sure
       * that subSpans[0] is advanced after the match, if any.
       */
      matchStart = prevStart;
      lastStart = prevStart;
      lastEnd = prevEnd;
    }
    return matchSlop <= allowedSlop; // ordered and allowed slop
  }

  public String toString() {
    return getClass().getName() + "("+query.toString()+")@"+
      (firstTime?"START":(more?(doc()+":"+start()+"-"+end()):"END"));
  }
}

