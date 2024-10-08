/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.web.servlet.mvc.annotation;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.generic.GenericBeanFactoryAccessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.handler.AbstractDetectingUrlHandlerMapping;

/**
 * Implementation of the {@link org.springframework.web.servlet.HandlerMapping}
 * interface that maps handlers based on HTTP paths expressed through the
 * {@link RequestMapping} annotation at the type or method level.
 *
 * <p>Registered by default in {@link org.springframework.web.servlet.DispatcherServlet}
 * on Java 5+. <b>NOTE:</b> If you define custom HandlerMapping beans in your
 * DispatcherServlet context, you need to add a DefaultAnnotationHandlerMapping bean
 * explicitly, since custom HandlerMapping beans replace the default mapping strategies.
 * Defining a DefaultAnnotationHandlerMapping also allows for registering custom
 * interceptors:
 *
 * <pre class="code">
 * &lt;bean class="org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping"&gt;
 *   &lt;property name="interceptors"&gt;
 *     ...
 *   &lt;/property&gt;
 * &lt;/bean&gt;</pre>
 *
 * Annotated controllers are usually marked with the {@link Controller} stereotype
 * at the type level. This is not strictly necessary when {@link RequestMapping} is
 * applied at the type level (since such a handler usually implements the
 * {@link org.springframework.web.servlet.mvc.Controller} interface). However,
 * {@link Controller} is required for detecting {@link RequestMapping} annotations
 * at the method level if {@link RequestMapping} is not present at the type level.
 *
 * <p><b>NOTE:</b> Method-level mappings are only allowed to narrow the mapping
 * expressed at the class level (if any). HTTP paths need to uniquely map onto
 * specific handler beans, with any given HTTP path only allowed to be mapped
 * onto one specific handler bean (not spread across multiple handler beans).
 * It is strongly recommended to co-locate related handler methods into the same bean.
 *
 * <p>The {@link AnnotationMethodHandlerAdapter} is responsible for processing
 * annotated handler methods, as mapped by this HandlerMapping. For
 * {@link RequestMapping} at the type level, specific HandlerAdapters such as
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter} apply.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 2.5
 * @see RequestMapping
 * @see AnnotationMethodHandlerAdapter
 */
public class DefaultAnnotationHandlerMapping extends AbstractDetectingUrlHandlerMapping {

	private boolean useDefaultSuffixPattern = true;

	private final Map<Class, RequestMapping> cachedMappings = new HashMap<Class, RequestMapping>();


	/**
	 * Set whether to register paths using the default suffix pattern as well:
	 * i.e. whether "/users" should be registered as "/users.*" too.
	 * <p>Default is "true". Turn this convention off if you intend to interpret
	 * your <code>@RequestMapping</code> paths strictly.
	 * <p>Note that paths which include a ".xxx" suffix already will not be
	 * transformed using the default suffix pattern in any case.
	 */
	public void setUseDefaultSuffixPattern(boolean useDefaultSuffixPattern) {
		this.useDefaultSuffixPattern = useDefaultSuffixPattern;
	}


	/**
	 * Checks for presence of the {@link org.springframework.web.bind.annotation.RequestMapping}
	 * annotation on the handler class and on any of its methods.
	 */
	@Override
	protected String[] determineUrlsForHandler(String beanName) {
		ApplicationContext context = getApplicationContext();
		Class<?> handlerType = context.getType(beanName);
		ListableBeanFactory bf = (context instanceof ConfigurableApplicationContext ?
				((ConfigurableApplicationContext) context).getBeanFactory() : context);
		GenericBeanFactoryAccessor bfa = new GenericBeanFactoryAccessor(bf);
		RequestMapping mapping = bfa.findAnnotationOnBean(beanName, RequestMapping.class);

		if (mapping != null) {
			// @RequestMapping found at type level
			this.cachedMappings.put(handlerType, mapping);
			Set<String> urls = new LinkedHashSet<String>();
			String[] paths = mapping.value();
			if (paths.length > 0) {
				// @RequestMapping specifies paths at type level
				for (String path : paths) {
					addUrlsForPath(urls, path);
				}
				return StringUtils.toStringArray(urls);
			}
			else {
				// actual paths specified by @RequestMapping at method level
				return determineUrlsForHandlerMethods(handlerType);
			}
		}
		else if (AnnotationUtils.findAnnotation(handlerType, Controller.class) != null) {
			// @RequestMapping to be introspected at method level
			return determineUrlsForHandlerMethods(handlerType);
		}
		else {
			return null;
		}
	}

	/**
	 * Derive URL mappings from the handler's method-level mappings.
	 * @param handlerType the handler type to introspect
	 * @return the array of mapped URLs
	 */
	protected String[] determineUrlsForHandlerMethods(Class<?> handlerType) {
		final Set<String> urls = new LinkedHashSet<String>();
		ReflectionUtils.doWithMethods(handlerType, new ReflectionUtils.MethodCallback() {
			public void doWith(Method method) {
				RequestMapping mapping = method.getAnnotation(RequestMapping.class);
				if (mapping != null) {
					String[] mappedPaths = mapping.value();
					for (String mappedPath : mappedPaths) {
						addUrlsForPath(urls, mappedPath);
					}
				}
			}
		});
		return StringUtils.toStringArray(urls);
	}

	/**
	 * Add URLs and/or URL patterns for the given path.
	 * @param urls the Set of URLs for the current bean
	 * @param path the currently introspected path
	 */
	protected void addUrlsForPath(Set<String> urls, String path) {
		urls.add(path);
		if (this.useDefaultSuffixPattern && path.indexOf('.') == -1) {
			urls.add(path + ".*");
		}
	}


	/**
	 * Validate the given annotated handler against the current request.
	 * @see #validateMapping
	 */
	@Override
	protected void validateHandler(Object handler, HttpServletRequest request) throws Exception {
		RequestMapping mapping = this.cachedMappings.get(handler.getClass());
		if (mapping == null) {
			mapping = AnnotationUtils.findAnnotation(handler.getClass(), RequestMapping.class);
		}
		if (mapping != null) {
			validateMapping(mapping, request);
		}
	}

	/**
	 * Validate the given type-level mapping metadata against the current request,
	 * checking HTTP request method and parameter conditions.
	 * @param mapping the mapping metadata to validate
	 * @param request current HTTP request
	 * @throws Exception if validation failed
	 */
	protected void validateMapping(RequestMapping mapping, HttpServletRequest request) throws Exception {
		RequestMethod[] mappedMethods = mapping.method();
		if (!ServletAnnotationMappingUtils.checkRequestMethod(mappedMethods, request)) {
			String[] supportedMethods = new String[mappedMethods.length];
			for (int i = 0; i < mappedMethods.length; i++) {
				supportedMethods[i] = mappedMethods[i].name();
			}
			throw new HttpRequestMethodNotSupportedException(request.getMethod(), supportedMethods);
		}

		String[] mappedParams = mapping.params();
		if (!ServletAnnotationMappingUtils.checkParameters(mappedParams, request)) {
			throw new ServletException("Parameter conditions {" +
					StringUtils.arrayToDelimitedString(mappedParams, ", ") +
					"} not met for request parameters: " + request.getParameterMap());
		}
	}

	

}
