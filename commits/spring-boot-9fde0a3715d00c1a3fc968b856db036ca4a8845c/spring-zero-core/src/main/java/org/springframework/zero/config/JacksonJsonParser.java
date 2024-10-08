/*
 * Copyright 2012-2013 the original author or authors.
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

package org.springframework.zero.config;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin wrapper to adapt Jackson 2 {@link ObjectMapper} to {@link JsonParser}.
 * 
 * @author Dave Syer
 * @see JsonParserFactory
 */
public class JacksonJsonParser implements JsonParser {

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> parseMap(String json) {
		try {
			return new ObjectMapper().readValue(json, Map.class);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Cannot parse JSON", ex);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Object> parseList(String json) {
		try {
			return new ObjectMapper().readValue(json, List.class);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Cannot parse JSON", ex);
		}
	}

}
