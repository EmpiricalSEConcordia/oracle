package org.apache.lucene.index.values;

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
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.values.DocValues.Source;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.CodecUtil;
import org.apache.lucene.util.FloatsRef;

/**
 * Exposes {@link Writer} and reader ({@link Source}) for 32 bit and 64 bit
 * floating point values.
 * <p>
 * Current implementations store either 4 byte or 8 byte floating points with
 * full precision without any compression.
 * 
 * @lucene.experimental
 */
public class Floats {
  // TODO - add bulk copy where possible
  private static final String CODEC_NAME = "SimpleFloats";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;
  private static final int INT_DEFAULT = Float
      .floatToRawIntBits(Float.NEGATIVE_INFINITY);
  private static final long LONG_DEFAULT = Double
      .doubleToRawLongBits(Double.NEGATIVE_INFINITY);

  
  public static Writer getWriter(Directory dir, String id, int precisionBytes,
      AtomicLong bytesUsed) throws IOException {
    if (precisionBytes != 4 && precisionBytes != 8) {
      throw new IllegalArgumentException("precisionBytes must be 4 or 8; got "
          + precisionBytes);
    }
    if (precisionBytes == 4) {
      return new Float4Writer(dir, id, bytesUsed);
    } else {
      return new Float8Writer(dir, id, bytesUsed);
    }
  }

  public static DocValues getValues(Directory dir, String id, int maxDoc)
      throws IOException {
    return new FloatsReader(dir, id, maxDoc);
  }

  abstract static class FloatsWriter extends Writer {
    private final Directory dir;
    private final String id;
    private FloatsRef floatsRef;
    protected int lastDocId = -1;
    protected IndexOutput datOut;
    private final byte precision;

    protected FloatsWriter(Directory dir, String id, int precision,
        AtomicLong bytesUsed) throws IOException {
      super(bytesUsed);
      this.dir = dir;
      this.id = id;
      this.precision = (byte) precision;
      initDatOut();
    }

    private void initDatOut() throws IOException {
      datOut = dir.createOutput(IndexFileNames.segmentFileName(id, "",
          Writer.DATA_EXTENSION));
      CodecUtil.writeHeader(datOut, CODEC_NAME, VERSION_CURRENT);
      assert datOut.getFilePointer() == CodecUtil.headerLength(CODEC_NAME);
      datOut.writeByte(precision);
    }

    public long ramBytesUsed() {
      return 0;
    }

    @Override
    protected void add(int docID) throws IOException {
      add(docID, floatsRef.get());
    }

    @Override
    public void add(int docID, PerDocFieldValues docValues) throws IOException {
      add(docID, docValues.getFloat());
    }

    @Override
    protected void setNextEnum(DocValuesEnum valuesEnum) {
      floatsRef = valuesEnum.getFloat();
    }

    protected abstract int fillDefault(int num) throws IOException;

    @Override
    protected void merge(MergeState state) throws IOException {
      if (state.bits == null && state.reader instanceof FloatsReader) {
        // no deletes - bulk copy
        // TODO: should be do bulks with deletes too?
        final FloatsReader reader = (FloatsReader) state.reader;
        assert reader.precisionBytes == (int) precision;
        if (reader.maxDoc == 0)
          return;
        if (datOut == null)
          initDatOut();
        final int docBase = state.docBase;
        if (docBase - lastDocId > 1) {
          // fill with default values
          lastDocId += fillDefault(docBase - lastDocId - 1);
        }
        lastDocId += reader.transferTo(datOut);
      } else
        super.merge(state);
    }

    @Override
    public void files(Collection<String> files) throws IOException {
      files.add(IndexFileNames.segmentFileName(id, "", Writer.DATA_EXTENSION));
    }

  }

  // Writes 4 bytes (float) per value
  static class Float4Writer extends FloatsWriter {

    protected Float4Writer(Directory dir, String id, AtomicLong bytesUsed)
        throws IOException {
      super(dir, id, 4, bytesUsed);
    }

    @Override
    public void add(final int docID, final double v)
        throws IOException {
      assert docID > lastDocId : "docID: " + docID
          + " must be greater than the last added doc id: " + lastDocId;
      if (docID - lastDocId > 1) {
        // fill with default values
        lastDocId += fillDefault(docID - lastDocId - 1);
      }
      assert datOut != null;
      datOut.writeInt(Float.floatToRawIntBits((float) v));
      ++lastDocId;
    }

    @Override
    public void finish(int docCount) throws IOException {
      try {
        if (docCount > lastDocId + 1)
          for (int i = lastDocId; i < docCount; i++) {
            datOut.writeInt(INT_DEFAULT); // default value
          }
      } finally {
        datOut.close();
      }
    }

