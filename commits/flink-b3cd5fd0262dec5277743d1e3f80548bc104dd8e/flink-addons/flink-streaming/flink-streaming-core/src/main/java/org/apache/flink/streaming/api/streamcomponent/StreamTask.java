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

package org.apache.flink.streaming.api.streamcomponent;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.runtime.io.network.api.RecordWriter;
import org.apache.flink.runtime.plugable.SerializationDelegate;
import org.apache.flink.streaming.api.invokable.StreamRecordInvokable;
import org.apache.flink.streaming.api.streamrecord.StreamRecord;

public class StreamTask<IN extends Tuple, OUT extends Tuple> extends
		SingleInputAbstractStreamComponent<IN, OUT> {

	private static final Log LOG = LogFactory.getLog(StreamTask.class);

	private List<RecordWriter<SerializationDelegate<StreamRecord<OUT>>>> outputs;
	private StreamRecordInvokable<IN, OUT> userInvokable;
	private static int numTasks;

	public StreamTask() {
		outputs = new LinkedList<RecordWriter<SerializationDelegate<StreamRecord<OUT>>>>();
		userInvokable = null;
		numTasks = newComponent();
		instanceID = numTasks;
	}

	@Override
	public void setInputsOutputs() {
		setConfigInputs();
		setConfigOutputs(outputs);

		inputIter = createInputIterator(inputs, inTupleSerializer);
	}

	@Override
	protected void setInvokable() {
		userInvokable = getInvokable();
		userInvokable.initialize(collector, inputIter, inTupleSerializer, isMutable);
	}

	@Override
	public void invoke() throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug("TASK " + name + " invoked with instance id " + instanceID);
		}

		for (RecordWriter<SerializationDelegate<StreamRecord<OUT>>> output : outputs) {
			output.initializeSerializers();
		}

		userInvokable.invoke();

		if (LOG.isDebugEnabled()) {
			LOG.debug("TASK " + name + " invoke finished with instance id " + instanceID);
		}

		for (RecordWriter<SerializationDelegate<StreamRecord<OUT>>> output : outputs) {
			output.flush();
		}
	}
}