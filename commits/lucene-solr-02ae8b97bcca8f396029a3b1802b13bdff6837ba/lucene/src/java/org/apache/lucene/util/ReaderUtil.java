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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.io.IOException;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.CompositeIndexReader;
import org.apache.lucene.index.CompositeIndexReader.CompositeReaderContext;
import org.apache.lucene.index.AtomicIndexReader;
import org.apache.lucene.index.AtomicIndexReader.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.ReaderContext;

/**
 * Common util methods for dealing with {@link IndexReader}s.
 *
 * @lucene.internal
 */
public final class ReaderUtil {

  private ReaderUtil() {} // no instance

  public static class Slice {
    public static final Slice[] EMPTY_ARRAY = new Slice[0];
    public final int start;
    public final int length;
    public final int readerIndex;

    public Slice(int start, int length, int readerIndex) {
      this.start = start;
      this.length = length;
      this.readerIndex = readerIndex;
    }

    @Override
    public String toString() {
      return "slice start=" + start + " length=" + length + " readerIndex=" + readerIndex;
    }
  }

  /**
   * Gathers sub-readers from reader into a List.  See
   * {@link Gather} for are more general way to gather
   * whatever you need to, per reader.
   *
   * @lucene.experimental
   * 
   * @param allSubReaders
   * @param reader
   */

  public static void gatherSubReaders(final List<AtomicIndexReader> allSubReaders, IndexReader reader) {
    try {
      new Gather(reader) {
        @Override
        protected void add(int base, AtomicIndexReader r) {
          allSubReaders.add(r);
        }
      }.run();
    } catch (IOException ioe) {
      // won't happen
      throw new RuntimeException(ioe);
    }
  }

  /** Recursively visits all sub-readers of a reader.  You
   *  should subclass this and override the add method to
   *  gather what you need.
   *
   * @lucene.experimental */
  public static abstract class Gather {
    private final IndexReader topReader;

    public Gather(IndexReader r) {
      topReader = r;
    }

    public int run() throws IOException {
      return run(0, topReader);
    }

    public int run(int docBase) throws IOException {
      return run(docBase, topReader);
    }

    private int run(int base, IndexReader reader) throws IOException {
      if (reader instanceof AtomicIndexReader) {
        // atomic reader
        add(base, (AtomicIndexReader) reader);
        base += reader.maxDoc();
      } else {
        assert reader instanceof CompositeIndexReader : "must be a composite reader";
        IndexReader[] subReaders = ((CompositeIndexReader) reader).getSequentialSubReaders();
        for (int i = 0; i < subReaders.length; i++) {
          base = run(base, subReaders[i]);
        }
      }

      return base;
    }

    protected abstract void add(int base, AtomicIndexReader r) throws IOException;
  }

  public static ReaderContext buildReaderContext(IndexReader reader) {
    return new ReaderContextBuilder(reader).build();
  }
  
  public static class ReaderContextBuilder {
    private final IndexReader reader;
    private final AtomicReaderContext[] leaves;
    private int leafOrd = 0;
    private int leafDocBase = 0;
    public ReaderContextBuilder(IndexReader reader) {
      this.reader = reader;
      leaves = new AtomicReaderContext[numLeaves(reader)];
    }
    
    public ReaderContext build() {
      return build(null, reader, 0, 0);
    }
    
    private ReaderContext build(CompositeReaderContext parent, IndexReader reader, int ord, int docBase) {
      if (reader instanceof AtomicIndexReader) {
        AtomicReaderContext atomic = new AtomicReaderContext(parent, (AtomicIndexReader) reader, ord, docBase, leafOrd, leafDocBase);
        leaves[leafOrd++] = atomic;
        leafDocBase += reader.maxDoc();
        return atomic;
      } else {
        CompositeIndexReader cr = (CompositeIndexReader) reader;
        IndexReader[] sequentialSubReaders = cr.getSequentialSubReaders();
        ReaderContext[] children = new ReaderContext[sequentialSubReaders.length];
        final CompositeReaderContext newParent;
        if (parent == null) {
          newParent = new CompositeReaderContext(cr, children, leaves);
        } else {
          newParent = new CompositeReaderContext(parent, cr, ord, docBase, children);
        }
        
        int newDocBase = 0;
        for (int i = 0; i < sequentialSubReaders.length; i++) {
          children[i] = build(newParent, sequentialSubReaders[i], i, newDocBase);
          newDocBase += sequentialSubReaders[i].maxDoc();
        }
        return newParent;
      }
    }
    
