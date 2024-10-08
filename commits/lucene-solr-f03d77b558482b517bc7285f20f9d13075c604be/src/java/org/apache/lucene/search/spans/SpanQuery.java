package org.apache.lucene.search.spans;

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
import java.util.Collection;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWeight;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Weight;

/** Base class for span-based queries. */
public abstract class SpanQuery extends Query {
  /** Expert: Returns the matches for this query in an index.  Used internally
   * to search for spans. */
  public abstract Spans getSpans(IndexReader reader) throws IOException;

  /**
   * Returns the matches for this query in an index, including access to any {@link org.apache.lucene.index.Payload}s at those
   * positions.  Implementing classes that want access to the payloads will need to implement this.
   * @param reader  The {@link org.apache.lucene.index.IndexReader} to use to get spans/payloads
   * @return null
   * @throws IOException if there is an error accessing the payload
   *
   * <font color="#FF0000">
   * WARNING: The status of the <b>Payloads</b> feature is experimental.
   * The APIs introduced here might change in the future and will not be
   * supported anymore in such a case.</font>
   */
  public PayloadSpans getPayloadSpans(IndexReader reader) throws IOException{
    return null;
  }

  /** Returns the name of the field matched by this query.*/
  public abstract String getField();

  /** Returns a collection of all terms matched by this query.
   * @deprecated use extractTerms instead
   * @see Query#extractTerms(Set)
   */
  public abstract Collection getTerms();

  /** @deprecated delete in 3.0. */
  protected Weight createWeight(Searcher searcher) throws IOException {
    return createQueryWeight(searcher);
  }
  
  public QueryWeight createQueryWeight(Searcher searcher) throws IOException {
    return new SpanWeight(this, searcher);
  }

}
