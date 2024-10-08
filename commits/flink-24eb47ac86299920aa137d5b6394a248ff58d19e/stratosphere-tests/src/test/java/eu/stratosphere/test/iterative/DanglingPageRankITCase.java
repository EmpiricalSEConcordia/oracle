/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Apache Flink project (http://flink.incubator.apache.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 **********************************************************************************************************************/

package eu.stratosphere.test.iterative;

import java.util.Collection;

import org.apache.flink.api.common.Plan;
import org.apache.flink.configuration.Configuration;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import eu.stratosphere.test.iterative.nephele.DanglingPageRankNepheleITCase;
import eu.stratosphere.test.recordJobs.graph.DanglingPageRank;
import eu.stratosphere.test.util.RecordAPITestBase;

@RunWith(Parameterized.class)
public class DanglingPageRankITCase extends RecordAPITestBase {

	protected String pagesPath;
	protected String edgesPath;
	protected String resultPath;
	
	
	public DanglingPageRankITCase(Configuration config) {
		super(config);
		setTaskManagerNumSlots(DOP);
	}
	
	
	@Override
	protected void preSubmit() throws Exception {
		pagesPath = createTempFile("pages.txt", DanglingPageRankNepheleITCase.TEST_VERTICES);
		edgesPath = createTempFile("edges.txt", DanglingPageRankNepheleITCase.TEST_EDGES);
		resultPath = getTempFilePath("results");
	}

	@Override
	protected Plan getTestJob() {
		DanglingPageRank pr = new DanglingPageRank();
		Plan plan = pr.getPlan(
			config.getString("PageRankITCase#NoSubtasks", "1"), 
			pagesPath,
			edgesPath,
			resultPath,
			config.getString("PageRankITCase#NumIterations", "25"),	// max iterations
			"5",	// num vertices
			"1");	// num dangling vertices
		return plan;
	}


	@Parameters
	public static Collection<Object[]> getConfigurations() {
		Configuration config1 = new Configuration();
		config1.setInteger("PageRankITCase#NoSubtasks", DOP);
		config1.setString("PageRankITCase#NumIterations", "25");
		return toParameterList(config1);
	}
}
