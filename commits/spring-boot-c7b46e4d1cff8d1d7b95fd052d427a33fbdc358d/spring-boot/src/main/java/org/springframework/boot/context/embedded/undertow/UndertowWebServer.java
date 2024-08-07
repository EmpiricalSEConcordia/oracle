/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.embedded.undertow;

import java.lang.reflect.Field;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import io.undertow.Undertow;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xnio.channels.BoundChannel;

import org.springframework.boot.context.embedded.EmbeddedWebServer;
import org.springframework.boot.context.embedded.EmbeddedWebServerException;
import org.springframework.boot.context.embedded.PortInUseException;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link EmbeddedWebServer} that can be used to control a Jetty web server. Usually this
 * class should be created using the {@link UndertowReactiveWebServerFactory} and not
 * directly.
 *
 * @author Ivan Sopov
 * @author Andy Wilkinson
 * @author Eddú Meléndez
 * @author Christoph Dreis
 * @author Brian Clozel
 */
public class UndertowWebServer implements EmbeddedWebServer {

	private static final Log logger = LogFactory
			.getLog(UndertowEmbeddedServletContainer.class);

	private final Object monitor = new Object();

	private final Undertow.Builder builder;

	private final boolean autoStart;

	private Undertow undertow;

	private volatile boolean started = false;

	/**
	 * Create a new {@link UndertowWebServer} instance.
	 * @param builder the builder
	 * @param autoStart if the server should be started
	 */
	public UndertowWebServer(Undertow.Builder builder, boolean autoStart) {
		this.builder = builder;
		this.autoStart = autoStart;
	}

	@Override
	public void start() throws EmbeddedWebServerException {
		synchronized (this.monitor) {
			if (this.started) {
				return;
			}
			try {
				if (!this.autoStart) {
					return;
				}
				if (this.undertow == null) {
					this.undertow = this.builder.build();
				}
				this.undertow.start();
				this.started = true;
				UndertowWebServer.logger
						.info("Undertow started on port(s) " + getPortsDescription());
			}
			catch (Exception ex) {
				if (findBindException(ex) != null) {
					List<UndertowWebServer.Port> failedPorts = getConfiguredPorts();
					List<UndertowWebServer.Port> actualPorts = getActualPorts();
					failedPorts.removeAll(actualPorts);
					if (failedPorts.size() == 1) {
						throw new PortInUseException(
								failedPorts.iterator().next().getNumber());
					}
				}
				throw new EmbeddedWebServerException("Unable to start embedded Undertow",
						ex);
			}
		}
	}

	private BindException findBindException(Exception ex) {
		Throwable candidate = ex;
		while (candidate != null) {
			if (candidate instanceof BindException) {
				return (BindException) candidate;
			}
			candidate = candidate.getCause();
		}
		return null;
	}

	private String getPortsDescription() {
		List<UndertowWebServer.Port> ports = getActualPorts();
		if (!ports.isEmpty()) {
			return StringUtils.collectionToDelimitedString(ports, " ");
		}
		return "unknown";
	}

	private List<UndertowWebServer.Port> getActualPorts() {
		List<UndertowWebServer.Port> ports = new ArrayList<UndertowWebServer.Port>();
		try {
			if (!this.autoStart) {
				ports.add(new UndertowWebServer.Port(-1, "unknown"));
			}
			else {
				for (BoundChannel channel : extractChannels()) {
					ports.add(getPortFromChannel(channel));
				}
			}
		}
		catch (Exception ex) {
			// Continue
		}
		return ports;
	}

	@SuppressWarnings("unchecked")
	private List<BoundChannel> extractChannels() {
		Field channelsField = ReflectionUtils.findField(Undertow.class, "channels");
		ReflectionUtils.makeAccessible(channelsField);
		return (List<BoundChannel>) ReflectionUtils.getField(channelsField,
				this.undertow);
	}

	private UndertowWebServer.Port getPortFromChannel(BoundChannel channel) {
		SocketAddress socketAddress = channel.getLocalAddress();
		if (socketAddress instanceof InetSocketAddress) {
			String protocol = ReflectionUtils.findField(channel.getClass(), "ssl") != null
					? "https" : "http";
			return new UndertowWebServer.Port(
					((InetSocketAddress) socketAddress).getPort(), protocol);
		}
		return null;
	}

	private List<UndertowWebServer.Port> getConfiguredPorts() {
		List<UndertowWebServer.Port> ports = new ArrayList<UndertowWebServer.Port>();
		for (Object listener : extractListeners()) {
			try {
				ports.add(getPortFromListener(listener));
			}
			catch (Exception ex) {
				// Continue
			}
		}
		return ports;
	}

	@SuppressWarnings("unchecked")
	private List<Object> extractListeners() {
		Field listenersField = ReflectionUtils.findField(Undertow.class, "listeners");
		ReflectionUtils.makeAccessible(listenersField);
		return (List<Object>) ReflectionUtils.getField(listenersField, this.undertow);
	}

	private UndertowWebServer.Port getPortFromListener(Object listener) {
		Field typeField = ReflectionUtils.findField(listener.getClass(), "type");
		ReflectionUtils.makeAccessible(typeField);
		String protocol = ReflectionUtils.getField(typeField, listener).toString();
		Field portField = ReflectionUtils.findField(listener.getClass(), "port");
		ReflectionUtils.makeAccessible(portField);
		int port = (Integer) ReflectionUtils.getField(portField, listener);
		return new UndertowWebServer.Port(port, protocol);
	}

	@Override
	public void stop() throws EmbeddedWebServerException {
		synchronized (this.monitor) {
			if (!this.started) {
				return;
			}
			this.started = false;
			try {
				this.undertow.stop();
			}
			catch (Exception ex) {
				throw new EmbeddedWebServerException("Unable to stop undertow", ex);
			}
		}
	}

	@Override
	public int getPort() {
		List<UndertowWebServer.Port> ports = getActualPorts();
		if (ports.isEmpty()) {
			return 0;
		}
		return ports.get(0).getNumber();
	}

	/**
	 * An active Undertow port.
	 */
	private final static class Port {

		private final int number;

		private final String protocol;

		private Port(int number, String protocol) {
			this.number = number;
			this.protocol = protocol;
		}

		public int getNumber() {
			return this.number;
		}

		@Override
		public String toString() {
			return this.number + " (" + this.protocol + ")";
		}

		@Override
		public int hashCode() {
			return this.number;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			UndertowWebServer.Port other = (UndertowWebServer.Port) obj;
			if (this.number != other.number) {
				return false;
			}
			return true;
		}

	}

}
