/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.embedded.jetty;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.boot.context.embedded.AbstractEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.AbstractEmbeddedServletContainerFactoryTests;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.boot.context.embedded.Ssl;
import org.springframework.boot.context.embedded.AbstractConfigurableEmbeddedServletContainer.CompressionProperties;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link JettyEmbeddedServletContainerFactory} and
 * {@link JettyEmbeddedServletContainer}.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 */
public class JettyEmbeddedServletContainerFactoryTests extends
		AbstractEmbeddedServletContainerFactoryTests {

	@Override
	protected JettyEmbeddedServletContainerFactory getFactory() {
		return new JettyEmbeddedServletContainerFactory(0);
	}

	@Test
	public void jettyConfigurations() throws Exception {
		JettyEmbeddedServletContainerFactory factory = getFactory();
		Configuration[] configurations = new Configuration[4];
		for (int i = 0; i < configurations.length; i++) {
			configurations[i] = mock(Configuration.class);
		}
		factory.setConfigurations(Arrays.asList(configurations[0], configurations[1]));
		factory.addConfigurations(configurations[2], configurations[3]);
		this.container = factory.getEmbeddedServletContainer();
		InOrder ordered = inOrder((Object[]) configurations);
		for (Configuration configuration : configurations) {
			ordered.verify(configuration).configure((WebAppContext) anyObject());
		}
	}

	@Test
	public void jettyCustomizations() throws Exception {
		JettyEmbeddedServletContainerFactory factory = getFactory();
		JettyServerCustomizer[] configurations = new JettyServerCustomizer[4];
		for (int i = 0; i < configurations.length; i++) {
			configurations[i] = mock(JettyServerCustomizer.class);
		}
		factory.setServerCustomizers(Arrays.asList(configurations[0], configurations[1]));
		factory.addServerCustomizers(configurations[2], configurations[3]);
		this.container = factory.getEmbeddedServletContainer();
		InOrder ordered = inOrder((Object[]) configurations);
		for (JettyServerCustomizer configuration : configurations) {
			ordered.verify(configuration).customize((Server) anyObject());
		}
	}

	@Test
	public void sessionTimeout() throws Exception {
		JettyEmbeddedServletContainerFactory factory = getFactory();
		factory.setSessionTimeout(10);
		assertTimeout(factory, 10);
	}

	@Test
	public void sessionTimeoutInMins() throws Exception {
		JettyEmbeddedServletContainerFactory factory = getFactory();
		factory.setSessionTimeout(1, TimeUnit.MINUTES);
		assertTimeout(factory, 60);
	}

	@Test
	public void sslCiphersConfiguration() throws Exception {
		Ssl ssl = new Ssl();
		ssl.setKeyStore("src/test/resources/test.jks");
		ssl.setKeyStorePassword("secret");
		ssl.setKeyPassword("password");
		ssl.setCiphers(new String[] { "ALPHA", "BRAVO", "CHARLIE" });

		JettyEmbeddedServletContainerFactory factory = getFactory();
		factory.setSsl(ssl);

		this.container = factory.getEmbeddedServletContainer();
		this.container.start();

		JettyEmbeddedServletContainer jettyContainer = (JettyEmbeddedServletContainer) this.container;
		ServerConnector connector = (ServerConnector) jettyContainer.getServer()
				.getConnectors()[0];
		SslConnectionFactory connectionFactory = connector
				.getConnectionFactory(SslConnectionFactory.class);
		assertThat(connectionFactory.getSslContextFactory().getIncludeCipherSuites(),
				equalTo(new String[] { "ALPHA", "BRAVO", "CHARLIE" }));
	}

	private void assertTimeout(JettyEmbeddedServletContainerFactory factory, int expected) {
		this.container = factory.getEmbeddedServletContainer();
		JettyEmbeddedServletContainer jettyContainer = (JettyEmbeddedServletContainer) this.container;
		Handler[] handlers = jettyContainer.getServer().getChildHandlersByClass(
				WebAppContext.class);
		WebAppContext webAppContext = (WebAppContext) handlers[0];
		int actual = webAppContext.getSessionHandler().getSessionManager()
				.getMaxInactiveInterval();
		assertThat(actual, equalTo(expected));
	}

	@Test
	public void wrappedHandlers() throws Exception {
		JettyEmbeddedServletContainerFactory factory = getFactory();
		factory.setServerCustomizers(Arrays.asList(new JettyServerCustomizer() {
			@Override
			public void customize(Server server) {
				Handler handler = server.getHandler();
				HandlerWrapper wrapper = new HandlerWrapper();
				wrapper.setHandler(handler);
				HandlerCollection collection = new HandlerCollection();
				collection.addHandler(wrapper);
				server.setHandler(collection);
			}
		}));
		this.container = factory
				.getEmbeddedServletContainer(exampleServletRegistration());
		this.container.start();
		assertThat(getResponse(getLocalUrl("/hello")), equalTo("Hello World"));
	}

	@Test
	public void basicSslClasspathKeyStore() throws Exception {
		testBasicSslWithKeyStore("classpath:test.jks");
	}

	@Test
	public void jspServletInitParameters() {
		JettyEmbeddedServletContainerFactory factory = getFactory();
		Map<String, String> initParameters = new HashMap<String, String>();
		initParameters.put("a", "alpha");
		factory.getJspServlet().setInitParameters(initParameters);
		this.container = factory.getEmbeddedServletContainer();
		assertThat(getJspServlet().getInitParameters(), is(equalTo(initParameters)));
	}

	@Override
	@SuppressWarnings("serial")
	// work-around for Jetty issue - https://bugs.eclipse.org/bugs/show_bug.cgi?id=470646
	protected String setupFactoryForCompression(final int contentSize, String mimeTypes)
			throws Exception {
		char[] chars = new char[contentSize];
		Arrays.fill(chars, 'F');
		final String testContent = new String(chars);

		AbstractEmbeddedServletContainerFactory factory = getFactory();

		CompressionProperties compression = new CompressionProperties();
		compression.setEnabled(true);
		if (mimeTypes != null) {
			compression.setMimeTypes(mimeTypes);
		}
		factory.setCompression(compression);

		this.container = factory.getEmbeddedServletContainer(new ServletRegistrationBean(
				new HttpServlet() {
					@Override
					protected void doGet(HttpServletRequest req, HttpServletResponse resp)
							throws ServletException, IOException {
						resp.setContentLength(contentSize);
						resp.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
						resp.getWriter().print(testContent);
					}
				}, "/test.txt"));
		this.container.start();
		return testContent;
	}

	@Override
	protected ServletHolder getJspServlet() {
		WebAppContext context = (WebAppContext) ((JettyEmbeddedServletContainer) this.container)
				.getServer().getHandler();
		return context.getServletHandler().getServlet("jsp");
	}

}
