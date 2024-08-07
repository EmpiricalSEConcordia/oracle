/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2014 by the Apache Flink project (http://flink.incubator.apache.org)
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
package eu.stratosphere.pact.runtime.test.util.types;

public class IntList {
	
	private int key;
	private int[] value;

	public IntList() {}
	
	public IntList(int key, int[] value) {
		this.key = key;
		this.value = value;
	}
	
	
	public int getKey() {
		return key;
	}
	
	public void setKey(int key) {
		this.key = key;
	}
	
	public int[] getValue() {
		return value;
	}
	
	public void setValue(int[] value) {
		this.value = value;
	}
	
	/**
	 * returns record size when serialized in bytes
	 * 
	 * @return size in bytes
	 */
	public int getSerializedSize() {
		return (2 + this.value.length) * 4;
	}
	
	@Override
	public String toString() {
		String result = "( " + this.key + " / ";
		for (int i = 0; i < value.length; i++) {
			result = result + value[i] + " "; 
		}
		return result + ")";
	}
}
