/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.launcher;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.gen5.commons.util.Preconditions;
import org.junit.gen5.engine.DiscoveryFilter;
import org.junit.gen5.engine.DiscoverySelector;
import org.junit.gen5.engine.EngineDiscoveryRequest;
import org.junit.gen5.engine.FilterResult;
import org.junit.gen5.engine.TestDescriptor;
import org.junit.gen5.engine.TestEngine;

/**
 * The {@code DiscoveryRequest} represents the configuration for the test
 * discovery and execution. It is passed to every active {@link TestEngine}
 * and should be used to lookup tests for the given configuration.
 *
 * <p>A {@code DiscoveryRequest} contains different configuration options.
 *
 * <ul>
 * <li>{@link DiscoverySelector}: A selector defines location(s) a
 * {@link TestEngine} should lookup tests at</li>
 * <li>{@link EngineIdFilter}: A special filter that is applied before an
 * {@link TestEngine} is executed</li>
 * <li>{@link DiscoveryFilter}: A filter that should be applied by the
 * {@link TestEngine} during test discovery.</li>
 * <li>{@link PostDiscoveryFilter}: A filter that will be applied after
 * test discovery.</li>
 * </ul>
 *
 * @since 5.0
 * @see DiscoverySelector
 * @see EngineIdFilter
 * @see DiscoveryFilter
 * @see PostDiscoveryFilter
 */
public final class DiscoveryRequest implements EngineDiscoveryRequest {
	// Selectors provided to the engines to be used for finding tests
	private final List<DiscoverySelector> selectors = new LinkedList<>();

	// Filter based on the engine id
	private final List<EngineIdFilter> engineIdFilters = new LinkedList<>();

	// Discovery filters are handed through to all test engines to be applied during discovery
	private final List<DiscoveryFilter<?>> discoveryFilters = new LinkedList<>();

	// Descriptor Filters are evaluated by the launcher itself after engines have done their discovery.
	private final List<PostDiscoveryFilter> postDiscoveryFilters = new LinkedList<>();

	public void addSelector(DiscoverySelector selector) {
		this.selectors.add(selector);
	}

	public void addSelectors(Collection<DiscoverySelector> selectors) {
		selectors.forEach(this::addSelector);
	}

	public void addEngineIdFilter(EngineIdFilter engineIdFilter) {
		this.engineIdFilters.add(engineIdFilter);
	}

	public void addEngineIdFilters(Collection<EngineIdFilter> engineIdFilters) {
		this.engineIdFilters.addAll(engineIdFilters);
	}

	public void addFilter(DiscoveryFilter<?> discoveryFilter) {
		this.discoveryFilters.add(discoveryFilter);
	}

	public void addFilters(Collection<DiscoveryFilter<?>> discoveryFilters) {
		this.discoveryFilters.addAll(discoveryFilters);
	}

	public void addPostFilter(PostDiscoveryFilter postDiscoveryFilter) {
		this.postDiscoveryFilters.add(postDiscoveryFilter);
	}

	public void addPostFilters(Collection<PostDiscoveryFilter> postDiscoveryFilters) {
		this.postDiscoveryFilters.addAll(postDiscoveryFilters);
	}

	@Override
	public List<DiscoverySelector> getSelectors() {
		return unmodifiableList(this.selectors);
	}

	@Override
	public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> selectorType) {
		return this.selectors.stream().filter(selectorType::isInstance).map(selectorType::cast).collect(toList());
	}

	public List<EngineIdFilter> getEngineIdFilters() {
		return unmodifiableList(this.engineIdFilters);
	}

	@Override
	public List<DiscoveryFilter<?>> getDiscoveryFilters() {
		return unmodifiableList(this.discoveryFilters);
	}

	@Override
	public <T extends DiscoveryFilter<?>> List<T> getDiscoveryFiltersByType(Class<T> filterType) {
		return this.discoveryFilters.stream().filter(filterType::isInstance).map(filterType::cast).collect(toList());
	}

	public List<PostDiscoveryFilter> getPostDiscoveryFilters() {
		return unmodifiableList(this.postDiscoveryFilters);
	}

	public boolean acceptDescriptor(TestDescriptor testDescriptor) {
		Preconditions.notNull(testDescriptor, "testDescriptor must not be null");

		// @formatter:off
		return this.getPostDiscoveryFilters().stream()
				.map(filter -> filter.filter(testDescriptor))
				.allMatch(FilterResult::included);
		// @formatter:on
	}

}
