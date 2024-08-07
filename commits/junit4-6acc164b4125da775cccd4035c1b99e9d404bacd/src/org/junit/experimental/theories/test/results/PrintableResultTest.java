package org.junit.experimental.theories.test.results;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertThat;

import java.util.Arrays;

import org.hamcrest.Matchers;
import org.junit.experimental.results.PrintableResult;
import org.junit.experimental.theories.methods.api.Theory;
import org.junit.experimental.theories.runner.api.Theories;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;

@RunWith(Theories.class)
public class PrintableResultTest {
	@SuppressWarnings("unchecked")
	@Theory(nullsAccepted= false)
	public void backTraceHasGoodToString(String descriptionName,
			final String stackTraceClassName) {
		Failure failure= new Failure(Description
				.createSuiteDescription(descriptionName), new Throwable() {
			private static final long serialVersionUID= 1L;

			@Override
			public StackTraceElement[] getStackTrace() {
				return new StackTraceElement[] { new StackTraceElement(
						stackTraceClassName, "methodName", "fileName", 1) };
			}
		});

		assertThat(new PrintableResult(asList(failure)).toString(), allOf(
				Matchers.containsString(descriptionName), Matchers
						.containsString(stackTraceClassName)));
	}

	public static String SHELL_POINT= "Shell Point";

	@Theory
	public void includeMultipleFailures(String secondExceptionName) {
		PrintableResult backtrace= new PrintableResult(Arrays.asList(
				new Failure(Description.createSuiteDescription("firstName"),
						new RuntimeException("firstException")), new Failure(
						Description.createSuiteDescription("secondName"),
						new RuntimeException(secondExceptionName))));
		assertThat(backtrace.toString(), Matchers
				.containsString(secondExceptionName));
	}
}
