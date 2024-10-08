/**
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
 */

package org.apache.flink.streaming.api.streamrecord;

import java.io.IOException;
import java.io.Serializable;

import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.typeutils.runtime.TupleSerializer;
import org.apache.flink.core.io.IOReadableWritable;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.runtime.plugable.DeserializationDelegate;
import org.apache.flink.runtime.plugable.SerializationDelegate;

/**
 * Object for wrapping a tuple with ID used for sending records between
 * streaming task in Apache Flink stream processing.
 */
public class StreamRecord<T extends Tuple> implements IOReadableWritable, Serializable {
	private static final long serialVersionUID = 1L;

	protected UID uid;
	private T tuple;

	protected SerializationDelegate<T> serializationDelegate;
	protected DeserializationDelegate<T> deserializationDelegate;
	protected TupleSerializer<T> tupleSerializer;

	public StreamRecord() {

	}

	public void setSeralizationDelegate(SerializationDelegate<T> serializationDelegate) {
		this.serializationDelegate = serializationDelegate;
	}

	public void setDeseralizationDelegate(DeserializationDelegate<T> deserializationDelegate,
			TupleSerializer<T> tupleSerializer) {
		this.deserializationDelegate = deserializationDelegate;
		this.tupleSerializer = tupleSerializer;
	}

	/**
	 * @return The ID of the object
	 */
	public UID getId() {
		return uid;
	}

	/**
	 * Creates a new ID for the StreamRecord using the given channelID
	 * 
	 * @param channelID
	 *            ID of the emitting task
	 * @return The StreamRecord object
	 */
	public StreamRecord<T> setId(int channelID) {
		uid = new UID(channelID);
		return this;
	}

	/**
	 * 
	 * @return The tuple contained
	 */
	public T getTuple() {
		return tuple;
	}

	/**
	 * Sets the tuple stored
	 * 
	 * @param tuple
	 *            Value to set
	 * @return Returns the StreamRecord object
	 */
	public StreamRecord<T> setTuple(T tuple) {
		this.tuple = tuple;
		return this;
	}

	@Override
	public void read(DataInputView in) throws IOException {
		uid = new UID();
		uid.read(in);
		deserializationDelegate.setInstance(tupleSerializer.createInstance());
		deserializationDelegate.read(in);
		tuple = deserializationDelegate.getInstance();
	}

	@Override
	public void write(DataOutputView out) throws IOException {
		uid.write(out);
		serializationDelegate.setInstance(tuple);
		serializationDelegate.write(out);
	}

	@Override
	public String toString() {
		return tuple.toString();
	}

}
