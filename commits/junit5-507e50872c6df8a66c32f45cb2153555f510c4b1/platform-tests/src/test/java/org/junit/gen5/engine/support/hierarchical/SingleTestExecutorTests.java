/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.support.hierarchical;

import static org.junit.gen5.engine.TestExecutionResult.Status.ABORTED;
import static org.junit.gen5.engine.TestExecutionResult.Status.FAILED;
import static org.junit.gen5.engine.TestExecutionResult.Status.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Optional;

import org.junit.gen5.engine.TestExecutionResult;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/**
 * @since 5.0
 */
public class SingleTestExecutorTests {

	@Test
	public void executeSafelySuccessful() {
		TestExecutionResult result = new SingleTestExecutor().executeSafely(() -> {
		});

		assertEquals(SUCCESSFUL, result.getStatus());
		assertEquals(Optional.empty(), result.getThrowable());
	}

	@Test
	public void executeSafelyAborted() {
		TestAbortedException testAbortedException = new TestAbortedException("assumption violated");

		TestExecutionResult result = new SingleTestExecutor().executeSafely(() -> {
			throw testAbortedException;
		});

		assertEquals(ABORTED, result.getStatus());
		assertSame(testAbortedException, result.getThrowable().get());
	}

	@Test
	public void executeSafelyFailed() {
		AssertionError assertionError = new AssertionError("assumption violated");

		TestExecutionResult result = new SingleTestExecutor().executeSafely(() -> {
			throw assertionError;
		});

		assertEquals(FAILED, result.getStatus());
		assertSame(assertionError, result.getThrowable().get());
	}
}
