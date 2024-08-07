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

package eu.stratosphere.pact.runtime.hash.util;

/**
 * Takes the last m bits of a hash as bucket number
 * 
 * 
 */
public class LastBitsToRange implements RangeCalculator {

	private final int mask;
	private final int bucketCount;

	/**
	 * Create object which calculates bucket number according to the last m bits
	 * 
	 * @param numberOfLastBits
	 *            Number of bits to be used for bucket calculation
	 */
	public LastBitsToRange(int numberOfLastBits) {
		bucketCount = (int) Math.pow(2, numberOfLastBits);
		mask = bucketCount - 1;
	}


	@Override
	public int getBucket(int hash) {
		return hash & mask;
	}


	public int getBucketCount() {
		return bucketCount;
	}

}
