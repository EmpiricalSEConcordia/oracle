package org.apache.lucene.index;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.index.DocumentsWriterPerThreadPool.ThreadState;

/**
 * Default {@link FlushPolicy} implementation that flushes based on RAM used,
 * document count and number of buffered deletes depending on the IndexWriter's
 * {@link IndexWriterConfig}.
 * 
 * <ul>
 * <li>{@link #onDelete(DocumentsWriterFlushControl, ThreadState)} - flushes
 * based on the global number of buffered delete terms iff
 * {@link IndexWriterConfig#getMaxBufferedDeleteTerms()} is enabled</li>
 * <li>{@link #onInsert(DocumentsWriterFlushControl, ThreadState)} - flushes
 * either on the number of documents per {@link DocumentsWriterPerThread} (
 * {@link DocumentsWriterPerThread#getNumDocsInRAM()}) or on the global active
 * memory consumption in the current indexing session iff
 * {@link IndexWriterConfig#getMaxBufferedDocs()} or
 * {@link IndexWriterConfig#getRAMBufferSizeMB()} is enabled respectively</li>
 * <li>{@link #onUpdate(DocumentsWriterFlushControl, ThreadState)} - calls
 * {@link #onInsert(DocumentsWriterFlushControl, ThreadState)} and
 * {@link #onDelete(DocumentsWriterFlushControl, ThreadState)} in order</li>
 * </ul>
 * All {@link IndexWriterConfig} settings are used to mark
 * {@link DocumentsWriterPerThread} as flush pending during indexing with
 * respect to their live updates.
 * <p>
 * If {@link IndexWriterConfig#setRAMBufferSizeMB(double)} is enabled, the
 * largest ram consuming {@link DocumentsWriterPerThread} will be marked as
 * pending iff the global active RAM consumption is >= the configured max RAM
 * buffer.
 */
public class FlushByRamOrCountsPolicy extends FlushPolicy {

  @Override
  public void onDelete(DocumentsWriterFlushControl control, ThreadState state) {
    if (flushOnDeleteTerms()) {
      // Flush this state by num del terms
      final int maxBufferedDeleteTerms = indexWriterConfig
          .getMaxBufferedDeleteTerms();
      if (control.getNumGlobalTermDeletes() >= maxBufferedDeleteTerms) {
        control.setApplyAllDeletes();
      }
    }
    final DocumentsWriter writer = this.writer.get();
    // If deletes alone are consuming > 1/2 our RAM
    // buffer, force them all to apply now. This is to
    // prevent too-frequent flushing of a long tail of
    // tiny segments:
    if ((flushOnRAM() &&
        writer.deleteQueue.bytesUsed() > (1024*1024*indexWriterConfig.getRAMBufferSizeMB()/2))) {
      control.setApplyAllDeletes();
     if (writer.infoStream != null) {
       writer.message("force apply deletes bytesUsed=" +  writer.deleteQueue.bytesUsed() + " vs ramBuffer=" + (1024*1024*indexWriterConfig.getRAMBufferSizeMB()));
     }
   }
  }

  @Override
  public void onInsert(DocumentsWriterFlushControl control, ThreadState state) {
    if (flushOnDocCount()
        && state.perThread.getNumDocsInRAM() >= indexWriterConfig
            .getMaxBufferedDocs()) {
      // Flush this state by num docs
      control.setFlushPending(state);
    } else if (flushOnRAM()) {// flush by RAM
      final long limit = (long) (indexWriterConfig.getRAMBufferSizeMB() * 1024.d * 1024.d);
      final long totalRam = control.activeBytes();
      if (totalRam >= limit) {
        markLargestWriterPending(control, state, totalRam);
      }
    }
  }
  
  /**
   * Marks the most ram consuming active {@link DocumentsWriterPerThread} flush
   * pending
   */
  protected void markLargestWriterPending(DocumentsWriterFlushControl control,
      ThreadState perThreadState, final long currentBytesPerThread) {
    control
        .setFlushPending(findLargestNonPendingWriter(control, perThreadState));
  }
  
  /**
   * Returns <code>true</code> if this {@link FlushPolicy} flushes on
   * {@link IndexWriterConfig#getMaxBufferedDocs()}, otherwise
   * <code>false</code>.
   */
  protected boolean flushOnDocCount() {
    return indexWriterConfig.getMaxBufferedDocs() != IndexWriterConfig.DISABLE_AUTO_FLUSH;
  }

  /**
   * Returns <code>true</code> if this {@link FlushPolicy} flushes on
   * {@link IndexWriterConfig#getMaxBufferedDeleteTerms()}, otherwise
   * <code>false</code>.
   */
  protected boolean flushOnDeleteTerms() {
    return indexWriterConfig.getMaxBufferedDeleteTerms() != IndexWriterConfig.DISABLE_AUTO_FLUSH;
  }

  /**
   * Returns <code>true</code> if this {@link FlushPolicy} flushes on
   * {@link IndexWriterConfig#getRAMBufferSizeMB()}, otherwise
   * <code>false</code>.
   */
  protected boolean flushOnRAM() {
    return indexWriterConfig.getRAMBufferSizeMB() != IndexWriterConfig.DISABLE_AUTO_FLUSH;
  }
}
