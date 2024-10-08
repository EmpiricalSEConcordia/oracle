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

package eu.stratosphere.runtime.io.serialization.types;

import eu.stratosphere.core.memory.DataInputView;
import eu.stratosphere.core.memory.DataOutputView;

import java.io.IOException;
import java.util.Random;

public class AsciiStringType implements SerializationTestType {

	private static final int MAX_LEN = 1500;

	public String value;

	public AsciiStringType() {
		this.value = "";
	}

	private AsciiStringType(String value) {
		this.value = value;
	}

	@Override
	public AsciiStringType getRandom(Random rnd) {
		final StringBuilder bld = new StringBuilder();
		final int len = rnd.nextInt(MAX_LEN + 1);

		for (int i = 0; i < len; i++) {
			// 1--127
			bld.append((char) (rnd.nextInt(126) + 1));
		}

		return new AsciiStringType(bld.toString());
	}

	@Override
	public int length() {
		return value.getBytes().length + 2;
	}

	@Override
	public void write(DataOutputView out) throws IOException {
		out.writeUTF(this.value);
	}

	@Override
	public void read(DataInputView in) throws IOException {
		this.value = in.readUTF();
	}

	@Override
	public int hashCode() {
		return this.value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AsciiStringType) {
			AsciiStringType other = (AsciiStringType) obj;
			return this.value.equals(other.value);
		} else {
			return false;
		}
	}
}
