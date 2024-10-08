/*
 * Copyright 2012-2014 the original author or authors.
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

package org.springframework.boot.actuate.autoconfigure;

import javax.sql.DataSource;

import org.springframework.boot.actuate.endpoint.DataSourcePublicMetrics;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourcePoolMetadataProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourcePoolMetadataProvidersConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * {@link EnableAutoConfiguration Auto-configuration} that provides metrics on dataSource
 * usage.
 *
 * @author Stephane Nicoll
 * @since 1.2.0
 */
@ConditionalOnBean(DataSource.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@Import(DataSourcePoolMetadataProvidersConfiguration.class)
public class DataSourceMetricsAutoConfiguration {

	@Bean
	@ConditionalOnBean(DataSourcePoolMetadataProvider.class)
	@ConditionalOnMissingBean
	public DataSourcePublicMetrics dataSourcePublicMetrics() {
		return new DataSourcePublicMetrics();
	}

}
