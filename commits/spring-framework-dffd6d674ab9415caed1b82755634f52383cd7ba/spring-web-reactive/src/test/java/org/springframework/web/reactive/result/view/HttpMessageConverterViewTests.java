/*
 * Copyright 2002-2016 the original author or authors.
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
package org.springframework.web.reactive.result.view;


import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import reactor.core.test.TestSubscriber;

import org.springframework.core.ResolvableType;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.http.codec.xml.Jaxb2Encoder;
import org.springframework.core.codec.StringEncoder;
import org.springframework.core.io.buffer.support.DataBufferTestUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.MockServerHttpRequest;
import org.springframework.http.server.reactive.MockServerHttpResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.ModelMap;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.WebSessionManager;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;


/**
 * Unit tests for {@link HttpMessageConverterView}.
 * @author Rossen Stoyanchev
 */
public class HttpMessageConverterViewTests {

	private HttpMessageConverterView view;

	private HandlerResult result;

	private ModelMap model;


	@Before
	public void setup() throws Exception {
		this.view = new HttpMessageConverterView(new JacksonJsonEncoder());
		this.model = new ExtendedModelMap();
		this.result = new HandlerResult(new Object(), null, ResolvableType.NONE, model);
	}


	@Test
	public void supportedMediaTypes() throws Exception {
		List<MimeType> mimeTypes = Arrays.asList(
				new MimeType("application", "json", StandardCharsets.UTF_8),
				new MimeType("application", "*+json", StandardCharsets.UTF_8));

		assertEquals(mimeTypes, this.view.getSupportedMediaTypes());
	}

	@Test
	public void extractObject() throws Exception {
		this.view.setModelKeys(Collections.singleton("foo2"));
		this.model.addAttribute("foo1", "bar1");
		this.model.addAttribute("foo2", "bar2");
		this.model.addAttribute("foo3", "bar3");

		assertEquals("bar2", this.view.extractObjectToRender(this.result));
	}

	@Test
	public void extractObjectNoMatch() throws Exception {
		this.view.setModelKeys(Collections.singleton("foo2"));
		this.model.addAttribute("foo1", "bar1");

		assertNull(this.view.extractObjectToRender(this.result));
	}

	@Test
	public void extractObjectMultipleMatches() throws Exception {
		this.view.setModelKeys(new HashSet<>(Arrays.asList("foo1", "foo2")));
		this.model.addAttribute("foo1", "bar1");
		this.model.addAttribute("foo2", "bar2");
		this.model.addAttribute("foo3", "bar3");

		Object value = this.view.extractObjectToRender(this.result);
		assertNotNull(value);
		assertEquals(HashMap.class, value.getClass());

		Map<?, ?> map = (Map<?, ?>) value;
		assertEquals(2, map.size());
		assertEquals("bar1", map.get("foo1"));
		assertEquals("bar2", map.get("foo2"));
	}

	@Test
	public void extractObjectMultipleMatchesNotSupported() throws Exception {
		HttpMessageConverterView view = new HttpMessageConverterView(new StringEncoder());
		view.setModelKeys(new HashSet<>(Arrays.asList("foo1", "foo2")));
		this.model.addAttribute("foo1", "bar1");
		this.model.addAttribute("foo2", "bar2");

		try {
			view.extractObjectToRender(this.result);
			fail();
		}
		catch (IllegalStateException ex) {
			String message = ex.getMessage();
			assertTrue(message, message.contains("Map rendering is not supported"));
		}
	}

	@Test
	public void extractObjectNotSupported() throws Exception {
		HttpMessageConverterView view = new HttpMessageConverterView(new Jaxb2Encoder());
		view.setModelKeys(new HashSet<>(Collections.singletonList("foo1")));
		this.model.addAttribute("foo1", "bar1");

		try {
			view.extractObjectToRender(this.result);
			fail();
		}
		catch (IllegalStateException ex) {
			String message = ex.getMessage();
			assertTrue(message, message.contains("[foo1] is not supported"));
		}
	}

	@Test
	public void render() throws Exception {
		Map<String, String> pojoData = new LinkedHashMap<>();
		pojoData.put("foo", "f");
		pojoData.put("bar", "b");
		this.model.addAttribute("pojoData", pojoData);
		this.view.setModelKeys(Collections.singleton("pojoData"));

		MockServerHttpRequest request = new MockServerHttpRequest(HttpMethod.GET, new URI("/path"));
		MockServerHttpResponse response = new MockServerHttpResponse();
		WebSessionManager manager = new DefaultWebSessionManager();
		ServerWebExchange exchange = new DefaultServerWebExchange(request, response, manager);

		this.view.render(result, MediaType.APPLICATION_JSON, exchange);

		TestSubscriber
				.subscribe(response.getBody())
				.assertValuesWith(buf -> assertEquals("{\"foo\":\"f\",\"bar\":\"b\"}",
						DataBufferTestUtils.dumpString(buf, Charset.forName("UTF-8"))));
	}


}
