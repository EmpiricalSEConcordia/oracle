package org.apache.lucene.util.junitcompat;

/*
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

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.store.SingleInstanceLockFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class TestFailIfDirectoryNotClosed extends WithNestedTests {
  public TestFailIfDirectoryNotClosed() {
    super(true);
  }
  
  public static class Nested1 extends WithNestedTests.AbstractNestedTest {
    public void testDummy() {
      Directory dir = newDirectory();
      System.out.println(dir.toString());
    }
  }
  
  public static class Nested2 extends WithNestedTests.AbstractNestedTest {
    public void testDummy() throws IOException {
      MockDirectoryWrapper dir = newMockDirectory();
      IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig(TEST_VERSION_CURRENT, null));
      dir.close();
    }
  }
  
  public static class Nested3 extends WithNestedTests.AbstractNestedTest {
    public void testDummy() throws IOException {
      MockDirectoryWrapper dir = newMockDirectory();
      dir.setLockFactory(new SingleInstanceLockFactory());
      IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig(TEST_VERSION_CURRENT, null));
      dir.close();
    }
  }

  @Test
  public void testFailIfDirectoryNotClosed() {
    Result r = JUnitCore.runClasses(Nested1.class);
    Assert.assertEquals(1, r.getFailureCount());
  }
  
  @Test
  public void testFailIfIndexWriterNotClosed() {
    Result r = JUnitCore.runClasses(Nested2.class);
    Assert.assertEquals(1, r.getFailureCount());
  }
  
  @Test
  public void testFailIfIndexWriterNotClosedChangeLockFactory() {
    Result r = JUnitCore.runClasses(Nested3.class);
    Assert.assertEquals(1, r.getFailureCount());
  }
}