    @Override
    protected int fillDefault(int numValues) throws IOException {
      for (int i = 0; i < numValues; i++) {
        datOut.writeInt(INT_DEFAULT);
      }
      return numValues;
    }
  }

  // Writes 8 bytes (double) per value
  static class Float8Writer extends FloatsWriter {

    protected Float8Writer(Directory dir, String id, AtomicLong bytesUsed)
        throws IOException {
      super(dir, id, 8, bytesUsed);
    }

    @Override
    public void add(int docID, double v) throws IOException {
      assert docID > lastDocId : "docID: " + docID
          + " must be greater than the last added doc id: " + lastDocId;
      if (docID - lastDocId > 1) {
        // fill with default values
        lastDocId += fillDefault(docID - lastDocId - 1);
      }
      assert datOut != null;
      datOut.writeLong(Double.doubleToRawLongBits(v));
      ++lastDocId;
    }

    @Override
    public void finish(int docCount) throws IOException {
      try {
        if (docCount > lastDocId + 1)
          for (int i = lastDocId; i < docCount; i++) {
            datOut.writeLong(LONG_DEFAULT); // default value
          }
      } finally {
        datOut.close();
      }
    }

    @Override
    protected int fillDefault(int numValues) throws IOException {
      for (int i = 0; i < numValues; i++) {
        datOut.writeLong(LONG_DEFAULT);
      }
      return numValues;
    }
  }

  /**
   * Opens all necessary files, but does not read any data in until you call
   * {@link #load}.
   */
  static class FloatsReader extends DocValues {

    private final IndexInput datIn;
    private final int precisionBytes;
    // TODO(simonw) is ByteBuffer the way to go here?
    private final int maxDoc;

    protected FloatsReader(Directory dir, String id, int maxDoc)
        throws IOException {
      datIn = dir.openInput(IndexFileNames.segmentFileName(id, "",
          Writer.DATA_EXTENSION));
      CodecUtil.checkHeader(datIn, CODEC_NAME, VERSION_START, VERSION_START);
      precisionBytes = datIn.readByte();
      assert precisionBytes == 4 || precisionBytes == 8;
      this.maxDoc = maxDoc;
    }

    int transferTo(IndexOutput out) throws IOException {
      IndexInput indexInput = (IndexInput) datIn.clone();
      try {
        indexInput.seek(CodecUtil.headerLength(CODEC_NAME));
        // skip precision:
        indexInput.readByte();
        out.copyBytes(indexInput, precisionBytes * maxDoc);
      } finally {
        indexInput.close();
      }
      return maxDoc;
    }

    /**
     * Loads the actual values. You may call this more than once, eg if you
     * already previously loaded but then discarded the Source.
     */
    @Override
    public Source load() throws IOException {
      /*
       *  the allocated byteBuffer always uses BIG_ENDIAN here
       *  and since the writer uses DataOutput#writeInt() / writeLong()
       *  we can allways assume BIGE_ENDIAN
       */
      final ByteBuffer buffer = ByteBuffer.allocate(precisionBytes * maxDoc);
      IndexInput indexInput = (IndexInput) datIn.clone();
      indexInput.seek(CodecUtil.headerLength(CODEC_NAME));
      // skip precision:
      indexInput.readByte();
      assert buffer.hasArray() : "Buffer must support Array";
      final byte[] arr = buffer.array();
      indexInput.readBytes(arr, 0, arr.length);
      return precisionBytes == 4 ? new Source4(buffer) : new Source8(buffer);
    }

    private class Source4 extends Source {
      private final FloatBuffer values;

      Source4(ByteBuffer buffer) {
        values = buffer.asFloatBuffer();
        missingValue.doubleValue = Float.NEGATIVE_INFINITY;
      }

      @Override
      public double getFloat(int docID) {
        return values.get(docID);
      }

      @Override
      public DocValuesEnum getEnum(AttributeSource attrSource)
          throws IOException {
        final MissingValue missing = getMissing();
        return new SourceEnum(attrSource, ValueType.FLOAT_32, this, maxDoc) {
          @Override
          public int advance(int target) throws IOException {
            if (target >= numDocs)
              return pos = NO_MORE_DOCS;
            while (missing.doubleValue == source.getFloat(target)) {
              if (++target >= numDocs) {
                return pos = NO_MORE_DOCS;
              }
            }
            floatsRef.floats[floatsRef.offset] = source.getFloat(target);
            return pos = target;
          }
        };
      }

