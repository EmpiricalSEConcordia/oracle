/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.http.server.reactive;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.test.MockServerHttpResponse;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link HttpHandler}.
 *
 * @author Rossen Stoyanchev
 */
public class HttpHandlerTests {

	@Test
	public void invalidContextPath() {
		testInvalidContextPath("  ", "Context path must not be empty");
		testInvalidContextPath("path", "Context path must begin with '/'");
		testInvalidContextPath("/path/", "Context path must not end with '/'");
	}

	private void testInvalidContextPath(String contextPath, String errorMessage) {
		try {
			new TestHttpHandlerAdapter(Collections.singletonMap(contextPath, new TestHttpHandler()));
			fail();
		}
		catch (IllegalArgumentException ex) {
			assertEquals(errorMessage, ex.getMessage());
		}
	}

	@Test
	public void match() {
		TestHttpHandler handler1 = new TestHttpHandler();
		TestHttpHandler handler2 = new TestHttpHandler();
		TestHttpHandler handler3 = new TestHttpHandler();

		Map<String, HttpHandler> map = new HashMap<>();
		map.put("/path", handler1);
		map.put("/another/path", handler2);
		map.put("/yet/another/path", handler3);

		testPath("/another/path/and/more", map);

		assertInvoked(handler2, "/another/path");
		assertNotInvoked(handler1, handler3);
	}

	@Test
	public void matchWithContextPathEqualToPath() {
		TestHttpHandler handler1 = new TestHttpHandler();
		TestHttpHandler handler2 = new TestHttpHandler();
		TestHttpHandler handler3 = new TestHttpHandler();

		Map<String, HttpHandler> map = new HashMap<>();
		map.put("/path", handler1);
		map.put("/another/path", handler2);
		map.put("/yet/another/path", handler3);

		testPath("/path", map);

		assertInvoked(handler1, "/path");
		assertNotInvoked(handler2, handler3);
	}

	@Test
	public void matchWithNativeContextPath() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("/yet/another/path")
				.contextPath("/yet")  // contextPath in underlying request
				.build();

		TestHttpHandler handler = new TestHttpHandler();
		Map<String, HttpHandler> map = Collections.singletonMap("/another/path", handler);

		new TestHttpHandlerAdapter(map).handle(request);

		assertTrue(handler.wasInvoked());
		assertEquals("/yet/another/path", handler.getRequest().getContextPath());
	}

	@Test
	public void notFound() throws Exception {
		TestHttpHandler handler1 = new TestHttpHandler();
		TestHttpHandler handler2 = new TestHttpHandler();

		Map<String, HttpHandler> map = new HashMap<>();
		map.put("/path", handler1);
		map.put("/another/path", handler2);

		ServerHttpResponse response = testPath("/yet/another/path", map);

		assertNotInvoked(handler1, handler2);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}


	private ServerHttpResponse testPath(String path, Map<String, HttpHandler> handlerMap) {
		TestHttpHandlerAdapter adapter = new TestHttpHandlerAdapter(handlerMap);
		return adapter.handle(path);
	}

	private void assertInvoked(TestHttpHandler handler, String contextPath) {
		assertTrue(handler.wasInvoked());
		assertEquals(contextPath, handler.getRequest().getContextPath());
	}

	private void assertNotInvoked(TestHttpHandler... handlers) {
		Arrays.stream(handlers).forEach(handler -> assertFalse(handler.wasInvoked()));
	}


	@SuppressWarnings("WeakerAccess")
	private static class TestHttpHandlerAdapter {


		private final HttpHandler httpHandler;


		public TestHttpHandlerAdapter(Map<String, HttpHandler> handlerMap) {
			this.httpHandler = HttpHandler.of(handlerMap);
		}

		public ServerHttpResponse handle(String path) {
			ServerHttpRequest request = MockServerHttpRequest.get(path).build();
			return handle(request);
		}

		public ServerHttpResponse handle(ServerHttpRequest request) {
			ServerHttpResponse response = new MockServerHttpResponse();
			this.httpHandler.handle(request, response);
			return response;
		}
	}


	@SuppressWarnings("WeakerAccess")
	private static class TestHttpHandler implements HttpHandler {

		private ServerHttpRequest request;

		public boolean wasInvoked() {
			return (this.request != null);
		}

		public ServerHttpRequest getRequest() {
			return this.request;
		}

		@Override
		public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
			this.request = request;
			return Mono.empty();
		}
	}

}
