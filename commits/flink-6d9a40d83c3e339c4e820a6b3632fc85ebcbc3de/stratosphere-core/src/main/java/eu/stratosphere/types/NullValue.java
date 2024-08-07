/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
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

package eu.stratosphere.types;

import java.io.IOException;

import eu.stratosphere.core.memory.DataInputView;
import eu.stratosphere.core.memory.DataOutputView;
import eu.stratosphere.core.memory.MemorySegment;

/**
 * Null base type for programs that implements the Key interface.
 * 
 * @see eu.stratosphere.types.Key
 */
public final class NullValue implements NormalizableKey<NullValue>, CopyableValue<NullValue> {
	private static final long serialVersionUID = 1L;
	
	/**
	 * The singleton NullValue instance.
	 */
	private final static NullValue INSTANCE = new NullValue();

	/**
	 * Returns the NullValue singleton instance.
	 *  
	 * @return The NullValue singleton instance.
	 */
	public static NullValue getInstance() {
		return INSTANCE;
	}

	// --------------------------------------------------------------------------------------------
	
	/**
	 * Creates a NullValue object.
	 */
	public NullValue() {}
	
	// --------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		return "(null)";
	}
	
	// --------------------------------------------------------------------------------------------
	
	@Override
	public void read(DataInputView in) throws IOException {
		in.readBoolean();
	}

	@Override
	public void write(DataOutputView out) throws IOException {
		out.writeBoolean(false);
	}
	
	// --------------------------------------------------------------------------------------------

	@Override
	public int compareTo(NullValue o) {
		return 0;
	}

	@Override
	public boolean equals(Object o) {
		return (o != null && o.getClass() == NullValue.class);
	}

	@Override
	public int hashCode() {
		return 53;
	}
	
	// --------------------------------------------------------------------------------------------

	@Override
	public int getMaxNormalizedKeyLen() {
		return 0;
	}

	@Override
	public void copyNormalizedKey(MemorySegment target, int offset, int len) {
		for (int i = offset; i < offset + len; i++) {
			target.put(i, (byte) 0);
		}
	}
	
	// --------------------------------------------------------------------------------------------
	
	@Override
	public int getBinaryLength() {
		return 1;
	}
	
	@Override
	public void copyTo(NullValue target) {
	}

	@Override
	public void copy(DataInputView source, DataOutputView target) throws IOException {
		source.readBoolean();
		target.writeBoolean(false);
	}
}
