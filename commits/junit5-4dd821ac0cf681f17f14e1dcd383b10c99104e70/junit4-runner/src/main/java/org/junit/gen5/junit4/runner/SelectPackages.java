/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.junit4.runner;

import static org.junit.gen5.commons.meta.API.Usage.Maintained;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.gen5.commons.meta.API;

/**
 * {@code @SelectPackages} specifies the names of packages to <em>select</em>
 * when running a test suite via {@code @RunWith(JUnitPlatform.class)}.
 *
 * @since 5.0
 * @see JUnitPlatform
 * @see SelectClasses
 * @see org.junit.gen5.engine.discovery.PackageSelector
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Documented
@API(Maintained)
public @interface SelectPackages {

	/**
	 * One or more fully qualified package names to select.
	 */
	String[] value();

}
