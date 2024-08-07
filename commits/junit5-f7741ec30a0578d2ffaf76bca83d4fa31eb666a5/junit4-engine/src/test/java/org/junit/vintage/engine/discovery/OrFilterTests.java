/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.vintage.engine.discovery;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.junit.gen5.api.Assertions.assertEquals;
import static org.junit.gen5.api.Assertions.assertTrue;
import static org.junit.gen5.api.Assertions.expectThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.gen5.api.Test;
import org.junit.gen5.commons.util.PreconditionViolationException;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

/**
 * @since 5.0
 */
class OrFilterTests {

	@Test
	void exceptionWithoutAnyFilters() {
		PreconditionViolationException actual = expectThrows(PreconditionViolationException.class, () -> {
			new OrFilter(emptyList());
		});
		assertEquals("filters must not be empty", actual.getMessage());
	}

	@Test
	void evaluatesSingleFilter() {
		Filter filter = mockFilter("foo", true);

		OrFilter orFilter = new OrFilter(singleton(filter));

		assertEquals("foo", orFilter.describe());

		Description description = Description.createTestDescription(getClass(), "evaluatesSingleFilter");
		assertTrue(orFilter.shouldRun(description));

		verify(filter).shouldRun(same(description));
	}

	@Test
	void evaluatesMultipleFilters() {
		Filter filter1 = mockFilter("foo", false);
		Filter filter2 = mockFilter("bar", true);

		OrFilter orFilter = new OrFilter(asList(filter1, filter2));

		assertEquals("foo OR bar", orFilter.describe());

		Description description = Description.createTestDescription(getClass(), "evaluatesMultipleFilters");
		assertTrue(orFilter.shouldRun(description));

		verify(filter1).shouldRun(same(description));
		verify(filter2).shouldRun(same(description));
	}

	private Filter mockFilter(String description, boolean result) {
		Filter filter = mock(Filter.class);
		when(filter.describe()).thenReturn(description);
		when(filter.shouldRun(any())).thenReturn(result);
		return filter;
	}

}
