/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.support.discovery;

import org.junit.gen5.engine.EngineAwareTestDescriptor;
import org.junit.gen5.engine.TestEngine;

/**
 * @since 5.0
 */
public class EngineDescriptor extends AbstractTestDescriptor implements EngineAwareTestDescriptor {

	private final TestEngine engine;

	public EngineDescriptor(TestEngine engine) {
		super(engine.getId());
		this.engine = engine;
	}

	@Override
	public String getName() {
		return this.engine.getId();
	}

	@Override
	public String getDisplayName() {
		return "Test Engine: " + engine.getId();
	}

	@Override
	public final boolean isTest() {
		return false;
	}

	@Override
	public boolean isContainer() {
		return true;
	}

	@Override
	public TestEngine getEngine() {
		return engine;
	}

}
