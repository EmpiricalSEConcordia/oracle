/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.reactive.web.dispatch.method.annotation;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;
import reactor.Publishers;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ReactiveServerHttpRequest;
import org.springframework.reactive.codec.decoder.ByteToMessageDecoder;
import org.springframework.reactive.web.dispatch.method.HandlerMethodArgumentResolver;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author Sebastien Deleuze
 * @author Stephane Maldini
 */
public class RequestBodyArgumentResolver implements HandlerMethodArgumentResolver {

	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private final List<ByteToMessageDecoder<?>> deserializers;
	private final List<ByteToMessageDecoder<ByteBuffer>> preProcessors;
	private final ConversionService conversionService;


	public RequestBodyArgumentResolver(List<ByteToMessageDecoder<?>> deserializers,
			ConversionService conversionService) {
		this(deserializers, conversionService, Collections.EMPTY_LIST);
	}

	public RequestBodyArgumentResolver(List<ByteToMessageDecoder<?>> deserializers,
			ConversionService conversionService,
			List<ByteToMessageDecoder<ByteBuffer>> preProcessors) {
		this.deserializers = deserializers;
		this.conversionService = conversionService;
		this.preProcessors = preProcessors;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(RequestBody.class);
	}

	@Override
	public Publisher<Object> resolveArgument(MethodParameter parameter, ReactiveServerHttpRequest request) {
		MediaType mediaType = resolveMediaType(request);
		ResolvableType type = ResolvableType.forMethodParameter(parameter);
		List<Object> hints = new ArrayList<>();
		hints.add(UTF_8);
		Publisher<ByteBuffer> inputStream = request.getBody();
		Publisher<?> elementStream = inputStream;
		ResolvableType elementType = type.hasGenerics() ? type.getGeneric(0) : type;
		ByteToMessageDecoder<?> deserializer = resolveDeserializers(request, elementType, mediaType, hints.toArray());
		if (deserializer != null) {
			List<ByteToMessageDecoder<ByteBuffer>> preProcessors =
					resolvePreProcessors(request, elementType, mediaType,hints.toArray());
			for (ByteToMessageDecoder<ByteBuffer> preProcessor : preProcessors) {
				inputStream = preProcessor.decode(inputStream, elementType, mediaType, hints.toArray());
			}
			elementStream = deserializer.decode(inputStream, elementType, mediaType, hints.toArray());
		}
		if (this.conversionService.canConvert(Publisher.class, type.getRawClass())) {
			return Publishers.just(this.conversionService.convert(elementStream, type.getRawClass()));
		}
		return Publishers.map(elementStream, element -> element);
	}

	private MediaType resolveMediaType(ReactiveServerHttpRequest request) {
		String acceptHeader = request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
		List<MediaType> mediaTypes = MediaType.parseMediaTypes(acceptHeader);
		MediaType.sortBySpecificityAndQuality(mediaTypes);
		return ( mediaTypes.size() > 0 ? mediaTypes.get(0) : MediaType.TEXT_PLAIN);
	}

	private ByteToMessageDecoder<?> resolveDeserializers(ReactiveServerHttpRequest request, ResolvableType type,  MediaType mediaType, Object[] hints) {
		for (ByteToMessageDecoder<?> deserializer : this.deserializers) {
			if (deserializer.canDecode(type, mediaType, hints)) {
				return deserializer;
			}
		}
		return null;
	}

	private List<ByteToMessageDecoder<ByteBuffer>> resolvePreProcessors(ReactiveServerHttpRequest request, ResolvableType type, MediaType mediaType, Object[] hints) {
		List<ByteToMessageDecoder<ByteBuffer>> preProcessors = new ArrayList<>();
		for (ByteToMessageDecoder<ByteBuffer> preProcessor : this.preProcessors) {
			if (preProcessor.canDecode(type, mediaType, hints)) {
				preProcessors.add(preProcessor);
			}
		}
		return preProcessors;
	}
}
