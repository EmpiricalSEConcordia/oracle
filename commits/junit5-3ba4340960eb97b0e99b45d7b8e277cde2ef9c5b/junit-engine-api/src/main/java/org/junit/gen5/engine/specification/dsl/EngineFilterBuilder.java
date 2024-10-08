/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.specification.dsl;

import org.junit.gen5.engine.EngineIdFilter;
import org.junit.gen5.engine.FilterResult;
import org.junit.gen5.engine.PostDiscoveryFilter;

public class EngineFilterBuilder {
	public static PostDiscoveryFilter filterByEngineId(String engineId) {
		return descriptor -> FilterResult.result(descriptor.getUniqueId().startsWith(engineId));
	}

	public static EngineIdFilter byEngineId(String engineId) {
		return new EngineIdFilter(engineId);
	}
}
