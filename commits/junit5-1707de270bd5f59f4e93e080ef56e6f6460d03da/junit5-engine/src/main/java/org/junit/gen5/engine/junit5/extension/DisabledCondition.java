/*
 * Copyright 2015 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5.extension;

import static org.junit.gen5.commons.util.AnnotationUtils.findAnnotation;

import java.util.Optional;

import org.junit.gen5.api.Disabled;
import org.junit.gen5.api.extension.Condition;
import org.junit.gen5.api.extension.ExtensionContext;
import org.junit.gen5.api.extension.TestExtensionContext;
import org.junit.gen5.commons.util.StringUtils;

/**
 * {@link Condition} that supports the {@link Disabled @Disabled} annotation.
 *
 * @since 5.0
 * @see Disabled
 */
public class DisabledCondition implements Condition {

	/**
	 * Tests are disabled if {@code @Disabled} is either present on the
	 * test class or on the test method.
	 */
	@Override
	public Result evaluate(ExtensionContext context) {

		// Class level?
		Optional<Disabled> disabled = findAnnotation(context.getTestClass(), Disabled.class);

		// Method level?
		if (!disabled.isPresent() && context instanceof TestExtensionContext) {
			disabled = findAnnotation(((TestExtensionContext) context).getTestMethod(), Disabled.class);
		}

		if (disabled.isPresent()) {
			String reason = disabled.map(Disabled::value).filter(StringUtils::isNotBlank).orElse("Test is @Disabled");
			return Result.disabled(reason);
		}

		return Result.enabled("@Disabled is not present");
	}

}
