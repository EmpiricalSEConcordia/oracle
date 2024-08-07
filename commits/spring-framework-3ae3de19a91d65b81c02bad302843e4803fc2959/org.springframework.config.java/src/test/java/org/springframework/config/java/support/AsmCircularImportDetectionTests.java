/*
 * Copyright 2002-2009 the original author or authors.
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
package org.springframework.config.java.support;

import org.springframework.config.java.Import;
import org.springframework.util.ClassUtils;


/**
 * Unit test proving that ASM-based {@link ConfigurationParser} correctly detects circular use of
 * the {@link Import @Import} annotation.
 * 
 * <p>While this test is the only subclass of {@link AbstractCircularImportDetectionTests}, the
 * hierarchy remains in place in case a JDT-based ConfigurationParser implementation needs to be
 * developed.
 * 
 * @author Chris Beams
 */
public class AsmCircularImportDetectionTests extends AbstractCircularImportDetectionTests {
	@Override
	protected ConfigurationParser newParser() {
		return new ConfigurationParser(ClassUtils.getDefaultClassLoader());
	}

	@Override
	protected String loadAsConfigurationSource(Class<?> clazz) throws Exception {
		return clazz.getName();
	}

}
