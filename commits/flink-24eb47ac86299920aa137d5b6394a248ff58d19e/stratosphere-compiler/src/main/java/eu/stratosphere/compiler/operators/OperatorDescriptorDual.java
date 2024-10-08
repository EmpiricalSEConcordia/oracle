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

package eu.stratosphere.compiler.operators;

import java.util.List;

import org.apache.flink.api.common.operators.util.FieldList;

import eu.stratosphere.compiler.dag.TwoInputNode;
import eu.stratosphere.compiler.dataproperties.GlobalProperties;
import eu.stratosphere.compiler.dataproperties.LocalProperties;
import eu.stratosphere.compiler.dataproperties.RequestedGlobalProperties;
import eu.stratosphere.compiler.dataproperties.RequestedLocalProperties;
import eu.stratosphere.compiler.plan.Channel;
import eu.stratosphere.compiler.plan.DualInputPlanNode;

/**
 * 
 */
public abstract class OperatorDescriptorDual implements AbstractOperatorDescriptor {
	
	protected final FieldList keys1;
	protected final FieldList keys2;
	
	private List<GlobalPropertiesPair> globalProps;
	private List<LocalPropertiesPair> localProps;
	
	protected OperatorDescriptorDual() {
		this(null, null);
	}
	
	protected OperatorDescriptorDual(FieldList keys1, FieldList keys2) {
		this.keys1 = keys1;
		this.keys2 = keys2;
	}
	
	public List<GlobalPropertiesPair> getPossibleGlobalProperties() {
		if (this.globalProps == null) {
			this.globalProps = createPossibleGlobalProperties();
		}
		
		return this.globalProps;
	}
	
	public List<LocalPropertiesPair> getPossibleLocalProperties() {
		if (this.localProps == null) {
			this.localProps = createPossibleLocalProperties();
		}
		
		return this.localProps;
	}
	
	protected abstract List<GlobalPropertiesPair> createPossibleGlobalProperties();
	
	protected abstract List<LocalPropertiesPair> createPossibleLocalProperties();
	
	public abstract boolean areCoFulfilled(RequestedLocalProperties requested1, RequestedLocalProperties requested2,
			LocalProperties produced1, LocalProperties produced2);
	
	public abstract DualInputPlanNode instantiate(Channel in1, Channel in2, TwoInputNode node);
	
	public abstract GlobalProperties computeGlobalProperties(GlobalProperties in1, GlobalProperties in2);
	
	public abstract LocalProperties computeLocalProperties(LocalProperties in1, LocalProperties in2);
	
	// --------------------------------------------------------------------------------------------
	
	public static final class GlobalPropertiesPair {
		
		private final RequestedGlobalProperties props1, props2;

		public GlobalPropertiesPair(RequestedGlobalProperties props1, RequestedGlobalProperties props2) {
			this.props1 = props1;
			this.props2 = props2;
		}
		
		public RequestedGlobalProperties getProperties1() {
			return this.props1;
		}
		
		public RequestedGlobalProperties getProperties2() {
			return this.props2;
		}
		
		@Override
		public int hashCode() {
			return (this.props1 == null ? 0 : this.props1.hashCode()) ^ (this.props2 == null ? 0 : this.props2.hashCode());
		}

		@Override
		public boolean equals(Object obj) {
			if (obj.getClass() == GlobalPropertiesPair.class) {
				final GlobalPropertiesPair other = (GlobalPropertiesPair) obj;
				
				return (this.props1 == null ? other.props1 == null : this.props1.equals(other.props1)) &&
						(this.props2 == null ? other.props2 == null : this.props2.equals(other.props2));
			}
			return false;
		}
		
		@Override
		public String toString() {
			return "{" + this.props1 + " / " + this.props2 + "}";
		}
	}
	
	public static final class LocalPropertiesPair {
		
		private final RequestedLocalProperties props1, props2;

		public LocalPropertiesPair(RequestedLocalProperties props1, RequestedLocalProperties props2) {
			this.props1 = props1;
			this.props2 = props2;
		}
		
		public RequestedLocalProperties getProperties1() {
			return this.props1;
		}
		
		public RequestedLocalProperties getProperties2() {
			return this.props2;
		}
		
		@Override
		public int hashCode() {
			return (this.props1 == null ? 0 : this.props1.hashCode()) ^ (this.props2 == null ? 0 : this.props2.hashCode());
		}

		@Override
		public boolean equals(Object obj) {
			if (obj.getClass() == LocalPropertiesPair.class) {
				final LocalPropertiesPair other = (LocalPropertiesPair) obj;
				
				return (this.props1 == null ? other.props1 == null : this.props1.equals(other.props1)) &&
						(this.props2 == null ? other.props2 == null : this.props2.equals(other.props2));
			}
			return false;
		}

		@Override
		public String toString() {
			return "{" + this.props1 + " / " + this.props2 + "}";
		}
	}
}
