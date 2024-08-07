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

package org.springframework.zero.actuate.endpoint;

import java.util.Collection;
import java.util.Collections;

import org.junit.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.zero.actuate.endpoint.MetricsEndpoint;
import org.springframework.zero.actuate.endpoint.PublicMetrics;
import org.springframework.zero.actuate.metrics.Metric;
import org.springframework.zero.context.properties.EnableConfigurationProperties;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link MetricsEndpoint}.
 * 
 * @author Phillip Webb
 */
public class MetricsEndpointTests extends AbstractEndpointTests<MetricsEndpoint> {

	public MetricsEndpointTests() {
		super(Config.class, MetricsEndpoint.class, "/metrics", true, "endpoints.metrics");
	}

	@Test
	public void invoke() throws Exception {
		assertThat(getEndpointBean().invoke().get("a"), equalTo((Object) 0.5));
	}

	@Configuration
	@EnableConfigurationProperties
	public static class Config {

		@Bean
		public MetricsEndpoint endpoint() {
			final Metric metric = new Metric("a", 0.5f);
			PublicMetrics metrics = new PublicMetrics() {
				@Override
				public Collection<Metric> metrics() {
					return Collections.singleton(metric);
				}
			};
			return new MetricsEndpoint(metrics);
		}

	}
}
