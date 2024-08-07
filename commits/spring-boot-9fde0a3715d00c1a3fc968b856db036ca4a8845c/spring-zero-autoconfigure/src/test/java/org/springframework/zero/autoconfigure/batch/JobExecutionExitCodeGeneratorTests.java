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

package org.springframework.zero.autoconfigure.batch;

import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link JobExecutionExitCodeGenerator}.
 * 
 * @author Dave Syer
 */
public class JobExecutionExitCodeGeneratorTests {

	private JobExecutionExitCodeGenerator generator = new JobExecutionExitCodeGenerator();

	@Test
	public void testExitCodeForRunning() {
		this.generator.onApplicationEvent(new JobExecutionEvent(new JobExecution(0L)));
		assertEquals(1, this.generator.getExitCode());
	}

	@Test
	public void testExitCodeForCompleted() {
		JobExecution execution = new JobExecution(0L);
		execution.setStatus(BatchStatus.COMPLETED);
		this.generator.onApplicationEvent(new JobExecutionEvent(execution));
		assertEquals(0, this.generator.getExitCode());
	}

	@Test
	public void testExitCodeForFailed() {
		JobExecution execution = new JobExecution(0L);
		execution.setStatus(BatchStatus.FAILED);
		this.generator.onApplicationEvent(new JobExecutionEvent(execution));
		assertEquals(5, this.generator.getExitCode());
	}

}
