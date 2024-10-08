package org.apache.lucene.codecs.simpletext;

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

import java.util.Set;

import org.apache.lucene.codecs.SegmentInfoFormat;
import org.apache.lucene.codecs.SegmentInfoReader;
import org.apache.lucene.codecs.SegmentInfoWriter;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;

// nocommit rename (remove s)

/**
 * plain text segments file format.
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public class SimpleTextSegmentInfosFormat extends SegmentInfoFormat {
  private final SegmentInfoReader reader = new SimpleTextSegmentInfosReader();
  private final SegmentInfoWriter writer = new SimpleTextSegmentInfosWriter();

  public static final String SI_EXTENSION = "si";
  
  @Override
  public SegmentInfoReader getSegmentInfosReader() {
    return reader;
  }

  @Override
  public SegmentInfoWriter getSegmentInfosWriter() {
    return writer;
  }
}
