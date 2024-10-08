/*
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
 */

package org.apache.flink.streaming.api.datastream;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.Partitioner;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.io.OutputFormat;
import org.apache.flink.api.common.typeinfo.BasicArrayTypeInfo;
import org.apache.flink.api.common.typeinfo.PrimitiveArrayTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.Utils;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.io.CsvOutputFormat;
import org.apache.flink.api.java.io.TextOutputFormat;
import org.apache.flink.api.java.operators.Keys;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.typeutils.InputTypeConfigurable;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.core.fs.FileSystem.WriteMode;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.collector.selector.OutputSelector;
import org.apache.flink.streaming.api.datastream.temporal.StreamJoinOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.TimestampExtractor;
import org.apache.flink.streaming.api.functions.sink.FileSinkFunctionByMillis;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.sink.SocketClientSink;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.api.operators.StreamFilter;
import org.apache.flink.streaming.api.operators.StreamFlatMap;
import org.apache.flink.streaming.api.operators.StreamMap;
import org.apache.flink.streaming.api.operators.StreamSink;
import org.apache.flink.streaming.api.transformations.OneInputTransformation;
import org.apache.flink.streaming.api.transformations.PartitionTransformation;
import org.apache.flink.streaming.api.transformations.StreamTransformation;
import org.apache.flink.streaming.api.transformations.UnionTransformation;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.SlidingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.WindowAssigner;
import org.apache.flink.streaming.api.windowing.helper.Count;
import org.apache.flink.streaming.api.windowing.helper.Delta;
import org.apache.flink.streaming.api.windowing.helper.FullStream;
import org.apache.flink.streaming.api.windowing.helper.Time;
import org.apache.flink.streaming.api.windowing.helper.WindowingHelper;
import org.apache.flink.streaming.api.windowing.policy.EvictionPolicy;
import org.apache.flink.streaming.api.windowing.policy.TriggerPolicy;
import org.apache.flink.streaming.api.windowing.time.AbstractTime;
import org.apache.flink.streaming.api.windowing.time.EventTime;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.api.windowing.windows.Window;
import org.apache.flink.streaming.runtime.operators.ExtractTimestampsOperator;
import org.apache.flink.streaming.runtime.partitioner.BroadcastPartitioner;
import org.apache.flink.streaming.runtime.partitioner.CustomPartitionerWrapper;
import org.apache.flink.streaming.runtime.partitioner.ForwardPartitioner;
import org.apache.flink.streaming.runtime.partitioner.RebalancePartitioner;
import org.apache.flink.streaming.runtime.partitioner.HashPartitioner;
import org.apache.flink.streaming.runtime.partitioner.GlobalPartitioner;
import org.apache.flink.streaming.runtime.partitioner.ShufflePartitioner;
import org.apache.flink.streaming.runtime.partitioner.StreamPartitioner;
import org.apache.flink.streaming.util.keys.KeySelectorUtil;
import org.apache.flink.streaming.util.serialization.SerializationSchema;

import com.google.common.base.Preconditions;

/**
 * A DataStream represents a stream of elements of the same type. A DataStream
 * can be transformed into another DataStream by applying a transformation as
 * for example:
 * <ul>
 * <li>{@link DataStream#map},
 * <li>{@link DataStream#filter}, or
 * </ul>
 * 
 * @param <T> The type of the elements in this Stream
 */
public class DataStream<T> {

	protected final StreamExecutionEnvironment environment;

	protected final StreamTransformation<T> transformation;

	/**
	 * Create a new {@link DataStream} in the given execution environment with
	 * partitioning set to forward by default.
	 *
	 * @param environment The StreamExecutionEnvironment
	 */
	public DataStream(StreamExecutionEnvironment environment, StreamTransformation<T> transformation) {
		this.environment = Preconditions.checkNotNull(environment, "Execution Environment must not be null.");
		this.transformation = Preconditions.checkNotNull(transformation, "Stream Transformation must not be null.");
	}

	/**
	 * Returns the ID of the {@link DataStream} in the current {@link StreamExecutionEnvironment}.
	 * 
	 * @return ID of the DataStream
	 */
	public Integer getId() {
		return transformation.getId();
	}

	/**
	 * Gets the parallelism for this operator.
	 * 
	 * @return The parallelism set for this operator.
	 */
	public int getParallelism() {
		return transformation.getParallelism();
	}

	/**
	 * Gets the type of the stream.
	 * 
	 * @return The type of the datastream.
	 */
	public TypeInformation<T> getType() {
		return transformation.getOutputType();
	}

	/**
	 * Invokes the {@link org.apache.flink.api.java.ClosureCleaner}
	 * on the given function if closure cleaning is enabled in the {@link ExecutionConfig}.
	 *
	 * @return The cleaned Function
	 */
	protected <F> F clean(F f) {
		return getExecutionEnvironment().clean(f);
	}

	/**
	 * Returns the {@link StreamExecutionEnvironment} that was used to create this
	 * {@link DataStream}
	 *
	 * @return The Execution Environment
	 */
	public StreamExecutionEnvironment getExecutionEnvironment() {
		return environment;
	}

	public ExecutionConfig getExecutionConfig() {
		return environment.getConfig();
	}

