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

import java.io.IOException;

import java.util.HashSet;
import java.util.List;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockObtainFailedException;

/**
 * IndexReader implementation that has access to a Directory. 
 * Instances that have a SegmentInfos object (i. e. segmentInfos != null)
 * "own" the directory, which means that they try to acquire a write lock
 * whenever index modifications are performed.
 */
abstract class DirectoryIndexReader extends IndexReader {
  protected Directory directory;
  protected boolean closeDirectory;
  private IndexDeletionPolicy deletionPolicy;

  private SegmentInfos segmentInfos;
  private Lock writeLock;
  private boolean stale;
  private HashSet synced = new HashSet();

  /** Used by commit() to record pre-commit state in case
   * rollback is necessary */
  private boolean rollbackHasChanges;
  private SegmentInfos rollbackSegmentInfos;

  
  void init(Directory directory, SegmentInfos segmentInfos, boolean closeDirectory)
    throws IOException {
    this.directory = directory;
    this.segmentInfos = segmentInfos;
    this.closeDirectory = closeDirectory;

    if (segmentInfos != null) {
      // We assume that this segments_N was previously
      // properly sync'd:
      for(int i=0;i<segmentInfos.size();i++) {
        final SegmentInfo info = segmentInfos.info(i);
        List files = info.files();
        for(int j=0;j<files.size();j++)
          synced.add(files.get(j));
      }
    }
  }
  
  protected DirectoryIndexReader() {}
  
  DirectoryIndexReader(Directory directory, SegmentInfos segmentInfos,
      boolean closeDirectory) throws IOException {
    super();
    init(directory, segmentInfos, closeDirectory);
  }
  
  static DirectoryIndexReader open(final Directory directory, final boolean closeDirectory, final IndexDeletionPolicy deletionPolicy) throws CorruptIndexException, IOException {

    return (DirectoryIndexReader) new SegmentInfos.FindSegmentsFile(directory) {

      protected Object doBody(String segmentFileName) throws CorruptIndexException, IOException {

        SegmentInfos infos = new SegmentInfos();
        infos.read(directory, segmentFileName);

        DirectoryIndexReader reader;

        if (infos.size() == 1) {          // index is optimized
          reader = SegmentReader.get(infos, infos.info(0), closeDirectory);
        } else {
          reader = new MultiSegmentReader(directory, infos, closeDirectory);
        }
        reader.setDeletionPolicy(deletionPolicy);
        return reader;
      }
    }.run();
  }

  
  public final synchronized IndexReader reopen() throws CorruptIndexException, IOException {
    ensureOpen();

    if (this.hasChanges || this.isCurrent()) {
      // the index hasn't changed - nothing to do here
      return this;
    }

    return (DirectoryIndexReader) new SegmentInfos.FindSegmentsFile(directory) {

      protected Object doBody(String segmentFileName) throws CorruptIndexException, IOException {
        SegmentInfos infos = new SegmentInfos();
        infos.read(directory, segmentFileName);

        DirectoryIndexReader newReader = doReopen(infos);
        
        if (DirectoryIndexReader.this != newReader) {
          newReader.init(directory, infos, closeDirectory);
          newReader.deletionPolicy = deletionPolicy;
        }

        return newReader;
      }
    }.run();
  }

  /**
   * Re-opens the index using the passed-in SegmentInfos 
   */
  protected abstract DirectoryIndexReader doReopen(SegmentInfos infos) throws CorruptIndexException, IOException;
  
  public void setDeletionPolicy(IndexDeletionPolicy deletionPolicy) {
    this.deletionPolicy = deletionPolicy;
  }
  
  /** Returns the directory this index resides in.
   */
  public Directory directory() {
    ensureOpen();
    return directory;
  }

  /**
   * Version number when this IndexReader was opened.
   */
  public long getVersion() {
    ensureOpen();
    return segmentInfos.getVersion();
  }

  /**
   * Check whether this IndexReader is still using the
   * current (i.e., most recently committed) version of the
   * index.  If a writer has committed any changes to the
   * index since this reader was opened, this will return
   * <code>false</code>, in which case you must open a new
   * IndexReader in order to see the changes.  See the
   * description of the <a href="IndexWriter.html#autoCommit"><code>autoCommit</code></a>
   * flag which controls when the {@link IndexWriter}
   * actually commits changes to the index.
   * 
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public boolean isCurrent() throws CorruptIndexException, IOException {
    ensureOpen();
    return SegmentInfos.readCurrentVersion(directory) == segmentInfos.getVersion();
  }

  /**
   * Checks is the index is optimized (if it has a single segment and no deletions)
   * @return <code>true</code> if the index is optimized; <code>false</code> otherwise
   */
  public boolean isOptimized() {
    ensureOpen();
    return segmentInfos.size() == 1 && hasDeletions() == false;
  }

