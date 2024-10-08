/*
 * Copyright 2012-2013 the original author or authors.
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

package org.springframework.boot.config.jdbc;

import javax.sql.DataSource;

import org.junit.Test;
import org.springframework.boot.config.jdbc.BasicDataSourceConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link BasicDataSourceConfiguration}.
 * 
 * @author Dave Syer
 */
public class BasicDataSourceConfigurationTests {

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@Test
	public void testDataSourceExists() throws Exception {
		this.context.register(BasicDataSourceConfiguration.class);
		this.context.refresh();
		assertNotNull(this.context.getBean(DataSource.class));
		this.context.close();
	}

}
