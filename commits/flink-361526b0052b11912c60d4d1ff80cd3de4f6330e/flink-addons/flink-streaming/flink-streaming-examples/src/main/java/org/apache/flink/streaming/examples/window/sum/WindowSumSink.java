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

package org.apache.flink.streaming.examples.window.sum;

import org.apache.flink.streaming.api.function.SinkFunction;

import org.apache.flink.api.java.tuple.Tuple2;

public class WindowSumSink extends SinkFunction<Tuple2<Integer, Long>> {
	private static final long serialVersionUID = 1L;
	@Override
	public void invoke(Tuple2<Integer, Long> inTuple) {
		System.out.println(inTuple);
	}
}
