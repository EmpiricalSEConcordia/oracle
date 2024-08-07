/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.test.context.junit4.statements;

import java.util.ArrayList;
import java.util.List;

import org.junit.internal.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.springframework.test.context.TestContextManager;

/**
 * {@code RunAfterTestClassCallbacks} is a custom JUnit 4.5+
 * {@link Statement} which allows the <em>Spring TestContext Framework</em> to
 * be plugged into the JUnit execution chain by calling
 * {@link TestContextManager#afterTestClass() afterTestClass()} on the supplied
 * {@link TestContextManager}.
 *
 * @see #evaluate()
 * @see RunBeforeTestMethodCallbacks
 * @author Sam Brannen
 * @since 3.0
 */
@SuppressWarnings("deprecation")
public class RunAfterTestClassCallbacks extends Statement {

	private final Statement next;

	private final TestContextManager testContextManager;


	/**
	 * Constructs a new {@code RunAfterTestClassCallbacks} statement.
	 *
	 * @param next the next {@code Statement} in the execution chain
	 * @param testContextManager the TestContextManager upon which to call
	 * {@code afterTestClass()}
	 */
	public RunAfterTestClassCallbacks(Statement next, TestContextManager testContextManager) {
		this.next = next;
		this.testContextManager = testContextManager;
	}

	/**
	 * Invokes the next {@link Statement} in the execution chain (typically an
	 * instance of {@link org.junit.internal.runners.statements.RunAfters
	 * RunAfters}), catching any exceptions thrown, and then calls
	 * {@link TestContextManager#afterTestClass()}. If the call to
	 * {@code afterTestClass()} throws an exception, it will also be
	 * tracked. Multiple exceptions will be combined into a
	 * {@link MultipleFailureException}.
	 */
	@Override
	public void evaluate() throws Throwable {
		List<Throwable> errors = new ArrayList<Throwable>();
		try {
			this.next.evaluate();
		}
		catch (Throwable e) {
			errors.add(e);
		}

		try {
			this.testContextManager.afterTestClass();
		}
		catch (Exception e) {
			errors.add(e);
		}

		if (errors.isEmpty()) {
			return;
		}
		if (errors.size() == 1) {
			throw errors.get(0);
		}
		throw new MultipleFailureException(errors);
	}
}
