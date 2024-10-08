/***********************************************************************************************************************
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************************************************/

package org.apache.flink.streaming.faulttolerance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.flink.streaming.api.streamrecord.ArrayStreamRecord;
import org.apache.flink.streaming.api.streamrecord.StreamRecord;
import org.apache.flink.streaming.api.streamrecord.UID;
import org.apache.flink.streaming.faulttolerance.ExactlyOnceFaultToleranceBuffer;
import org.junit.Before;
import org.junit.Test;

import org.apache.flink.api.java.tuple.Tuple1;

public class ExactlyOnceBufferTest {

	ExactlyOnceFaultToleranceBuffer buffer;
	int[] numberOfChannels;

	@Before
	public void setUp() throws Exception {
		numberOfChannels = new int[] { 1, 2, 2 };
		buffer = new ExactlyOnceFaultToleranceBuffer(numberOfChannels, 1);
	}

	@Test
	public void testAddToAckCounter() {
		StreamRecord record1 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R1")).setId(1);
		buffer.addToAckCounter(record1.getId());
		assertArrayEquals(new int[] { 0, 1, 2, 2 }, buffer.ackCounter.get(record1.getId()));

		StreamRecord record2 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R2")).setId(1);
		buffer.addToAckCounter(record2.getId(), 1);
		int[] acks = buffer.ackCounter.get(record2.getId());
		assertArrayEquals(new int[] { 2, 0, 2, 0 }, acks);

		StreamRecord record3 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R3")).setId(1);
		buffer.addToAckCounter(record3.getId(), 0);
		acks = buffer.ackCounter.get(record3.getId());
		assertArrayEquals(new int[] { 2, 1, 0, 0 }, acks);
	}

	@Test
	public void testRemoveFromAckCounter() {
		StreamRecord record1 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R1")).setId(1);
		StreamRecord record2 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R2")).setId(1);

		buffer.addToAckCounter(record1.getId());
		buffer.addToAckCounter(record2.getId());
		assertEquals(2, buffer.ackCounter.size());

		buffer.removeFromAckCounter(record2.getId());

		assertEquals(1, buffer.ackCounter.size());
		assertArrayEquals(new int[] { 0, 1, 2, 2 }, buffer.ackCounter.get(record1.getId()));
		assertFalse(buffer.ackCounter.containsKey(record2.getId()));

		StreamRecord record3 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R3")).setId(1);
		StreamRecord record4 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R4")).setId(1);

		buffer.addToAckCounter(record3.getId(), 0);
		buffer.addToAckCounter(record4.getId(), 2);
		assertEquals(3, buffer.ackCounter.size());

		buffer.removeFromAckCounter(record3.getId());
		assertEquals(2, buffer.ackCounter.size());
		assertTrue(buffer.ackCounter.containsKey(record4.getId()));
		assertFalse(buffer.ackCounter.containsKey(record3.getId()));
	}

	@Test
	public void testAck() {
		StreamRecord record1 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R1")).setId(1);
		UID id = record1.getId();
		buffer.add(record1);
		
		assertArrayEquals(new int[] { 0, 1, 2, 2 }, buffer.ackCounter.get(id));
		buffer.ack(id, 0);
		assertArrayEquals(new int[] { 1, 0, 2, 2 }, buffer.ackCounter.get(id));
		buffer.ack(id, 1);
		assertArrayEquals(new int[] { 1, 0, 1, 2 }, buffer.ackCounter.get(id));
		buffer.ack(id, 1);
		assertArrayEquals(new int[] { 2, 0, 0, 2 }, buffer.ackCounter.get(id));
		buffer.ack(id, 2);
		buffer.ack(id, 2);
		assertFalse(buffer.ackCounter.containsKey(id));
	}

	@Test
	public void testFailChannel() {
		StreamRecord record1 = new ArrayStreamRecord(1).setTuple(0, new Tuple1<String>("R1")).setId(1);
		UID id = record1.getId();
		buffer.add(record1);
		
		assertArrayEquals(new int[] { 0, 1, 2, 2 }, buffer.ackCounter.get(id));
		StreamRecord failedRecord = buffer.failChannel(id, 1);
		
		assertArrayEquals(new int[] { 1, 1, 0, 2 }, buffer.ackCounter.get(id));
		assertArrayEquals(new int[] { 2, 0, 2, 0 }, buffer.ackCounter.get(failedRecord.getId()));
		assertEquals(2, buffer.ackCounter.size());
		buffer.ack(id, 1);
		assertArrayEquals(new int[] { 1, 1, -1, 2 }, buffer.ackCounter.get(id));
		assertEquals(2, buffer.ackCounter.size());
		
		assertEquals(null, buffer.failChannel(id, 1));
		assertArrayEquals(new int[] { 1, 1, -1, 2 }, buffer.ackCounter.get(id));
		assertArrayEquals(new int[] { 2, 0, 2, 0 }, buffer.ackCounter.get(failedRecord.getId()));
		assertEquals(2, buffer.ackCounter.size());
		
		buffer.failChannel(failedRecord.getId(), 1);
		assertFalse(buffer.ackCounter.containsKey(failedRecord.getId()));
	}

	@Test
	public void testExactlyOnceFaultToleranceBuffer() {
		// fail("Not yet implemented");
	}

	@Test
	public void testFaultToleranceBuffer() {
		// fail("Not yet implemented");
	}

	@Test
	public void testAdd() {
		// fail("Not yet implemented");
	}

	@Test
	public void testFail() {
		// fail("Not yet implemented");
	}

	@Test
	public void testRemove() {
		// fail("Not yet implemented");
	}

}
