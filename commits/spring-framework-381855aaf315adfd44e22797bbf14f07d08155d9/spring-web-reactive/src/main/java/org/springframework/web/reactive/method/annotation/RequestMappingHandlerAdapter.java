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

package org.springframework.web.reactive.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.UnpooledByteBufAllocator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.support.ByteBufferDecoder;
import org.springframework.core.codec.support.JacksonJsonDecoder;
import org.springframework.core.codec.support.JsonObjectDecoder;
import org.springframework.core.codec.support.StringDecoder;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.buffer.DataBufferAllocator;
import org.springframework.core.io.buffer.NettyDataBufferAllocator;
import org.springframework.util.ObjectUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.reactive.HandlerAdapter;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.method.HandlerMethodArgumentResolver;
import org.springframework.web.reactive.method.InvocableHandlerMethod;
import org.springframework.web.server.ServerWebExchange;


/**
 * @author Rossen Stoyanchev
 */
public class RequestMappingHandlerAdapter implements HandlerAdapter, InitializingBean {

	private static Log logger = LogFactory.getLog(RequestMappingHandlerAdapter.class);


	private final List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();

	private ConversionService conversionService = new DefaultConversionService();

	private DataBufferAllocator allocator =
			new NettyDataBufferAllocator(new UnpooledByteBufAllocator(false));

	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache =
			new ConcurrentHashMap<Class<?>, ExceptionHandlerMethodResolver>(64);


	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		this.argumentResolvers.clear();
		this.argumentResolvers.addAll(resolvers);
	}

	/**
	 * Return the configured argument resolvers.
	 */
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return this.argumentResolvers;
	}

	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	public ConversionService getConversionService() {
		return this.conversionService;
	}

	public void setAllocator(DataBufferAllocator allocator) {
		this.allocator = allocator;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (ObjectUtils.isEmpty(this.argumentResolvers)) {

			List<Decoder<?>> decoders = Arrays.asList(new ByteBufferDecoder(),
					new StringDecoder(allocator),
					new JacksonJsonDecoder(new JsonObjectDecoder(allocator)));

			this.argumentResolvers.add(new RequestParamArgumentResolver());
			this.argumentResolvers.add(new RequestBodyArgumentResolver(decoders, this.conversionService));
		}
	}

	@Override
	public boolean supports(Object handler) {
		return HandlerMethod.class.equals(handler.getClass());
	}

	@Override
	public Mono<HandlerResult> handle(ServerWebExchange exchange, Object handler) {
		HandlerMethod handlerMethod = (HandlerMethod) handler;
		InvocableHandlerMethod invocable = new InvocableHandlerMethod(handlerMethod);
		invocable.setHandlerMethodArgumentResolvers(this.argumentResolvers);

		return invocable.invokeForRequest(exchange)
				.map(result -> result.setExceptionHandler(ex -> handleException(ex, handlerMethod, exchange)))
				.otherwise(ex -> handleException(ex, handlerMethod, exchange));
	}

	private Mono<HandlerResult> handleException(Throwable ex, HandlerMethod handlerMethod,
			ServerWebExchange exchange) {

		if (ex instanceof Exception) {
			InvocableHandlerMethod invocable = findExceptionHandler(handlerMethod, (Exception) ex);
			if (invocable != null) {
				try {
					if (logger.isDebugEnabled()) {
						logger.debug("Invoking @ExceptionHandler method: " + invocable);
					}
					invocable.setHandlerMethodArgumentResolvers(getArgumentResolvers());
					return invocable.invokeForRequest(exchange, ex);
				}
				catch (Exception invocationEx) {
					if (logger.isErrorEnabled()) {
						logger.error("Failed to invoke @ExceptionHandler method: " + invocable, invocationEx);
					}
				}
			}
		}
		return Mono.error(ex);
	}

	protected InvocableHandlerMethod findExceptionHandler(HandlerMethod handlerMethod, Exception exception) {
		if (handlerMethod == null) {
			return null;
		}
		Class<?> handlerType = handlerMethod.getBeanType();
		ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache.get(handlerType);
		if (resolver == null) {
			resolver = new ExceptionHandlerMethodResolver(handlerType);
			this.exceptionHandlerCache.put(handlerType, resolver);
		}
		Method method = resolver.resolveMethod(exception);
		return (method != null ? new InvocableHandlerMethod(handlerMethod.getBean(), method) : null);
	}

}