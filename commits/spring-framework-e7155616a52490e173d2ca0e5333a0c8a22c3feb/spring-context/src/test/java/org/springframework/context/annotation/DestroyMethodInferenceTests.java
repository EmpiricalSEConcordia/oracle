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

package org.springframework.context.annotation;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.Closeable;
import java.io.IOException;

import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

public class DestroyMethodInferenceTests {

	@Test
	public void beanMethods() {
		ConfigurableApplicationContext ctx =
				new AnnotationConfigApplicationContext(Config.class);
		WithExplicitDestroyMethod c0 = ctx.getBean(WithExplicitDestroyMethod.class);
		WithLocalCloseMethod c1 = ctx.getBean("c1", WithLocalCloseMethod.class);
		WithLocalCloseMethod c2 = ctx.getBean("c2", WithLocalCloseMethod.class);
		WithInheritedCloseMethod c3 = ctx.getBean("c3", WithInheritedCloseMethod.class);
		WithInheritedCloseMethod c4 = ctx.getBean("c4", WithInheritedCloseMethod.class);
		WithInheritedCloseMethod c5 = ctx.getBean("c5", WithInheritedCloseMethod.class);
		WithNoCloseMethod c6 = ctx.getBean("c6", WithNoCloseMethod.class);

		assertThat(c0.closed, is(false));
		assertThat(c1.closed, is(false));
		assertThat(c2.closed, is(false));
		assertThat(c3.closed, is(false));
		assertThat(c4.closed, is(false));
		assertThat(c5.closed, is(false));
		assertThat(c6.closed, is(false));
		ctx.close();
		assertThat("c0", c0.closed, is(true));
		assertThat("c1", c1.closed, is(true));
		assertThat("c2", c2.closed, is(true));
		assertThat("c3", c3.closed, is(true));
		assertThat("c4", c4.closed, is(true));
		assertThat("c5", c5.closed, is(true));
		assertThat("c6", c6.closed, is(false));
	}

	@Test
	public void xml() {
		ConfigurableApplicationContext ctx = new GenericXmlApplicationContext(
				getClass(), "DestroyMethodInferenceTests-context.xml");
		WithLocalCloseMethod x1 = ctx.getBean("x1", WithLocalCloseMethod.class);
		WithLocalCloseMethod x2 = ctx.getBean("x2", WithLocalCloseMethod.class);
		WithLocalCloseMethod x3 = ctx.getBean("x3", WithLocalCloseMethod.class);
		WithNoCloseMethod x4 = ctx.getBean("x4", WithNoCloseMethod.class);
		assertThat(x1.closed, is(false));
		assertThat(x2.closed, is(false));
		assertThat(x3.closed, is(false));
		assertThat(x4.closed, is(false));
		ctx.close();
		assertThat(x1.closed, is(false));
		assertThat(x2.closed, is(true));
		assertThat(x3.closed, is(true));
		assertThat(x4.closed, is(false));
	}

	@Configuration
	static class Config {
		@Bean(destroyMethod="explicitClose")
		public WithExplicitDestroyMethod c0() {
			return new WithExplicitDestroyMethod();
		}

		@Bean
		public WithLocalCloseMethod c1() {
			return new WithLocalCloseMethod();
		}

		@Bean
		public Object c2() {
			return new WithLocalCloseMethod();
		}

		@Bean
		public WithInheritedCloseMethod c3() {
			return new WithInheritedCloseMethod();
		}

		@Bean
		public Closeable c4() {
			return new WithInheritedCloseMethod();
		}

		@Bean(destroyMethod="other")
		public WithInheritedCloseMethod c5() {
			return new WithInheritedCloseMethod() {
				@Override
				public void close() throws IOException {
					throw new RuntimeException("close() should not be called");
				}
				@SuppressWarnings("unused")
				public void other() {
					this.closed = true;
				}
			};
		}

		@Bean
		public WithNoCloseMethod c6() {
			return new WithNoCloseMethod();
		}
	}


	static class WithExplicitDestroyMethod {
		boolean closed = false;
		public void explicitClose() {
			closed = true;
		}
	}

	static class WithLocalCloseMethod {
		boolean closed = false;
		public void close() {
			closed = true;
		}
	}

	static class WithInheritedCloseMethod implements Closeable {
		boolean closed = false;
		@Override
		public void close() throws IOException {
			closed = true;
		}
	}

	static class WithNoCloseMethod {
		boolean closed = false;
	}
}
