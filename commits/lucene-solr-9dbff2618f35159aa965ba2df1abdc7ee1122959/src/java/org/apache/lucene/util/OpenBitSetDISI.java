package org.apache.lucene.util;

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
import org.apache.lucene.search.DocIdSetIterator;
 
public class OpenBitSetDISI extends OpenBitSet {

  /** Construct an OpenBitSetDISI with its bits set
   * from the doc ids of the given DocIdSetIterator.
   * Also give a maximum size one larger than the largest doc id for which a
   * bit may ever be set on this OpenBitSetDISI.
   */
  public OpenBitSetDISI(DocIdSetIterator disi, int maxSize) throws IOException {
    super(maxSize);
    inPlaceOr(disi);
  }

  /** Construct an OpenBitSetDISI with no bits set, and a given maximum size
   * one larger than the largest doc id for which a bit may ever be set
   * on this OpenBitSetDISI.
   */
  public OpenBitSetDISI(int maxSize) {
    super(maxSize);
  }

  /**
   * Perform an inplace OR with the doc ids from a given DocIdSetIterator,
   * setting the bit for each such doc id.
   * These doc ids should be smaller than the maximum size passed to the
   * constructor.
   */   
  public void inPlaceOr(DocIdSetIterator disi) throws IOException {
    while (disi.next() && (disi.doc() < size())) {
      fastSet(disi.doc());
    }
  }

  /**
   * Perform an inplace AND with the doc ids from a given DocIdSetIterator,
   * leaving only the bits set for which the doc ids are in common.
   * These doc ids should be smaller than the maximum size passed to the
   * constructor.
   */   
  public void inPlaceAnd(DocIdSetIterator disi) throws IOException {
    int bitSetDoc = nextSetBit(0);
    while ((bitSetDoc != -1) && disi.skipTo(bitSetDoc)) {
      int disiDoc = disi.doc();
      clear(bitSetDoc, disiDoc);
      bitSetDoc = nextSetBit(disiDoc + 1);
    }
    if (bitSetDoc != -1) {
      clear(bitSetDoc, size());
    }
  }

  /**
   * Perform an inplace NOT with the doc ids from a given DocIdSetIterator,
   * clearing all the bits for each such doc id.
   * These doc ids should be smaller than the maximum size passed to the
   * constructor.
   */   
  public void inPlaceNot(DocIdSetIterator disi) throws IOException {
    while (disi.next() && (disi.doc() < size())) {
      fastClear(disi.doc());
    }
  }

  /**
   * Perform an inplace XOR with the doc ids from a given DocIdSetIterator,
   * flipping all the bits for each such doc id.
   * These doc ids should be smaller than the maximum size passed to the
   * constructor.
   */   
  public void inPlaceXor(DocIdSetIterator disi) throws IOException {
    while (disi.next() && (disi.doc() < size())) {
      fastFlip(disi.doc());
    }
  }
}