    private int numLeaves(IndexReader reader) {
      final int[] numLeaves = new int[1];
      try {
        new Gather(reader) {
          @Override
          protected void add(int base, AtomicIndexReader r) {
            numLeaves[0]++;
          }
        }.run();
      } catch (IOException ioe) {
        // won't happen
        throw new RuntimeException(ioe);
      }
      return numLeaves[0];
    }
    
  }

  /**
   * Returns the context's leaves or the context itself as the only element of
   * the returned array. If the context's #leaves() method returns
   * <code>null</code> the given context must be an instance of
   * {@link AtomicReaderContext}
   */
  public static AtomicReaderContext[] leaves(ReaderContext context) {
    assert context != null && context.isTopLevel : "context must be non-null & top-level";
    final AtomicReaderContext[] leaves = context.leaves();
    if (leaves == null) {
      return new AtomicReaderContext[] { (AtomicReaderContext) context };
    }
    return leaves;
  }
  
  /**
   * Walks up the reader tree and return the given context's top level reader
   * context, or in other words the reader tree's root context.
   */
  public static ReaderContext getTopLevelContext(ReaderContext context) {
    while (context.parent != null) {
      context = context.parent;
    }
    return context;
  }

  /**
   * Returns index of the searcher/reader for document <code>n</code> in the
   * array used to construct this searcher/reader.
   */
  public static int subIndex(int n, int[] docStarts) { // find
    // searcher/reader for doc n:
    int size = docStarts.length;
    int lo = 0; // search starts array
    int hi = size - 1; // for first element less than n, return its index
    while (hi >= lo) {
      int mid = (lo + hi) >>> 1;
      int midValue = docStarts[mid];
      if (n < midValue)
        hi = mid - 1;
      else if (n > midValue)
        lo = mid + 1;
      else { // found a match
        while (mid + 1 < size && docStarts[mid + 1] == midValue) {
          mid++; // scan to last match
        }
        return mid;
      }
    }
    return hi;
  }
  
  /**
   * Returns index of the searcher/reader for document <code>n</code> in the
   * array used to construct this searcher/reader.
   */
  public static int subIndex(int n, AtomicReaderContext[] leaves) { // find
    // searcher/reader for doc n:
    int size = leaves.length;
    int lo = 0; // search starts array
    int hi = size - 1; // for first element less than n, return its index
    while (hi >= lo) {
      int mid = (lo + hi) >>> 1;
      int midValue = leaves[mid].docBase;
      if (n < midValue)
        hi = mid - 1;
      else if (n > midValue)
        lo = mid + 1;
      else { // found a match
        while (mid + 1 < size && leaves[mid + 1].docBase == midValue) {
          mid++; // scan to last match
        }
        return mid;
      }
    }
    return hi;
  }

  public static Collection<String> getIndexedFields(IndexReader reader) {
    final Collection<String> fields = new HashSet<String>();
    for(FieldInfo fieldInfo : getMergedFieldInfos(reader)) {
      if (fieldInfo.isIndexed) {
        fields.add(fieldInfo.name);
      }
    }
    return fields;
  }

  /** Call this to get the (merged) FieldInfos for a
   *  composite reader */
  public static FieldInfos getMergedFieldInfos(IndexReader reader) {
    final List<AtomicIndexReader> subReaders = new ArrayList<AtomicIndexReader>();
    ReaderUtil.gatherSubReaders(subReaders, reader);
    final FieldInfos fieldInfos = new FieldInfos();
    for(AtomicIndexReader subReader : subReaders) {
      fieldInfos.add(subReader.getFieldInfos());
    }
    return fieldInfos;
  }
}
