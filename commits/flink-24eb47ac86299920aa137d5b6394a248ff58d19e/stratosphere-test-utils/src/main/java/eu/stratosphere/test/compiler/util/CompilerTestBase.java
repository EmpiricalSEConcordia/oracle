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
package eu.stratosphere.test.compiler.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.flink.api.common.Plan;
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.common.io.FileInputFormat.FileBaseStatistics;
import org.apache.flink.api.common.operators.Operator;
import org.apache.flink.api.common.operators.base.GenericDataSourceBase;
import org.apache.flink.util.OperatingSystem;
import org.junit.Before;

import eu.stratosphere.compiler.DataStatistics;
import eu.stratosphere.compiler.PactCompiler;
import eu.stratosphere.compiler.costs.DefaultCostEstimator;
import eu.stratosphere.compiler.plan.OptimizedPlan;
import eu.stratosphere.compiler.plan.PlanNode;
import eu.stratosphere.compiler.plan.SingleInputPlanNode;

/**
 *
 */
public abstract class CompilerTestBase {

	protected static final String IN_FILE = OperatingSystem.isWindows() ? "file:/c:/" : "file:///dev/random";
	
	protected static final String OUT_FILE = OperatingSystem.isWindows() ? "file:/c:/" : "file:///dev/null";
	
	protected static final int DEFAULT_PARALLELISM = 8;
	
	protected static final String DEFAULT_PARALLELISM_STRING = String.valueOf(DEFAULT_PARALLELISM);
	
	private static final String CACHE_KEY = "cachekey";
	
	// ------------------------------------------------------------------------
	
	protected DataStatistics dataStats;
	
	protected PactCompiler withStatsCompiler;
	
	protected PactCompiler noStatsCompiler;
	
	private int statCounter;
	
	// ------------------------------------------------------------------------	
	
	@Before
	public void setup() {		
		this.dataStats = new DataStatistics();
		this.withStatsCompiler = new PactCompiler(this.dataStats, new DefaultCostEstimator());
		this.withStatsCompiler.setDefaultDegreeOfParallelism(DEFAULT_PARALLELISM);
		
		this.noStatsCompiler = new PactCompiler(null, new DefaultCostEstimator());
		this.noStatsCompiler.setDefaultDegreeOfParallelism(DEFAULT_PARALLELISM);
	}
	
	// ------------------------------------------------------------------------
	
	public OptimizedPlan compileWithStats(Plan p) {
		return this.withStatsCompiler.compile(p);
	}
	
	public OptimizedPlan compileNoStats(Plan p) {
		return this.noStatsCompiler.compile(p);
	}
	
	public void setSourceStatistics(GenericDataSourceBase<?, ?> source, long size, float recordWidth) {
		setSourceStatistics(source, new FileBaseStatistics(Long.MAX_VALUE, size, recordWidth));
	}
	
	public void setSourceStatistics(GenericDataSourceBase<?, ?> source, FileBaseStatistics stats) {
		final String key = CACHE_KEY + this.statCounter++;
		this.dataStats.cacheBaseStatistics(stats, key);
		source.setStatisticsKey(key);
	}

	public static OperatorResolver getContractResolver(Plan plan) {
		return new OperatorResolver(plan);
	}
	
	public static OptimizerPlanNodeResolver getOptimizerPlanNodeResolver(OptimizedPlan plan) {
		return new OptimizerPlanNodeResolver(plan);
	}
	
	// ------------------------------------------------------------------------
	
	public static final class OptimizerPlanNodeResolver {
		
		private final Map<String, ArrayList<PlanNode>> map;
		
		OptimizerPlanNodeResolver(OptimizedPlan p) {
			HashMap<String, ArrayList<PlanNode>> map = new HashMap<String, ArrayList<PlanNode>>();
			
			for (PlanNode n : p.getAllNodes()) {
				Operator<?> c = n.getOriginalOptimizerNode().getPactContract();
				String name = c.getName();
				
				ArrayList<PlanNode> list = map.get(name);
				if (list == null) {
					list = new ArrayList<PlanNode>(2);
					map.put(name, list);
				}
				
				// check whether this node is a child of a node with the same contract (aka combiner)
				boolean shouldAdd = true;
				for (Iterator<PlanNode> iter = list.iterator(); iter.hasNext();) {
					PlanNode in = iter.next();
					if (in.getOriginalOptimizerNode().getPactContract() == c) {
						// is this the child or is our node the child
						if (in instanceof SingleInputPlanNode && n instanceof SingleInputPlanNode) {
							SingleInputPlanNode thisNode = (SingleInputPlanNode) n;
							SingleInputPlanNode otherNode = (SingleInputPlanNode) in;
							
							if (thisNode.getPredecessor() == otherNode) {
								// other node is child, remove it
								iter.remove();
							} else if (otherNode.getPredecessor() == thisNode) {
								shouldAdd = false;
							}
						} else {
							throw new RuntimeException("Unrecodnized case in test.");
						}
					}
				}
				
				if (shouldAdd) {
					list.add(n);
				}
			}
			
			this.map = map;
		}
		
		
		@SuppressWarnings("unchecked")
		public <T extends PlanNode> T getNode(String name) {
			List<PlanNode> nodes = this.map.get(name);
			if (nodes == null || nodes.isEmpty()) {
				throw new RuntimeException("No node found with the given name.");
			} else if (nodes.size() != 1) {
				throw new RuntimeException("Multiple nodes found with the given name.");
			} else {
				return (T) nodes.get(0);
			}
		}
		
		@SuppressWarnings("unchecked")
		public <T extends PlanNode> T getNode(String name, Class<? extends Function> stubClass) {
			List<PlanNode> nodes = this.map.get(name);
			if (nodes == null || nodes.isEmpty()) {
				throw new RuntimeException("No node found with the given name and stub class.");
			} else {
				PlanNode found = null;
				for (PlanNode node : nodes) {
					if (node.getClass() == stubClass) {
						if (found == null) {
							found = node;
						} else {
							throw new RuntimeException("Multiple nodes found with the given name and stub class.");
						}
					}
				}
				if (found == null) {
					throw new RuntimeException("No node found with the given name and stub class.");
				} else {
					return (T) found;
				}
			}
		}
		
		public List<PlanNode> getNodes(String name) {
			List<PlanNode> nodes = this.map.get(name);
			if (nodes == null || nodes.isEmpty()) {
				throw new RuntimeException("No node found with the given name.");
			} else {
				return new ArrayList<PlanNode>(nodes);
			}
		}
	}
}
