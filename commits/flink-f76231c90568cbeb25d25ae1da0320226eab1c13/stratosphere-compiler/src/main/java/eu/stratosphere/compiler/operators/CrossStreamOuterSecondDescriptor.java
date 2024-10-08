/***********************************************************************************************************************
 *
 * Copyright (C) 2012 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.compiler.operators;

import eu.stratosphere.compiler.dataproperties.LocalProperties;
import eu.stratosphere.pact.runtime.task.DriverStrategy;

/**
 * 
 */
public class CrossStreamOuterSecondDescriptor extends CartesianProductDescriptor {
	
	public CrossStreamOuterSecondDescriptor() {
		this(true, true);
	}
	
	public CrossStreamOuterSecondDescriptor(boolean allowBroadcastFirst, boolean allowBroadcastSecond) {
		super(allowBroadcastFirst, allowBroadcastSecond);
	}
	
	@Override
	public DriverStrategy getStrategy() {
		return DriverStrategy.NESTEDLOOP_STREAMED_OUTER_SECOND;
	}

	@Override
	public LocalProperties computeLocalProperties(LocalProperties in1, LocalProperties in2) {
		// uniqueness becomes grouping with streamed nested loops
		if ((in2.getGroupedFields() == null || in2.getGroupedFields().size() == 0) &&
				in2.getUniqueFields() != null && in2.getUniqueFields().size() > 0)
		{
			in2.setGroupedFields(in2.getUniqueFields().iterator().next().toFieldList());
		}
		in2.clearUniqueFieldSets();
		return in2;
	}
}
