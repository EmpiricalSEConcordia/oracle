/*
 * Copyright 2015 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.gen5.api.Test;
import org.junit.gen5.commons.util.Preconditions;
import org.junit.gen5.engine.ClassNameSpecification;
import org.junit.gen5.engine.EngineExecutionContext;
import org.junit.gen5.engine.TestDescriptor;
import org.junit.gen5.engine.TestEngine;
import org.junit.gen5.engine.TestPlanSpecification;
import org.junit.gen5.engine.TestPlanSpecificationElement;
import org.junit.gen5.engine.UniqueIdSpecification;
import org.opentestalliance.TestAbortedException;
import org.opentestalliance.TestSkippedException;

public class JUnit5TestEngine implements TestEngine {

	// TODO Consider using class names for engine IDs.
	private static final String ENGINE_ID = "junit5";

	@Override
	public String getId() {
		return ENGINE_ID;
	}

	@Override
	public List<TestDescriptor> discoverTests(TestPlanSpecification specification, TestDescriptor root) {
		List<TestDescriptor> testDescriptors = new ArrayList<>();

		for (TestPlanSpecificationElement element : specification) {
			if (element instanceof ClassNameSpecification) {
				ClassNameSpecification classNameSpecification = (ClassNameSpecification) element;
				Class<?> testClass = discoverTestClass(classNameSpecification.getClassName());
				JavaClassTestDescriptor parent = new JavaClassTestDescriptor(testClass, root);
				testDescriptors.add(parent);
				// @formatter:off
				testDescriptors.addAll(Arrays.stream(testClass.getDeclaredMethods())
					.filter(method -> method.isAnnotationPresent(Test.class))
					.map(method -> new JavaMethodTestDescriptor(  method, parent))
					.collect(toList()));
				// @formatter:on
			}
			else if (element instanceof UniqueIdSpecification) {
				UniqueIdSpecification uniqueIdSpecification = (UniqueIdSpecification) element;
				testDescriptors.add(JavaTestDescriptorFactory.from(uniqueIdSpecification.getUniqueId(), root));
			}
		}

		return testDescriptors;
	}

	private Class<?> discoverTestClass(String className) {
		// TODO Use correct ClassLoader
		try {
			return Class.forName(className);
		}
		catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(format("Failed to load test class '%s'", className));
		}
	}

	@Override
	public boolean supports(TestDescriptor testDescriptor) {
		// Todo super class for Java test descriptors?
		return testDescriptor instanceof JavaMethodTestDescriptor || testDescriptor instanceof JavaClassTestDescriptor;
	}

	@Override
	public void execute(EngineExecutionContext context) {

		// TODO Build a tree of TestDescriptors.
		//
		// Simply iterating over a collection is insufficient for our purposes. We need a
		// tree (or some form of hierarchical data structure) in order to be able to
		// execute each test within the correct scope.
		//
		// For example, we need to execute all test methods within a given test class as a
		// group in order to:
		//
		// 1) retain the instance across test method invocations (if desired).
		// 2) invoke class-level before & after methods _around_ the set of methods.

		// todo hierarchies of tests must be executed top down
		for (TestDescriptor testDescriptor : context.getTestDescriptions()) {

			Preconditions.condition(supports(testDescriptor),
				String.format("%s supports test descriptors of type %s, not of type %s", getClass().getSimpleName(),
					JavaMethodTestDescriptor.class.getName(),
					(testDescriptor != null ? testDescriptor.getClass().getName() : "null")));

			if (testDescriptor instanceof JavaClassTestDescriptor) {
				continue;
			}

			JavaMethodTestDescriptor javaTestDescriptor = (JavaMethodTestDescriptor) testDescriptor;

			try {
				context.getTestExecutionListener().testStarted(javaTestDescriptor);
				new TestExecutor(javaTestDescriptor).execute();
				context.getTestExecutionListener().testSucceeded(javaTestDescriptor);
			}
			catch (InvocationTargetException ex) {
				Throwable targetException = ex.getTargetException();
				if (targetException instanceof TestSkippedException) {
					context.getTestExecutionListener().testSkipped(javaTestDescriptor, targetException);
				}
				else if (targetException instanceof TestAbortedException) {
					context.getTestExecutionListener().testAborted(javaTestDescriptor, targetException);
				}
				else {
					context.getTestExecutionListener().testFailed(javaTestDescriptor, targetException);
				}
			}
			catch (NoSuchMethodException | InstantiationException | IllegalAccessException ex) {
				throw new IllegalStateException(String.format("Test %s is not well-formed and cannot be executed",
					javaTestDescriptor.getUniqueId()), ex);
			}
			catch (Exception ex) {
				context.getTestExecutionListener().testFailed(javaTestDescriptor, ex);
			}
		}
	}

}
