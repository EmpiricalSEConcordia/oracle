package org.apache.lucene.index;

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

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BitVector;

/**
 * @lucene.experimental
 */
public class SegmentWriteState {
  public final PrintStream infoStream;
  public final Directory directory;
  public final String segmentName;
  public final FieldInfos fieldInfos;
  public final int numDocs;
  public boolean hasVectors;
  public final Collection<String> flushedFiles;
  public final AtomicLong bytesUsed;

  // Deletes to apply while we are flushing the segment.  A
  // Term is enrolled in here if it was deleted at one
  // point, and it's mapped to the docIDUpto, meaning any
  // docID < docIDUpto containing this term should be
  // deleted.
  public final BufferedDeletes segDeletes;

  // Lazily created:
  public BitVector deletedDocs;

  final SegmentCodecs segmentCodecs;
  public final String codecId;

  /** Expert: The fraction of terms in the "dictionary" which should be stored
   * in RAM.  Smaller values use more memory, but make searching slightly
   * faster, while larger values use less memory and make searching slightly
   * slower.  Searching is typically not dominated by dictionary lookup, so
   * tweaking this is rarely useful.*/
  public int termIndexInterval;                   // TODO: this should be private to the codec, not settable here or in IWC

  /** Expert: The fraction of TermDocs entries stored in skip tables,
   * used to accelerate {@link DocsEnum#advance(int)}.  Larger values result in
   * smaller indexes, greater acceleration, but fewer accelerable cases, while
   * smaller values result in bigger indexes, less acceleration and more
   * accelerable cases. More detailed experiments would be useful here. */
  public final int skipInterval = 16;
  
  /** Expert: The maximum number of skip levels. Smaller values result in 
   * slightly smaller indexes, but slower skipping in big posting lists.
   */
  public final int maxSkipLevels = 10;
  


  public SegmentWriteState(PrintStream infoStream, Directory directory, String segmentName, FieldInfos fieldInfos,
      int numDocs, int termIndexInterval, SegmentCodecs segmentCodecs, BufferedDeletes segDeletes, AtomicLong bytesUsed) {
    this.infoStream = infoStream;
    this.segDeletes = segDeletes;
    this.directory = directory;
    this.segmentName = segmentName;
    this.fieldInfos = fieldInfos;
    this.numDocs = numDocs;
    this.termIndexInterval = termIndexInterval;
    this.segmentCodecs = segmentCodecs;
    flushedFiles = new HashSet<String>();
    codecId = "";
    this.bytesUsed = bytesUsed;
  }
  
  /**
   * Create a shallow {@link SegmentWriteState} copy final a codec ID
   */
  SegmentWriteState(SegmentWriteState state, String codecId) {
    infoStream = state.infoStream;
    directory = state.directory;
    segmentName = state.segmentName;
    fieldInfos = state.fieldInfos;
    numDocs = state.numDocs;
    termIndexInterval = state.termIndexInterval;
    segmentCodecs = state.segmentCodecs;
    flushedFiles = state.flushedFiles;
    this.codecId = codecId;
    segDeletes = state.segDeletes;
    bytesUsed = state.bytesUsed;
  }
}