      @Override
      public ValueType type() {
        return ValueType.FLOAT_32;
      }
    }

    private class Source8 extends Source {
      private final DoubleBuffer values;

      Source8(ByteBuffer buffer) {
        values = buffer.asDoubleBuffer();
        missingValue.doubleValue = Double.NEGATIVE_INFINITY;

      }

      @Override
      public double getFloat(int docID) {
        return values.get(docID);
      }

      @Override
      public DocValuesEnum getEnum(AttributeSource attrSource)
          throws IOException {
        final MissingValue missing = getMissing();
        return new SourceEnum(attrSource, type(), this, maxDoc) {
          @Override
          public int advance(int target) throws IOException {
            if (target >= numDocs)
              return pos = NO_MORE_DOCS;
            while (missing.doubleValue == source.getFloat(target)) {
              if (++target >= numDocs) {
                return pos = NO_MORE_DOCS;
              }
            }
            floatsRef.floats[floatsRef.offset] = source.getFloat(target);
            return pos = target;
          }
        };
      }

      @Override
      public ValueType type() {
        return ValueType.FLOAT_64;
      }
    }

    @Override
    public void close() throws IOException {
      super.close();
      datIn.close();
    }

    @Override
    public DocValuesEnum getEnum(AttributeSource source) throws IOException {
      IndexInput indexInput = (IndexInput) datIn.clone();
      indexInput.seek(CodecUtil.headerLength(CODEC_NAME));
      // skip precision:
      indexInput.readByte();
      return precisionBytes == 4 ? new Floats4Enum(source, indexInput, maxDoc)
          : new Floats8EnumImpl(source, indexInput, maxDoc);
    }

    @Override
    public ValueType type() {
      return precisionBytes == 4 ? ValueType.FLOAT_32
          : ValueType.FLOAT_64;
    }
  }

  static final class Floats4Enum extends FloatsEnumImpl {

    Floats4Enum(AttributeSource source, IndexInput dataIn, int maxDoc)
        throws IOException {
      super(source, dataIn, 4, maxDoc, ValueType.FLOAT_32);
    }

    @Override
    public int advance(int target) throws IOException {
      if (target >= maxDoc)
        return pos = NO_MORE_DOCS;
      dataIn.seek(fp + (target * precision));
      int intBits;
      while ((intBits = dataIn.readInt()) == INT_DEFAULT) {
        if (++target >= maxDoc)
          return pos = NO_MORE_DOCS;
      }
      floatsRef.floats[0] = Float.intBitsToFloat(intBits);
      floatsRef.offset = 0;
      return pos = target;
    }

    @Override
    public int docID() {
      return pos;
    }

    @Override
    public int nextDoc() throws IOException {
      if (pos >= maxDoc) {
        return pos = NO_MORE_DOCS;
      }
      return advance(pos + 1);
    }
  }

  private static final class Floats8EnumImpl extends FloatsEnumImpl {

    Floats8EnumImpl(AttributeSource source, IndexInput dataIn, int maxDoc)
        throws IOException {
      super(source, dataIn, 8, maxDoc, ValueType.FLOAT_64);
    }

    @Override
    public int advance(int target) throws IOException {
      if (target >= maxDoc) {
        return pos = NO_MORE_DOCS;
      }
      dataIn.seek(fp + (target * precision));
      long value;
      while ((value = dataIn.readLong()) == LONG_DEFAULT) {
        if (++target >= maxDoc)
          return pos = NO_MORE_DOCS;
      }
      floatsRef.floats[0] = Double.longBitsToDouble(value);
      floatsRef.offset = 0;
      return pos = target;
    }

    @Override
    public int docID() {
      return pos;
    }

    @Override
    public int nextDoc() throws IOException {
      if (pos >= maxDoc) {
        return pos = NO_MORE_DOCS;
      }
      return advance(pos + 1);
    }
  }

  static abstract class FloatsEnumImpl extends DocValuesEnum {
    protected final IndexInput dataIn;
    protected int pos = -1;
    protected final int precision;
    protected final int maxDoc;
    protected final long fp;

    FloatsEnumImpl(AttributeSource source, IndexInput dataIn, int precision,
        int maxDoc, ValueType type) throws IOException {
      super(source, precision == 4 ? ValueType.FLOAT_32
          : ValueType.FLOAT_64);
      this.dataIn = dataIn;
      this.precision = precision;
      this.maxDoc = maxDoc;
      fp = dataIn.getFilePointer();
      floatsRef.offset = 0;
    }

    @Override
    public void close() throws IOException {
      dataIn.close();
    }
  }
}