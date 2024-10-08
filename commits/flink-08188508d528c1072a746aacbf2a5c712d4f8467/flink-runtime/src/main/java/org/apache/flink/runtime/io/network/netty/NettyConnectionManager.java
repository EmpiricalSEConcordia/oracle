/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io.network.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flink.runtime.io.network.ChannelManager;
import org.apache.flink.runtime.io.network.Envelope;
import org.apache.flink.runtime.io.network.EnvelopeDispatcher;
import org.apache.flink.runtime.io.network.NetworkConnectionManager;
import org.apache.flink.runtime.io.network.RemoteReceiver;
import org.apache.flink.runtime.io.network.bufferprovider.BufferProviderBroker;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NettyConnectionManager implements NetworkConnectionManager {

	private static final Logger LOG = LoggerFactory.getLogger(NettyConnectionManager.class);

	private static final int DEBUG_PRINT_QUEUED_ENVELOPES_EVERY_MS = 10000;

	private final ConcurrentMap<RemoteReceiver, Object> outConnections = new ConcurrentHashMap<RemoteReceiver, Object>();

	private final InetAddress bindAddress;

	private final int bindPort;

	private final int bufferSize;

	private final int numInThreads;

	private final int numOutThreads;

	private final int lowWaterMark;

	private final int highWaterMark;

	private final int closeAfterIdleForMs;

	private ServerBootstrap in;

	private Bootstrap out;

	public NettyConnectionManager(
			InetAddress bindAddress,
			int bindPort,
			int bufferSize,
			int numInThreads,
			int numOutThreads,
			int closeAfterIdleForMs) {

		this.bindAddress = bindAddress;
		this.bindPort = bindPort;

		this.bufferSize = bufferSize;

		int defaultNumThreads = Math.max(Runtime.getRuntime().availableProcessors() / 4, 1);

		this.numInThreads = (numInThreads == -1) ? defaultNumThreads : numInThreads;
		this.numOutThreads = (numOutThreads == -1) ? defaultNumThreads : numOutThreads;

		this.lowWaterMark = bufferSize / 2;
		this.highWaterMark = bufferSize;

		this.closeAfterIdleForMs = closeAfterIdleForMs;
	}

	@Override
	public void start(ChannelManager channelManager) throws IOException {
		LOG.info(String.format("Starting with %d incoming and %d outgoing connection threads.",
				numInThreads, numOutThreads));
		LOG.info(String.format("Setting low water mark to %d and high water mark to %d bytes.",
				lowWaterMark, highWaterMark));
		LOG.info(String.format("Close channels after idle for %d ms.", closeAfterIdleForMs));

		final BufferProviderBroker bufferProviderBroker = channelManager;
		final EnvelopeDispatcher envelopeDispatcher = channelManager;

		int numHeapArenas = 0;
		int numDirectArenas = numInThreads + numOutThreads;
		int pageSize = bufferSize << 1;
		int chunkSize = 16 << 20; // 16 MB

		// shift pageSize maxOrder times to get to chunkSize
		int maxOrder = (int) (Math.log(chunkSize / pageSize) / Math.log(2));

		PooledByteBufAllocator pooledByteBufAllocator =
				new PooledByteBufAllocator(true, numHeapArenas, numDirectArenas, pageSize, maxOrder);

		String msg = String.format("Instantiated PooledByteBufAllocator with direct arenas: %d, heap arenas: %d, " +
						"page size (bytes): %d, chunk size (bytes): %d.",
				numDirectArenas, numHeapArenas, pageSize, (pageSize << maxOrder));
		LOG.info(msg);

		// --------------------------------------------------------------------
		// server bootstrap (incoming connections)
		// --------------------------------------------------------------------
		in = new ServerBootstrap();
		in.group(new NioEventLoopGroup(numInThreads))
				.channel(NioServerSocketChannel.class)
				.localAddress(bindAddress, bindPort)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel channel) throws Exception {
						channel.pipeline()
								.addLast(new InboundEnvelopeDecoder(bufferProviderBroker))
								.addLast(new InboundEnvelopeDispatcher(envelopeDispatcher));
					}
				})
				.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(pageSize))
				.option(ChannelOption.ALLOCATOR, pooledByteBufAllocator);

		// --------------------------------------------------------------------
		// client bootstrap (outgoing connections)
		// --------------------------------------------------------------------
		out = new Bootstrap();
		out.group(new NioEventLoopGroup(numOutThreads))
				.channel(NioSocketChannel.class)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel channel) throws Exception {
						channel.pipeline()
								.addLast(new OutboundEnvelopeEncoder());
					}
				})
				.option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, lowWaterMark)
				.option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, highWaterMark)
				.option(ChannelOption.ALLOCATOR, pooledByteBufAllocator)
				.option(ChannelOption.TCP_NODELAY, false)
				.option(ChannelOption.SO_KEEPALIVE, true);

		try {
			in.bind().sync();
		} catch (InterruptedException e) {
			throw new IOException(e);
		}

		if (LOG.isDebugEnabled()) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					Date date = new Date();

					while (true) {
						try {
							Thread.sleep(DEBUG_PRINT_QUEUED_ENVELOPES_EVERY_MS);

							date.setTime(System.currentTimeMillis());

							System.out.println(date);
							System.out.println(getNonZeroNumQueuedEnvelopes());
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}).start();
		}
	}

	@Override
	public void enqueue(Envelope envelope, RemoteReceiver receiver) throws IOException {
		// Get the channel. The channel may be
		// 1) a channel that already exists (usual case) -> just send the data
		// 2) a channel that is in buildup (sometimes) -> attach to the future and wait for the actual channel
		// 3) not yet existing -> establish the channel

		final Object entry = outConnections.get(receiver);
		final OutboundConnectionQueue channel;

		if (entry != null) {
			// existing channel or channel in buildup
			if (entry instanceof OutboundConnectionQueue) {
				channel = (OutboundConnectionQueue) entry;
			}
			else {
				ChannelInBuildup future = (ChannelInBuildup) entry;
				channel = future.waitForChannel();
			}
		}
		else {
			// No channel yet. Create one, but watch out for a race.
			// We create a "buildup future" and atomically add it to the map.
			// Only the thread that really added it establishes the channel.
			// The others need to wait on that original establisher's future.
			ChannelInBuildup inBuildup = new ChannelInBuildup(out, receiver, this, closeAfterIdleForMs);
			Object old = outConnections.putIfAbsent(receiver, inBuildup);

			if (old == null) {
				out.connect(receiver.getConnectionAddress()).addListener(inBuildup);
				channel = inBuildup.waitForChannel();

				Object previous = outConnections.put(receiver, channel);

				if (inBuildup != previous) {
					throw new IOException("Race condition during channel build up.");
				}
			}
			else if (old instanceof ChannelInBuildup) {
				channel = ((ChannelInBuildup) old).waitForChannel();
			}
			else {
				channel = (OutboundConnectionQueue) old;
			}
		}

		if (!channel.enqueue(envelope)) {
			// The channel has been closed, try again.
			LOG.debug("Retry enqueue on channel: " + channel + ".");

			// This will either establish a new connection or use the
			// one, which has been established in the mean time.
			enqueue(envelope, receiver);
		}
	}

	@Override
	public void close(RemoteReceiver receiver) {
		outConnections.remove(receiver);
	}

	@Override
	public void shutdown() throws IOException {
		if (!in.group().isShuttingDown()) {
			LOG.info("Shutting down incoming connections.");

			try {
				in.group().shutdownGracefully().sync();
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
		}

		if (!out.group().isShuttingDown()) {
			LOG.info("Shutting down outgoing connections.");

			try {
				out.group().shutdownGracefully().sync();
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
		}
	}

	private String getNonZeroNumQueuedEnvelopes() {
		StringBuilder str = new StringBuilder();

		str.append(String.format("==== %d outgoing connections ===\n", outConnections.size()));

		for (Map.Entry<RemoteReceiver, Object> entry : outConnections.entrySet()) {
			RemoteReceiver receiver = entry.getKey();

			Object value = entry.getValue();
			if (value instanceof OutboundConnectionQueue) {
				OutboundConnectionQueue queue = (OutboundConnectionQueue) value;
				if (queue.getNumQueuedEnvelopes() > 0) {
					str.append(String.format("%s> Number of queued envelopes for %s with channel %s: %d\n",
							Thread.currentThread().getId(), receiver, queue.toString(), queue.getNumQueuedEnvelopes()));
				}
			}
			else if (value instanceof ChannelInBuildup) {
				str.append(String.format("%s> Connection to %s is still in buildup\n",
						Thread.currentThread().getId(), receiver));
			}
		}

		return str.toString();
	}

	// ------------------------------------------------------------------------

	private static final class ChannelInBuildup implements ChannelFutureListener {

		private final Object lock = new Object();

		private volatile OutboundConnectionQueue channel;

		private volatile Throwable error;

		private int numRetries = 3;

		private final Bootstrap out;

		private final RemoteReceiver receiver;

		private final NetworkConnectionManager connectionManager;

		private final int closeAfterIdleMs;

		private ChannelInBuildup(
				Bootstrap out,
				RemoteReceiver receiver,
				NetworkConnectionManager connectionManager,
				int closeAfterIdleMs) {

			this.out = out;
			this.receiver = receiver;
			this.connectionManager = connectionManager;
			this.closeAfterIdleMs = closeAfterIdleMs;
		}

		private void handInChannel(OutboundConnectionQueue c) {
			synchronized (lock) {
				channel = c;
				lock.notifyAll();
			}
		}

		private void notifyOfError(Throwable t) {
			synchronized (lock) {
				error = t;
				lock.notifyAll();
			}
		}

		private OutboundConnectionQueue waitForChannel() throws IOException {
			synchronized (lock) {
				while (error == null && channel == null) {
					try {
						lock.wait(2000);
					} catch (InterruptedException e) {
						throw new RuntimeException("Channel buildup interrupted.");
					}
				}
			}

			if (error != null) {
				throw new IOException("Connecting the channel failed: " + error.getMessage(), error);
			}

			return channel;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (future.isSuccess()) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Channel %s connected", future.channel()));
				}

				handInChannel(new OutboundConnectionQueue(
						future.channel(), receiver, connectionManager, closeAfterIdleMs));
			}
			else if (numRetries > 0) {
				LOG.debug(String.format("Connection request did not succeed, retrying (%d attempts left)", numRetries));

				out.connect(receiver.getConnectionAddress()).addListener(this);
				numRetries--;
			}
			else {
				if (future.getClass() != null) {
					notifyOfError(future.cause());
				}
				else {
					notifyOfError(new Exception("Connection could not be established."));
				}
			}
		}
	}
}