	/**
	 * Creates a new {@link DataStream} by merging {@link DataStream} outputs of
	 * the same type with each other. The DataStreams merged using this operator
	 * will be transformed simultaneously.
	 * 
	 * @param streams
	 *            The DataStreams to union output with.
	 * @return The {@link DataStream}.
	 */
	public DataStream<T> union(DataStream<T>... streams) {
		List<StreamTransformation<T>> unionedTransforms = Lists.newArrayList();
		unionedTransforms.add(this.transformation);

		Collection<StreamTransformation<?>> thisPredecessors = this.getTransformation().getTransitivePredecessors();

		for (DataStream<T> newStream : streams) {
			if (!(newStream.getParallelism() == this.getParallelism())) {
				throw new UnsupportedClassVersionError(
						"DataStream can only be unioned with DataStreams of the same parallelism. " +
								"This Stream: " + this.getTransformation() +
								", other stream: " + newStream.getTransformation());
			}
			Collection<StreamTransformation<?>> predecessors = newStream.getTransformation().getTransitivePredecessors();

			if (predecessors.contains(this.transformation) || thisPredecessors.contains(newStream.getTransformation())) {
				throw new UnsupportedOperationException("A DataStream cannot be unioned with itself");
			}
			unionedTransforms.add(newStream.getTransformation());
		}
		return new DataStream<T>(this.environment, new UnionTransformation<T>(unionedTransforms));
	}

	/**
	 * Operator used for directing tuples to specific named outputs using an
	 * {@link org.apache.flink.streaming.api.collector.selector.OutputSelector}.
	 * Calling this method on an operator creates a new {@link SplitDataStream}.
	 * 
	 * @param outputSelector
	 *            The user defined
	 *            {@link org.apache.flink.streaming.api.collector.selector.OutputSelector}
	 *            for directing the tuples.
	 * @return The {@link SplitDataStream}
	 */
	public SplitDataStream<T> split(OutputSelector<T> outputSelector) {
		return new SplitDataStream<T>(this, clean(outputSelector));
	}

	/**
	 * Creates a new {@link ConnectedStreams} by connecting
	 * {@link DataStream} outputs of (possible) different types with each other.
	 * The DataStreams connected using this operator can be used with
	 * CoFunctions to apply joint transformations.
	 * 
	 * @param dataStream
	 *            The DataStream with which this stream will be connected.
	 * @return The {@link ConnectedStreams}.
	 */
	public <R> ConnectedStreams<T, R> connect(DataStream<R> dataStream) {
		return new ConnectedStreams<T, R>(environment, this, dataStream);
	}

	/**
	 * 
	 * It creates a new {@link KeyedStream} that uses the provided key for partitioning
	 * its operator states. 
	 *
	 * @param key
	 *            The KeySelector to be used for extracting the key for partitioning
	 * @return The {@link DataStream} with partitioned state (i.e. KeyedStream)
	 */
	public <K> KeyedStream<T, K> keyBy(KeySelector<T, K> key){
		return new KeyedStream<T, K>(this, clean(key));
	}

	/**
	 * Partitions the operator state of a {@link DataStream} by the given key positions. 
	 *
	 * @param fields
	 *            The position of the fields on which the {@link DataStream}
	 *            will be grouped.
	 * @return The {@link DataStream} with partitioned state (i.e. KeyedStream)
	 */
	public KeyedStream<T, Tuple> keyBy(int... fields) {
		if (getType() instanceof BasicArrayTypeInfo || getType() instanceof PrimitiveArrayTypeInfo) {
			return keyBy(new KeySelectorUtil.ArrayKeySelector<T>(fields));
		} else {
			return keyBy(new Keys.ExpressionKeys<T>(fields, getType()));
		}
	}

	/**
	 * Partitions the operator state of a {@link DataStream}using field expressions. 
	 * A field expression is either the name of a public field or a getter method with parentheses
	 * of the {@link DataStream}S underlying type. A dot can be used to drill
	 * down into objects, as in {@code "field1.getInnerField2()" }.
	 *
	 * @param fields
	 *            One or more field expressions on which the state of the {@link DataStream} operators will be
	 *            partitioned.
	 * @return The {@link DataStream} with partitioned state (i.e. KeyedStream)
	 **/
	public KeyedStream<T, Tuple> keyBy(String... fields) {
		return keyBy(new Keys.ExpressionKeys<T>(fields, getType()));
	}

	private KeyedStream<T, Tuple> keyBy(Keys<T> keys) {
		return new KeyedStream<T, Tuple>(this, clean(KeySelectorUtil.getSelectorForKeys(keys,
				getType(), getExecutionConfig())));
	}
	
	/**
	 * Partitions the operator state of a {@link DataStream} by the given key positions. 
	 * Mind that keyBy does not affect the partitioning of the {@link DataStream}
	 * but only the way explicit state is partitioned among parallel instances.
	 * 
	 * @param fields
	 *            The position of the fields on which the states of the {@link DataStream}
	 *            will be partitioned.
	 * @return The {@link DataStream} with partitioned state (i.e. KeyedStream)
	 */
	public GroupedDataStream<T, Tuple> groupBy(int... fields) {
		if (getType() instanceof BasicArrayTypeInfo || getType() instanceof PrimitiveArrayTypeInfo) {
			return groupBy(new KeySelectorUtil.ArrayKeySelector<T>(fields));
		} else {
			return groupBy(new Keys.ExpressionKeys<T>(fields, getType()));
		}
	}

	/**
	 * Groups a {@link DataStream} using field expressions. A field expression
	 * is either the name of a public field or a getter method with parentheses
	 * of the {@link DataStream}S underlying type. A dot can be used to drill
	 * down into objects, as in {@code "field1.getInnerField2()" }. This method
	 * returns an {@link GroupedDataStream}.
	 *
	 * <p>
	 * This operator also affects the
	 * partitioning of the stream, by forcing values with the same key to go to
	 * the same processing instance.
	 * 
	 * @param fields
	 *            One or more field expressions on which the DataStream will be
	 *            grouped.
	 * @return The grouped {@link DataStream}
	 **/
	public GroupedDataStream<T, Tuple> groupBy(String... fields) {
		return groupBy(new Keys.ExpressionKeys<T>(fields, getType()));
	}