  protected void doClose() throws IOException {
    if(closeDirectory)
      directory.close();
  }
  
  /**
   * Commit changes resulting from delete, undeleteAll, or
   * setNorm operations
   *
   * If an exception is hit, then either no changes or all
   * changes will have been committed to the index
   * (transactional semantics).
   * @throws IOException if there is a low-level IO error
   */
  protected void doCommit() throws IOException {
    if(hasChanges){
      if (segmentInfos != null) {

        // Default deleter (for backwards compatibility) is
        // KeepOnlyLastCommitDeleter:
        IndexFileDeleter deleter =  new IndexFileDeleter(directory,
                                                         deletionPolicy == null ? new KeepOnlyLastCommitDeletionPolicy() : deletionPolicy,
                                                         segmentInfos, null, null);

        // Checkpoint the state we are about to change, in
        // case we have to roll back:
        startCommit();

        boolean success = false;
        try {
          commitChanges();

          // Sync all files we just wrote
          for(int i=0;i<segmentInfos.size();i++) {
            final SegmentInfo info = segmentInfos.info(i);
            final List files = info.files();
            for(int j=0;j<files.size();j++) {
              final String fileName = (String) files.get(j);
              if (!synced.contains(fileName)) {
                assert directory.fileExists(fileName);
                directory.sync(fileName);
                synced.add(fileName);
              }
            }
          }

          segmentInfos.commit(directory);
          success = true;
        } finally {

          if (!success) {

            // Rollback changes that were made to
            // SegmentInfos but failed to get [fully]
            // committed.  This way this reader instance
            // remains consistent (matched to what's
            // actually in the index):
            rollbackCommit();

            // Recompute deletable files & remove them (so
            // partially written .del files, etc, are
            // removed):
            deleter.refresh();
          }
        }

        // Have the deleter remove any now unreferenced
        // files due to this commit:
        deleter.checkpoint(segmentInfos, true);

        if (writeLock != null) {
          writeLock.release();  // release write lock
          writeLock = null;
        }
      }
      else
        commitChanges();
    }
    hasChanges = false;
  }
  
  protected abstract void commitChanges() throws IOException;
  
  /**
   * Tries to acquire the WriteLock on this directory.
   * this method is only valid if this IndexReader is directory owner.
   * 
   * @throws StaleReaderException if the index has changed
   * since this reader was opened
   * @throws CorruptIndexException if the index is corrupt
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws IOException if there is a low-level IO error
   */
  protected void acquireWriteLock() throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    if (segmentInfos != null) {
      ensureOpen();
      if (stale)
        throw new StaleReaderException("IndexReader out of date and no longer valid for delete, undelete, or setNorm operations");
  
      if (writeLock == null) {
        Lock writeLock = directory.makeLock(IndexWriter.WRITE_LOCK_NAME);
        if (!writeLock.obtain(IndexWriter.WRITE_LOCK_TIMEOUT)) // obtain write lock
          throw new LockObtainFailedException("Index locked for write: " + writeLock);
        this.writeLock = writeLock;
  
        // we have to check whether index has changed since this reader was opened.
        // if so, this reader is no longer valid for deletion
        if (SegmentInfos.readCurrentVersion(directory) > segmentInfos.getVersion()) {
          stale = true;
          this.writeLock.release();
          this.writeLock = null;
          throw new StaleReaderException("IndexReader out of date and no longer valid for delete, undelete, or setNorm operations");
        }
      }
    }
  }

  /**
   * Should internally checkpoint state that will change
   * during commit so that we can rollback if necessary.
   */
  void startCommit() {
    if (segmentInfos != null) {
      rollbackSegmentInfos = (SegmentInfos) segmentInfos.clone();
    }
    rollbackHasChanges = hasChanges;
  }

  /**
   * Rolls back state to just before the commit (this is
   * called by commit() if there is some exception while
   * committing).
   */
  void rollbackCommit() {
    if (segmentInfos != null) {
      for(int i=0;i<segmentInfos.size();i++) {
        // Rollback each segmentInfo.  Because the
        // SegmentReader holds a reference to the
        // SegmentInfo we can't [easily] just replace
        // segmentInfos, so we reset it in place instead:
        segmentInfos.info(i).reset(rollbackSegmentInfos.info(i));
      }
      rollbackSegmentInfos = null;
    }

    hasChanges = rollbackHasChanges;
  }

  /** Release the write lock, if needed. */
  protected void finalize() throws Throwable {
    try {
      if (writeLock != null) {
        writeLock.release();                        // release write lock
        writeLock = null;
      }
    } finally {
      super.finalize();
    }
  }

}
