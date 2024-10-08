/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.store.memory;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockObtainFailedException;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author kimchy (Shay Banon)
 */
public class SimpleHeapStoreTests {

    @Test public void test1BufferNoCache() throws Exception {
        HeapDirectory dir = new HeapDirectory(new ByteSizeValue(1, ByteSizeUnit.BYTES), new ByteSizeValue(0, ByteSizeUnit.BYTES), false);
        insertData(dir);
        verifyData(dir);
        dir.close();
    }

    @Test public void test1Buffer() throws Exception {
        HeapDirectory dir = new HeapDirectory(new ByteSizeValue(1, ByteSizeUnit.BYTES), new ByteSizeValue(10, ByteSizeUnit.BYTES), false);
        insertData(dir);
        verifyData(dir);
        dir.close();
    }

    @Test public void test3Buffer() throws Exception {
        HeapDirectory dir = new HeapDirectory(new ByteSizeValue(3, ByteSizeUnit.BYTES), new ByteSizeValue(10, ByteSizeUnit.BYTES), false);
        insertData(dir);
        verifyData(dir);
        dir.close();
    }

    @Test public void test10Buffer() throws Exception {
        HeapDirectory dir = new HeapDirectory(new ByteSizeValue(10, ByteSizeUnit.BYTES), new ByteSizeValue(20, ByteSizeUnit.BYTES), false);
        insertData(dir);
        verifyData(dir);
        dir.close();
    }

    @Test public void test15Buffer() throws Exception {
        HeapDirectory dir = new HeapDirectory(new ByteSizeValue(15, ByteSizeUnit.BYTES), new ByteSizeValue(30, ByteSizeUnit.BYTES), false);
        insertData(dir);
        verifyData(dir);
        dir.close();
    }

    @Test public void test40Buffer() throws Exception {
        HeapDirectory dir = new HeapDirectory(new ByteSizeValue(40, ByteSizeUnit.BYTES), new ByteSizeValue(80, ByteSizeUnit.BYTES), false);
        insertData(dir);
        verifyData(dir);
        dir.close();
    }

    @Test public void testSimpeLocking() throws Exception {
        HeapDirectory dir = new HeapDirectory(new ByteSizeValue(40, ByteSizeUnit.BYTES), new ByteSizeValue(80, ByteSizeUnit.BYTES), false);

        Lock lock = dir.makeLock("testlock");

        assertThat(lock.isLocked(), equalTo(false));
        assertThat(lock.obtain(200), equalTo(true));
        assertThat(lock.isLocked(), equalTo(true));
        try {
            assertThat(lock.obtain(200), equalTo(false));
            assertThat("lock should be thrown", false, equalTo(true));
        } catch (LockObtainFailedException e) {
            // all is well
        }
        lock.release();
        assertThat(lock.isLocked(), equalTo(false));
        dir.close();
    }

    private void insertData(HeapDirectory dir) throws IOException {
        byte[] test = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        IndexOutput indexOutput = dir.createOutput("value1");
        indexOutput.writeBytes(new byte[]{2, 4, 6, 7, 8}, 5);
        indexOutput.writeInt(-1);
        indexOutput.writeLong(10);
        indexOutput.writeInt(0);
        indexOutput.writeInt(0);
        indexOutput.writeBytes(test, 8);
        indexOutput.writeBytes(test, 5);

        indexOutput.seek(0);
        indexOutput.writeByte((byte) 8);
        if (dir.bufferSizeInBytes() > 4) {
            indexOutput.seek(2);
            indexOutput.writeBytes(new byte[]{1, 2}, 2);
        }

        indexOutput.close();
    }

    private void verifyData(HeapDirectory dir) throws IOException {
        byte[] test = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        assertThat(dir.fileExists("value1"), equalTo(true));
        assertThat(dir.fileLength("value1"), equalTo(38l));

        IndexInput indexInput = dir.openInput("value1");
        indexInput.readBytes(test, 0, 5);
        assertThat(test[0], equalTo((byte) 8));
        assertThat(indexInput.readInt(), equalTo(-1));
        assertThat(indexInput.readLong(), equalTo((long) 10));
        assertThat(indexInput.readInt(), equalTo(0));
        assertThat(indexInput.readInt(), equalTo(0));
        indexInput.readBytes(test, 0, 8);
        assertThat(test[0], equalTo((byte) 1));
        assertThat(test[7], equalTo((byte) 8));
        indexInput.readBytes(test, 0, 5);
        assertThat(test[0], equalTo((byte) 1));
        assertThat(test[4], equalTo((byte) 5));

        indexInput.seek(28);
        assertThat(indexInput.readByte(), equalTo((byte) 4));
        indexInput.seek(30);
        assertThat(indexInput.readByte(), equalTo((byte) 6));

        indexInput.seek(0);
        indexInput.readBytes(test, 0, 5);
        assertThat(test[0], equalTo((byte) 8));

        indexInput.close();

        indexInput = dir.openInput("value1");
        // iterate over all the data
        for (int i = 0; i < 38; i++) {
            indexInput.readByte();
        }
        indexInput.close();
    }

}