	/**
	 * Groups the elements of a {@link DataStream} by the key extracted by the
	 * {@link KeySelector} to be used with grouped operators like
	 * {@link GroupedDataStream#reduce(org.apache.flink.api.common.functions.ReduceFunction)}.
	 *
	 * <p/>
	 * This operator also affects the partitioning of the stream, by forcing
	 * values with the same key to go to the same processing instance.
	 * 
	 * @param keySelector
	 *            The {@link KeySelector} that will be used to extract keys for
	 *            the values
	 * @return The grouped {@link DataStream}
	 */
	public <K> GroupedDataStream<T, K> groupBy(KeySelector<T, K> keySelector) {
		return new GroupedDataStream<T, K>(this, clean(keySelector));
	}

	private GroupedDataStream<T, Tuple> groupBy(Keys<T> keys) {
		return new GroupedDataStream<T, Tuple>(this, 
				clean(KeySelectorUtil.getSelectorForKeys(keys, getType(), getExecutionConfig())));
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output is
	 * partitioned hashing on the given fields. This setting only
	 * effects the how the outputs will be distributed between the parallel
	 * instances of the next processing operator.
	 *
	 * @param fields The tuple fields that should be used for partitioning
	 * @return The partitioned DataStream
	 *
	 */
	public DataStream<T> partitionByHash(int... fields) {
		if (getType() instanceof BasicArrayTypeInfo || getType() instanceof PrimitiveArrayTypeInfo) {
			return partitionByHash(new KeySelectorUtil.ArrayKeySelector<T>(fields));
		} else {
			return partitionByHash(new Keys.ExpressionKeys<T>(fields, getType()));
		}
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output is
	 * partitioned hashing on the given fields. This setting only
	 * effects the how the outputs will be distributed between the parallel
	 * instances of the next processing operator.
	 *
	 * @param fields The tuple fields that should be used for partitioning
	 * @return The partitioned DataStream
	 *
	 */
	public DataStream<T> partitionByHash(String... fields) {
		return partitionByHash(new Keys.ExpressionKeys<T>(fields, getType()));
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output is
	 * partitioned using the given {@link KeySelector}. This setting only
	 * effects the how the outputs will be distributed between the parallel
	 * instances of the next processing operator.
	 *
	 * @param keySelector The function that extracts the key from an element in the Stream
	 * @return The partitioned DataStream
	 */
	public DataStream<T> partitionByHash(KeySelector<T, ?> keySelector) {
		return setConnectionType(new HashPartitioner<T>(clean(keySelector)));
	}

	//private helper method for partitioning
	private DataStream<T> partitionByHash(Keys<T> keys) {
		KeySelector<T, ?> keySelector = clean(KeySelectorUtil.getSelectorForKeys(
				keys,
				getType(),
				getExecutionConfig()));

		return setConnectionType(new HashPartitioner<T>(keySelector));
	}

	/**
	 * Partitions a tuple DataStream on the specified key fields using a custom partitioner.
	 * This method takes the key position to partition on, and a partitioner that accepts the key type.
	 * <p>
	 * Note: This method works only on single field keys.
	 *
	 * @param partitioner The partitioner to assign partitions to keys.
	 * @param field The field index on which the DataStream is to partitioned.
	 * @return The partitioned DataStream.
	 */
	public <K> DataStream<T> partitionCustom(Partitioner<K> partitioner, int field) {
		Keys.ExpressionKeys<T> outExpressionKeys = new Keys.ExpressionKeys<T>(new int[]{field}, getType());
		return partitionCustom(partitioner, outExpressionKeys);
	}

	/**
	 * Partitions a POJO DataStream on the specified key fields using a custom partitioner.
	 * This method takes the key expression to partition on, and a partitioner that accepts the key type.
	 * <p>
	 * Note: This method works only on single field keys.
	 *
	 * @param partitioner The partitioner to assign partitions to keys.
	 * @param field The field index on which the DataStream is to partitioned.
	 * @return The partitioned DataStream.
	 */
	public <K> DataStream<T> partitionCustom(Partitioner<K> partitioner, String field) {
		Keys.ExpressionKeys<T> outExpressionKeys = new Keys.ExpressionKeys<T>(new String[]{field}, getType());
		return partitionCustom(partitioner, outExpressionKeys);
	}


	/**
	 * Partitions a DataStream on the key returned by the selector, using a custom partitioner.
	 * This method takes the key selector to get the key to partition on, and a partitioner that
	 * accepts the key type.
	 * <p>
	 * Note: This method works only on single field keys, i.e. the selector cannot return tuples
	 * of fields.
	 *
	 * @param partitioner
	 * 		The partitioner to assign partitions to keys.
	 * @param keySelector
	 * 		The KeySelector with which the DataStream is partitioned.
	 * @return The partitioned DataStream.
	 * @see KeySelector
	 */
	public <K> DataStream<T> partitionCustom(Partitioner<K> partitioner, KeySelector<T, K> keySelector) {
		return setConnectionType(new CustomPartitionerWrapper<K, T>(clean(partitioner), clean(keySelector)));
	}

	//	private helper method for custom partitioning
	private <K> DataStream<T> partitionCustom(Partitioner<K> partitioner, Keys<T> keys) {
		KeySelector<T, K> keySelector = KeySelectorUtil.getSelectorForOneKey(keys, partitioner, getType(), getExecutionConfig());

		return setConnectionType(
				new CustomPartitionerWrapper<K, T>(
						clean(partitioner),
						clean(keySelector)));
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are broadcasted to every parallel instance of the next component.
	 *
	 * <p>
	 * This setting only effects the how the outputs will be distributed between
	 * the parallel instances of the next processing operator.
	 * 
	 * @return The DataStream with broadcast partitioning set.
	 */
	public DataStream<T> broadcast() {
		return setConnectionType(new BroadcastPartitioner<T>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are shuffled uniformly randomly to the next component.
	 *
	 * <p>
	 * This setting only effects the how the outputs will be distributed between
	 * the parallel instances of the next processing operator.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<T> shuffle() {
		return setConnectionType(new ShufflePartitioner<T>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are forwarded to the local subtask of the next component (whenever
	 * possible).
	 *
	 * <p>
	 * This setting only effects the how the outputs will be distributed between
	 * the parallel instances of the next processing operator.
	 * 
	 * @return The DataStream with forward partitioning set.
	 */
	public DataStream<T> forward() {
		return setConnectionType(new ForwardPartitioner<T>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output tuples
	 * are distributed evenly to instances of the next component in a Round-robin
	 * fashion.
	 *
	 * <p>
	 * This setting only effects the how the outputs will be distributed between
	 * the parallel instances of the next processing operator.
	 * 
	 * @return The DataStream with rebalance partitioning set.
	 */
	public DataStream<T> rebalance() {
		return setConnectionType(new RebalancePartitioner<T>());
	}

	/**
	 * Sets the partitioning of the {@link DataStream} so that the output values
	 * all go to the first instance of the next processing operator. Use this
	 * setting with care since it might cause a serious performance bottleneck
	 * in the application.
	 * 
	 * @return The DataStream with shuffle partitioning set.
	 */
	public DataStream<T> global() {
		return setConnectionType(new GlobalPartitioner<T>());
	}

	/**
	 * Initiates an iterative part of the program that feeds back data streams.
	 * The iterative part needs to be closed by calling
	 * {@link IterativeDataStream#closeWith(DataStream)}. The transformation of
	 * this IterativeDataStream will be the iteration head. The data stream
	 * given to the {@link IterativeDataStream#closeWith(DataStream)} method is
	 * the data stream that will be fed back and used as the input for the
	 * iteration head. The user can also use different feedback type than the
	 * input of the iteration and treat the input and feedback streams as a
	 * {@link ConnectedStreams} be calling
	 * {@link IterativeDataStream#withFeedbackType(TypeInformation)}
	 * <p>
	 * A common usage pattern for streaming iterations is to use output
	 * splitting to send a part of the closing data stream to the head. Refer to
	 * {@link #split(OutputSelector)} for more information.
	 * <p>
	 * The iteration edge will be partitioned the same way as the first input of
	 * the iteration head unless it is changed in the
	 * {@link IterativeDataStream#closeWith(DataStream)} call.
	 * <p>
	 * By default a DataStream with iteration will never terminate, but the user
	 * can use the maxWaitTime parameter to set a max waiting time for the
	 * iteration head. If no data received in the set time, the stream
	 * terminates.
	 * 
	 * @return The iterative data stream created.
	 */
	public IterativeDataStream<T> iterate() {
		return new IterativeDataStream<T>(this, 0);
	}

	/**
	 * Initiates an iterative part of the program that feeds back data streams.
	 * The iterative part needs to be closed by calling
	 * {@link IterativeDataStream#closeWith(DataStream)}. The transformation of
	 * this IterativeDataStream will be the iteration head. The data stream
	 * given to the {@link IterativeDataStream#closeWith(DataStream)} method is
	 * the data stream that will be fed back and used as the input for the
	 * iteration head. The user can also use different feedback type than the
	 * input of the iteration and treat the input and feedback streams as a
	 * {@link ConnectedStreams} be calling
	 * {@link IterativeDataStream#withFeedbackType(TypeInformation)}
	 * <p>
	 * A common usage pattern for streaming iterations is to use output
	 * splitting to send a part of the closing data stream to the head. Refer to
	 * {@link #split(OutputSelector)} for more information.
	 * <p>
	 * The iteration edge will be partitioned the same way as the first input of
	 * the iteration head unless it is changed in the
	 * {@link IterativeDataStream#closeWith(DataStream)} call.
	 * <p>
	 * By default a DataStream with iteration will never terminate, but the user
	 * can use the maxWaitTime parameter to set a max waiting time for the
	 * iteration head. If no data received in the set time, the stream
	 * terminates.
	 * 
	 * @param maxWaitTimeMillis
	 *            Number of milliseconds to wait between inputs before shutting
	 *            down
	 * 
	 * @return The iterative data stream created.
	 */
	public IterativeDataStream<T> iterate(long maxWaitTimeMillis) {
		return new IterativeDataStream<T>(this, maxWaitTimeMillis);
	}

	/**
	 * Applies a Map transformation on a {@link DataStream}. The transformation
	 * calls a {@link MapFunction} for each element of the DataStream. Each
	 * MapFunction call returns exactly one element. The user can also extend
	 * {@link RichMapFunction} to gain access to other features provided by the
	 * {@link org.apache.flink.api.common.functions.RichFunction} interface.
	 * 
	 * @param mapper
	 *            The MapFunction that is called for each element of the
	 *            DataStream.
	 * @param <R>
	 *            output type
	 * @return The transformed {@link DataStream}.
	 */
	public <R> SingleOutputStreamOperator<R, ?> map(MapFunction<T, R> mapper) {

		TypeInformation<R> outType = TypeExtractor.getMapReturnTypes(clean(mapper), getType(),
				Utils.getCallLocationName(), true);

		return transform("Map", outType, new StreamMap<T, R>(clean(mapper)));
	}

	/**
	 * Applies a FlatMap transformation on a {@link DataStream}. The
	 * transformation calls a {@link FlatMapFunction} for each element of the
	 * DataStream. Each FlatMapFunction call can return any number of elements
	 * including none. The user can also extend {@link RichFlatMapFunction} to
	 * gain access to other features provided by the
	 * {@link org.apache.flink.api.common.functions.RichFunction} interface.
	 * 
	 * @param flatMapper
	 *            The FlatMapFunction that is called for each element of the
	 *            DataStream
	 * 
	 * @param <R>
	 *            output type
	 * @return The transformed {@link DataStream}.
	 */
	public <R> SingleOutputStreamOperator<R, ?> flatMap(FlatMapFunction<T, R> flatMapper) {

		TypeInformation<R> outType = TypeExtractor.getFlatMapReturnTypes(clean(flatMapper),
				getType(), Utils.getCallLocationName(), true);

		return transform("Flat Map", outType, new StreamFlatMap<T, R>(clean(flatMapper)));

	}

	/**
	 * Applies a Filter transformation on a {@link DataStream}. The
	 * transformation calls a {@link FilterFunction} for each element of the
	 * DataStream and retains only those element for which the function returns
	 * true. Elements for which the function returns false are filtered. The
	 * user can also extend {@link RichFilterFunction} to gain access to other
	 * features provided by the
	 * {@link org.apache.flink.api.common.functions.RichFunction} interface.
	 * 
	 * @param filter
	 *            The FilterFunction that is called for each element of the
	 *            DataStream.
	 * @return The filtered DataStream.
	 */
	public SingleOutputStreamOperator<T, ?> filter(FilterFunction<T> filter) {
		return transform("Filter", getType(), new StreamFilter<T>(clean(filter)));

	}

	/**
	 * Initiates a Project transformation on a {@link Tuple} {@link DataStream}.<br/>
	 * <b>Note: Only Tuple DataStreams can be projected.</b>
	 *
	 * <p>
	 * The transformation projects each Tuple of the DataSet onto a (sub)set of
	 * fields.
	 * 
	 * @param fieldIndexes
	 *            The field indexes of the input tuples that are retained. The
	 *            order of fields in the output tuple corresponds to the order
	 *            of field indexes.
	 * @return The projected DataStream
	 * 
	 * @see Tuple
	 * @see DataStream
	 */
	public <R extends Tuple> SingleOutputStreamOperator<R, ?> project(int... fieldIndexes) {
		return new StreamProjection<T>(this, fieldIndexes).projectTupleX();
	}

	/**
	 * Initiates a temporal Join transformation. <br/>
	 * A temporal Join transformation joins the elements of two
	 * {@link DataStream}s on key equality over a specified time window.
	 *
	 * <p>
	 * This method returns a {@link StreamJoinOperator} on which the
	 * {@link StreamJoinOperator#onWindow(long, java.util.concurrent.TimeUnit)}
	 * should be called to define the window, and then the
	 * {@link StreamJoinOperator.JoinWindow#where(int...)} and
	 * {@link StreamJoinOperator.JoinPredicate#equalTo(int...)} can be used to define
	 * the join keys.
	 * <p>
	 * The user can also use the
	 * {@link org.apache.flink.streaming.api.datastream.temporal.StreamJoinOperator.JoinPredicate.JoinedStream#with}
	 * to apply a custom join function.
	 * 
	 * @param dataStreamToJoin
	 *            The other DataStream with which this DataStream is joined.
	 * @return A {@link StreamJoinOperator} to continue the definition of the
	 *         Join transformation.
	 * 
	 */
	public <IN2> StreamJoinOperator<T, IN2> join(DataStream<IN2> dataStreamToJoin) {
		return new StreamJoinOperator<T, IN2>(this, dataStreamToJoin);
	}

	/**
	 * Create a {@link WindowedDataStream} that can be used to apply
	 * transformation like {@link WindowedDataStream#reduceWindow},
	 * {@link WindowedDataStream#mapWindow} or aggregations on preset
	 * chunks(windows) of the data stream. To define windows a
	 * {@link WindowingHelper} such as {@link Time}, {@link Count},
	 * {@link Delta} and {@link FullStream} can be used.
	 *
	 * <p>
	 * When applied to a grouped data stream, the windows (evictions) and slide sizes
	 * (triggers) will be computed on a per group basis.
	 *
	 * <p>
	 * For more advanced control over the trigger and eviction policies please refer to
	 * {@link #window(TriggerPolicy, EvictionPolicy)}
	 *
	 * <p>
	 * For example, to create a sum every 5 seconds in a tumbling fashion:
	 *
	 * <pre>
	 * {@code ds.window(Time.of(5, TimeUnit.SECONDS)).sum(field)}
	 * </pre>
	 *
	 * <p>
	 * To create sliding windows use the
	 * {@link WindowedDataStream#every(WindowingHelper)}, for example with 3 second slides:</br>
	 *
	 * <pre>
	 * 
	 * {@code
	 * ds.window(Time.of(5, TimeUnit.SECONDS)).every(Time.of(3, TimeUnit.SECONDS)).sum(field)
	 * }
	 *
	 * </pre>
	 * 
	 * @param policyHelper
	 *            Any {@link WindowingHelper} such as {@link Time},
	 *            {@link Count}, {@link Delta} {@link FullStream} to define the
	 *            window size.
	 *
	 * @return A {@link WindowedDataStream} providing further operations.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public WindowedDataStream<T> window(WindowingHelper policyHelper) {
		policyHelper.setExecutionConfig(getExecutionConfig());
		return new WindowedDataStream<T>(this, policyHelper);
	}

	/**
	 * Create a {@link WindowedDataStream} using the given {@link TriggerPolicy}
	 * and {@link EvictionPolicy}. Windowing can be used to apply transformation
	 * like {@link WindowedDataStream#reduceWindow},
	 * {@link WindowedDataStream#mapWindow} or aggregations on preset
	 * chunks(windows) of the data stream.
	 *
	 * <p>
	 * For most common use-cases please refer to {@link #window(WindowingHelper)}
	 * 
	 * @param trigger
	 *            The {@link TriggerPolicy} that will determine how often the
	 *            user function is called on the window.
	 * @param eviction
	 *            The {@link EvictionPolicy} that will determine the number of
	 *            elements in each time window.
	 * @return A {@link WindowedDataStream} providing further operations.
	 */
	public WindowedDataStream<T> window(TriggerPolicy<T> trigger, EvictionPolicy<T> eviction) {
		return new WindowedDataStream<T>(this, trigger, eviction);
	}

	/**
	 * Create a {@link WindowedDataStream} on the full stream history, to
	 * produce periodic aggregates.
	 * 
	 * @return A {@link WindowedDataStream} providing further operations.
	 */
	@SuppressWarnings("rawtypes")
	public WindowedDataStream<T> every(WindowingHelper policyHelper) {
		policyHelper.setExecutionConfig(getExecutionConfig());
		return window(FullStream.window()).every(policyHelper);
	}

	/**
	 * Windows this {@code DataStream} into tumbling time windows.
	 *
	 * <p>
	 * This is a shortcut for either {@code .window(TumblingTimeWindows.of(size))} or
	 * {@code .window(TumblingProcessingTimeWindows.of(size))} depending on the time characteristic
	 * set using
	 * {@link org.apache.flink.streaming.api.environment.StreamExecutionEnvironment#setStreamTimeCharacteristic(org.apache.flink.streaming.api.TimeCharacteristic)}
	 *
	 * @param size The size of the window.
	 */
	public AllWindowedStream<T, TimeWindow> timeWindowAll(AbstractTime size) {
		AbstractTime actualSize = size.makeSpecificBasedOnTimeCharacteristic(environment.getStreamTimeCharacteristic());

		if (actualSize instanceof EventTime) {
			return windowAll(TumblingTimeWindows.of(actualSize.toMilliseconds()));
		} else {
			return windowAll(TumblingProcessingTimeWindows.of(actualSize.toMilliseconds()));
		}
	}

	/**
	 * Windows this {@code DataStream} into sliding time windows.
	 *
	 * <p>
	 * This is a shortcut for either {@code .window(SlidingTimeWindows.of(size, slide))} or
	 * {@code .window(SlidingProcessingTimeWindows.of(size, slide))} depending on the time characteristic
	 * set using
	 * {@link org.apache.flink.streaming.api.environment.StreamExecutionEnvironment#setStreamTimeCharacteristic(org.apache.flink.streaming.api.TimeCharacteristic)}
	 *
	 * @param size The size of the window.
	 */
	public AllWindowedStream<T, TimeWindow> timeWindowAll(AbstractTime size, AbstractTime slide) {
		AbstractTime actualSize = size.makeSpecificBasedOnTimeCharacteristic(environment.getStreamTimeCharacteristic());
		AbstractTime actualSlide = slide.makeSpecificBasedOnTimeCharacteristic(environment.getStreamTimeCharacteristic());

		if (actualSize instanceof EventTime) {
			return windowAll(SlidingTimeWindows.of(actualSize.toMilliseconds(),
					actualSlide.toMilliseconds()));
		} else {
			return windowAll(SlidingProcessingTimeWindows.of(actualSize.toMilliseconds(),
					actualSlide.toMilliseconds()));
		}
	}

	/**
	 * Windows this data stream to a {@code KeyedTriggerWindowDataStream}, which evaluates windows
	 * over a key grouped stream. Elements are put into windows by a
	 * {@link org.apache.flink.streaming.api.windowing.assigners.WindowAssigner}. The grouping of
	 * elements is done both by key and by window.
	 *
	 * <p>
	 * A {@link org.apache.flink.streaming.api.windowing.triggers.Trigger} can be defined to specify
	 * when windows are evaluated. However, {@code WindowAssigners} have a default {@code Trigger}
	 * that is used if a {@code Trigger} is not specified.
	 *
	 * <p>
	 * Note: This operation can be inherently non-parallel since all elements have to pass through
	 * the same operator instance. (Only for special cases, such as aligned time windows is
	 * it possible to perform this operation in parallel).
	 *
	 * @param assigner The {@code WindowAssigner} that assigns elements to windows.
	 * @return The trigger windows data stream.
	 */
	public <W extends Window> AllWindowedStream<T, W> windowAll(WindowAssigner<? super T, W> assigner) {
		return new AllWindowedStream<>(this, assigner);
	}

	/**
	 * Extracts a timestamp from an element and assigns it as the internal timestamp of that element.
	 * The internal timestamps are, for example, used to to event-time window operations.
	 *
	 * <p>
	 * If you know that the timestamps are strictly increasing you can use an
	 * {@link org.apache.flink.streaming.api.functions.AscendingTimestampExtractor}. Otherwise,
	 * you should provide a {@link TimestampExtractor} that also implements
	 * {@link TimestampExtractor#getCurrentWatermark()} to keep track of watermarks.
	 *
	 * @see org.apache.flink.streaming.api.watermark.Watermark
	 *
	 * @param extractor The TimestampExtractor that is called for each element of the DataStream.
	 */
	public SingleOutputStreamOperator<T, ?> extractTimestamp(TimestampExtractor<T> extractor) {
		// match parallelism to input, otherwise dop=1 sources could lead to some strange
		// behaviour: the watermark will creep along very slowly because the elements
		// from the source go to each extraction operator round robin.
		int inputParallelism = getTransformation().getParallelism();
		ExtractTimestampsOperator<T> operator = new ExtractTimestampsOperator<>(clean(extractor));
		return transform("ExtractTimestamps", getTransformation().getOutputType(), operator)
				.setParallelism(inputParallelism);
	}

	/**
	 * Writes a DataStream to the standard output stream (stdout).
	 *
	 * <p>
	 * For each element of the DataStream the result of
	 * {@link Object#toString()} is written.
	 * 
	 * @return The closed DataStream.
	 */
	public DataStreamSink<T> print() {
		PrintSinkFunction<T> printFunction = new PrintSinkFunction<T>();
		return addSink(printFunction);
	}

	/**
	 * Writes a DataStream to the standard output stream (stderr).
	 *
	 * <p>
	 * For each element of the DataStream the result of
	 * {@link Object#toString()} is written.
	 * 
	 * @return The closed DataStream.
	 */
	public DataStreamSink<T> printToErr() {
		PrintSinkFunction<T> printFunction = new PrintSinkFunction<T>(true);
		return addSink(printFunction);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format.
	 *
	 * <p>
	 * For every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            the path pointing to the location the text file is written to
	 * 
	 * @return the closed DataStream.
	 */
	public DataStreamSink<T> writeAsText(String path) {
		return write(new TextOutputFormat<T>(new Path(path)), 0L);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format. The
	 * writing is performed periodically, in every millis milliseconds.
	 *
	 * <p>
	 * For every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            the path pointing to the location the text file is written to
	 * @param millis
	 *            the file update frequency
	 * 
	 * @return the closed DataStream
	 */
	public DataStreamSink<T> writeAsText(String path, long millis) {
		TextOutputFormat<T> tof = new TextOutputFormat<T>(new Path(path));
		return write(tof, millis);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format.
	 *
	 * <p>
	 * For every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            the path pointing to the location the text file is written to
	 * @param writeMode
	 *            Control the behavior for existing files. Options are
	 *            NO_OVERWRITE and OVERWRITE.
	 * 
	 * @return the closed DataStream.
	 */
	public DataStreamSink<T> writeAsText(String path, WriteMode writeMode) {
		TextOutputFormat<T> tof = new TextOutputFormat<T>(new Path(path));
		tof.setWriteMode(writeMode);
		return write(tof, 0L);
	}

	/**
	 * Writes a DataStream to the file specified by path in text format.
	 *
	 * <p>
	 * For every element of the DataStream the result of {@link Object#toString()}
	 * is written.
	 * 
	 * @param path
	 *            the path pointing to the location the text file is written to
	 * @param writeMode
	 *            Controls the behavior for existing files. Options are
	 *            NO_OVERWRITE and OVERWRITE.
	 * @param millis
	 *            the file update frequency
	 * 
	 * @return the closed DataStream.
	 */
	public DataStreamSink<T> writeAsText(String path, WriteMode writeMode, long millis) {
		TextOutputFormat<T> tof = new TextOutputFormat<T>(new Path(path));
		tof.setWriteMode(writeMode);
		return write(tof, millis);
	}

	/**
	 * Writes a DataStream to the file specified by path in csv format.
	 *
	 * <p>
	 * For every field of an element of the DataStream the result of {@link Object#toString()}
	 * is written. This method can only be used on data streams of tuples.
	 * 
	 * @param path
	 *            the path pointing to the location the text file is written to
	 * 
	 * @return the closed DataStream
	 */
	@SuppressWarnings("unchecked")
	public <X extends Tuple> DataStreamSink<T> writeAsCsv(String path) {
		Preconditions.checkArgument(getType().isTupleType(),
				"The writeAsCsv() method can only be used on data sets of tuples.");
		CsvOutputFormat<X> of = new CsvOutputFormat<X>(new Path(path),
				CsvOutputFormat.DEFAULT_LINE_DELIMITER, CsvOutputFormat.DEFAULT_FIELD_DELIMITER);
		return write((OutputFormat<T>) of, 0L);
	}

	/**
	 * Writes a DataStream to the file specified by path in csv format. The
	 * writing is performed periodically, in every millis milliseconds.
	 *
	 * <p>
	 * For every field of an element of the DataStream the result of {@link Object#toString()}
	 * is written. This method can only be used on data streams of tuples.
	 *
	 * @param path
	 *            the path pointing to the location the text file is written to
	 * @param millis
	 *            the file update frequency
	 * 
	 * @return the closed DataStream
	 */
	@SuppressWarnings("unchecked")
	public <X extends Tuple> DataStreamSink<T> writeAsCsv(String path, long millis) {
		Preconditions.checkArgument(getType().isTupleType(),
				"The writeAsCsv() method can only be used on data sets of tuples.");
		CsvOutputFormat<X> of = new CsvOutputFormat<X>(new Path(path),
				CsvOutputFormat.DEFAULT_LINE_DELIMITER, CsvOutputFormat.DEFAULT_FIELD_DELIMITER);
		return write((OutputFormat<T>) of, millis);
	}

	/**
	 * Writes a DataStream to the file specified by path in csv format.
	 *
	 * <p>
	 * For every field of an element of the DataStream the result of {@link Object#toString()}
	 * is written. This method can only be used on data streams of tuples.
	 * 
	 * @param path
	 *            the path pointing to the location the text file is written to
	 * @param writeMode
	 *            Controls the behavior for existing files. Options are
	 *            NO_OVERWRITE and OVERWRITE.
	 * 
	 * @return the closed DataStream
	 */
	@SuppressWarnings("unchecked")
	public <X extends Tuple> DataStreamSink<T> writeAsCsv(String path, WriteMode writeMode) {
		Preconditions.checkArgument(getType().isTupleType(),
				"The writeAsCsv() method can only be used on data sets of tuples.");
		CsvOutputFormat<X> of = new CsvOutputFormat<X>(new Path(path),
				CsvOutputFormat.DEFAULT_LINE_DELIMITER, CsvOutputFormat.DEFAULT_FIELD_DELIMITER);
		if (writeMode != null) {
			of.setWriteMode(writeMode);
		}
		return write((OutputFormat<T>) of, 0L);
	}

	/**
	 * Writes a DataStream to the file specified by path in csv format. The
	 * writing is performed periodically, in every millis milliseconds.
	 *
	 * <p>
	 * For every field of an element of the DataStream the result of {@link Object#toString()}
	 * is written. This method can only be used on data streams of tuples.
	 * 
	 * @param path
	 *            the path pointing to the location the text file is written to
	 * @param writeMode
	 *            Controls the behavior for existing files. Options are
	 *            NO_OVERWRITE and OVERWRITE.
	 * @param millis
	 *            the file update frequency
	 * 
	 * @return the closed DataStream
	 */
	@SuppressWarnings("unchecked")
	public <X extends Tuple> DataStreamSink<T> writeAsCsv(String path, WriteMode writeMode,
			long millis) {
		Preconditions.checkArgument(getType().isTupleType(),
				"The writeAsCsv() method can only be used on data sets of tuples.");
		CsvOutputFormat<X> of = new CsvOutputFormat<X>(new Path(path),
				CsvOutputFormat.DEFAULT_LINE_DELIMITER, CsvOutputFormat.DEFAULT_FIELD_DELIMITER);
		if (writeMode != null) {
			of.setWriteMode(writeMode);
		}
		return write((OutputFormat<T>) of, millis);
	}

	/**
	 * Writes the DataStream to a socket as a byte array. The format of the
	 * output is specified by a {@link SerializationSchema}.
	 * 
	 * @param hostName
	 *            host of the socket
	 * @param port
	 *            port of the socket
	 * @param schema
	 *            schema for serialization
	 * @return the closed DataStream
	 */
	public DataStreamSink<T> writeToSocket(String hostName, int port, SerializationSchema<T, byte[]> schema) {
		DataStreamSink<T> returnStream = addSink(new SocketClientSink<T>(hostName, port, schema, 0));
		returnStream.setParallelism(1); // It would not work if multiple instances would connect to the same port
		return returnStream;
	}
	
	/**
	 * Writes the dataStream into an output, described by an OutputFormat.
	 * 
	 * @param format The output format
	 * @param millis the write frequency
	 * @return The closed DataStream
	 */
	public DataStreamSink<T> write(OutputFormat<T> format, long millis) {
		return addSink(new FileSinkFunctionByMillis<T>(format, millis));
	}

	/**
	 * Method for passing user defined operators along with the type
	 * information that will transform the DataStream.
	 * 
	 * @param operatorName
	 *            name of the operator, for logging purposes
	 * @param outTypeInfo
	 *            the output type of the operator
	 * @param operator
	 *            the object containing the transformation logic
	 * @param <R>
	 *            type of the return stream
	 * @return the data stream constructed
	 */
	public <R> SingleOutputStreamOperator<R, ?> transform(String operatorName, TypeInformation<R> outTypeInfo, OneInputStreamOperator<T, R> operator) {

		// read the output type of the input Transform to coax out errors about MissingTypeInfo
		transformation.getOutputType();

		OneInputTransformation<T, R> resultTransform = new OneInputTransformation<T, R>(
				this.transformation,
				operatorName,
				operator,
				outTypeInfo,
				environment.getParallelism());

		@SuppressWarnings({ "unchecked", "rawtypes" })
		SingleOutputStreamOperator<R, ?> returnStream = new SingleOutputStreamOperator(environment, resultTransform);

		getExecutionEnvironment().addOperator(resultTransform);

		return returnStream;
	}

	/**
	 * Internal function for setting the partitioner for the DataStream
	 *
	 * @param partitioner
	 *            Partitioner to set.
	 * @return The modified DataStream.
	 */
	protected DataStream<T> setConnectionType(StreamPartitioner<T> partitioner) {
		return new DataStream<T>(this.getExecutionEnvironment(), new PartitionTransformation<T>(this.getTransformation(), partitioner));
	}

	/**
	 * Adds the given sink to this DataStream. Only streams with sinks added
	 * will be executed once the {@link StreamExecutionEnvironment#execute()}
	 * method is called.
	 * 
	 * @param sinkFunction
	 *            The object containing the sink's invoke function.
	 * @return The closed DataStream.
	 */
	public DataStreamSink<T> addSink(SinkFunction<T> sinkFunction) {

		// read the output type of the input Transform to coax out errors about MissingTypeInfo
		transformation.getOutputType();

		// configure the type if needed
		if (sinkFunction instanceof InputTypeConfigurable) {
			((InputTypeConfigurable) sinkFunction).setInputType(getType(), getExecutionConfig() );
		}

		StreamSink<T> sinkOperator = new StreamSink<T>(clean(sinkFunction));

		DataStreamSink<T> sink = new DataStreamSink<T>(this, sinkOperator);

		getExecutionEnvironment().addOperator(sink.getTransformation());
		return sink;
	}

	/**
	 * Returns the {@link StreamTransformation} that represents the operation that logically creates
	 * this {@link DataStream}.
	 *
	 * @return The Transformation
	 */
	public StreamTransformation<T> getTransformation() {
		return transformation;
	}
}
