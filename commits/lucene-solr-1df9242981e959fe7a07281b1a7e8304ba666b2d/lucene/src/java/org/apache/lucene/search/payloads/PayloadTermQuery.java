package org.apache.lucene.search.payloads;

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

import org.apache.lucene.index.IndexReader.ReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.ComplexExplanation;
import org.apache.lucene.search.spans.TermSpans;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.SpanScorer;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

/**
 * This class is very similar to
 * {@link org.apache.lucene.search.spans.SpanTermQuery} except that it factors
 * in the value of the payload located at each of the positions where the
 * {@link org.apache.lucene.index.Term} occurs.
 * <p>
 * In order to take advantage of this, you must override
 * {@link org.apache.lucene.search.Similarity#scorePayload(int, String, int, int, byte[],int,int)}
 * which returns 1 by default.
 * <p>
 * Payload scores are aggregated using a pluggable {@link PayloadFunction}.
 **/
public class PayloadTermQuery extends SpanTermQuery {
  protected PayloadFunction function;
  private boolean includeSpanScore;

  public PayloadTermQuery(Term term, PayloadFunction function) {
    this(term, function, true);
  }

  public PayloadTermQuery(Term term, PayloadFunction function,
      boolean includeSpanScore) {
    super(term);
    this.function = function;
    this.includeSpanScore = includeSpanScore;
  }

  @Override
  public Weight createWeight(IndexSearcher searcher) throws IOException {
    return new PayloadTermWeight(this, searcher);
  }

  protected class PayloadTermWeight extends SpanWeight {

    public PayloadTermWeight(PayloadTermQuery query, IndexSearcher searcher)
        throws IOException {
      super(query, searcher);
    }

    @Override
    public Scorer scorer(ReaderContext context, boolean scoreDocsInOrder,
        boolean topScorer) throws IOException {
      return new PayloadTermSpanScorer((TermSpans) query.getSpans(context.reader),
          this, similarity, context.reader.norms(query.getField()));
    }

    protected class PayloadTermSpanScorer extends SpanScorer {
      protected BytesRef payload;
      protected float payloadScore;
      protected int payloadsSeen;
      private final TermSpans termSpans;

      public PayloadTermSpanScorer(TermSpans spans, Weight weight,
          Similarity similarity, byte[] norms) throws IOException {
        super(spans, weight, similarity, norms);
        termSpans = spans;
      }

      @Override
      protected boolean setFreqCurrentDoc() throws IOException {
        if (!more) {
          return false;
        }
        doc = spans.doc();
        freq = 0.0f;
        payloadScore = 0;
        payloadsSeen = 0;
        Similarity similarity1 = getSimilarity();
        while (more && doc == spans.doc()) {
          int matchLength = spans.end() - spans.start();

          freq += similarity1.sloppyFreq(matchLength);
          processPayload(similarity1);

          more = spans.next();// this moves positions to the next match in this
                              // document
        }
        return more || (freq != 0);
      }

      protected void processPayload(Similarity similarity) throws IOException {
        final DocsAndPositionsEnum postings = termSpans.getPostings();
        if (postings.hasPayload()) {
          payload = postings.getPayload();
          if (payload != null) {
            payloadScore = function.currentScore(doc, term.field(),
                                                 spans.start(), spans.end(), payloadsSeen, payloadScore,
                                                 similarity.scorePayload(doc, term.field(), spans.start(),
                                                                         spans.end(), payload.bytes,
                                                                         payload.offset,
                                                                         payload.length));
          } else {
            payloadScore = function.currentScore(doc, term.field(),
                                                 spans.start(), spans.end(), payloadsSeen, payloadScore,
                                                 similarity.scorePayload(doc, term.field(), spans.start(),
                                                                         spans.end(), null,
                                                                         0,
                                                                         0));
          }
          payloadsSeen++;

        } else {
          // zero out the payload?
        }
      }

      /**
       * 
       * @return {@link #getSpanScore()} * {@link #getPayloadScore()}
       * @throws IOException
       */
      @Override
      public float score() throws IOException {

        return includeSpanScore ? getSpanScore() * getPayloadScore()
            : getPayloadScore();
      }

      /**
       * Returns the SpanScorer score only.
       * <p/>
       * Should not be overridden without good cause!
       * 
       * @return the score for just the Span part w/o the payload
       * @throws IOException
       * 
       * @see #score()
       */
      protected float getSpanScore() throws IOException {
        return super.score();
      }

      /**
       * The score for the payload
       * 
       * @return The score, as calculated by
       *         {@link PayloadFunction#docScore(int, String, int, float)}
       */
      protected float getPayloadScore() {
        return function.docScore(doc, term.field(), payloadsSeen, payloadScore);
      }

      @Override
      protected Explanation explain(final int doc) throws IOException {
        ComplexExplanation result = new ComplexExplanation();
        Explanation nonPayloadExpl = super.explain(doc);
        result.addDetail(nonPayloadExpl);
        // QUESTION: Is there a way to avoid this skipTo call? We need to know
        // whether to load the payload or not
        Explanation payloadBoost = new Explanation();
        result.addDetail(payloadBoost);

        float payloadScore = getPayloadScore();
        payloadBoost.setValue(payloadScore);
        // GSI: I suppose we could toString the payload, but I don't think that
        // would be a good idea
        payloadBoost.setDescription("scorePayload(...)");
        result.setValue(nonPayloadExpl.getValue() * payloadScore);
        result.setDescription("btq, product of:");
        result.setMatch(nonPayloadExpl.getValue() == 0 ? Boolean.FALSE
            : Boolean.TRUE); // LUCENE-1303
        return result;
      }

    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((function == null) ? 0 : function.hashCode());
    result = prime * result + (includeSpanScore ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    PayloadTermQuery other = (PayloadTermQuery) obj;
    if (function == null) {
      if (other.function != null)
        return false;
    } else if (!function.equals(other.function))
      return false;
    if (includeSpanScore != other.includeSpanScore)
      return false;
    return true;
  }

}
