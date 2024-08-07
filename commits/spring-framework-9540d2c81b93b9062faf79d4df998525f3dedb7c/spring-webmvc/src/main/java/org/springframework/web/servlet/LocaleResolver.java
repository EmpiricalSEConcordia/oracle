/*
 * Copyright 2002-2006 the original author or authors.
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

package org.springframework.web.servlet;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Interface for web-based locale resolution strategies that allows for
 * both locale resolution via the request and locale modification via
 * request and response.
 *
 * <p>This interface allows for implementations based on request, session,
 * cookies, etc. The default implementation is AcceptHeaderLocaleResolver,
 * simply using the request's locale provided by the respective HTTP header.
 *
 * <p>Use {@code RequestContext.getLocale()} to retrieve the current locale
 * in controllers or views, independent of the actual resolution strategy.
 *
 * @author Juergen Hoeller
 * @since 27.02.2003
 * @see org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
 * @see org.springframework.web.servlet.support.RequestContext#getLocale
 */
public interface LocaleResolver {

  /**
   * Resolve the current locale via the given request.
   * Should return a default locale as fallback in any case.
   * @param request the request to resolve the locale for
   * @return the current locale (never {@code null})
   */
	Locale resolveLocale(HttpServletRequest request);

  /**
   * Set the current locale to the given one.
   * @param request the request to be used for locale modification
   * @param response the response to be used for locale modification
   * @param locale the new locale, or {@code null} to clear the locale
   * @throws UnsupportedOperationException if the LocaleResolver implementation
   * does not support dynamic changing of the theme
   */
	void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale);

}
