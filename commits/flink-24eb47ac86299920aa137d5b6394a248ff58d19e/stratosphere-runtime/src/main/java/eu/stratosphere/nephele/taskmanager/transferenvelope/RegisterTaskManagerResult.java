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

package eu.stratosphere.nephele.taskmanager.transferenvelope;

import eu.stratosphere.nephele.util.EnumUtils;

import java.io.IOException;

import org.apache.flink.core.io.IOReadableWritable;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

public class RegisterTaskManagerResult implements IOReadableWritable {
	public enum ReturnCode{
		SUCCESS, FAILURE
	};

	public RegisterTaskManagerResult(){
		this.returnCode = ReturnCode.SUCCESS;
	}

	public RegisterTaskManagerResult(ReturnCode returnCode){
		this.returnCode = returnCode;
	}

	private ReturnCode returnCode;

	public ReturnCode getReturnCode() { return this.returnCode; }


	@Override
	public void write(DataOutputView out) throws IOException {
		EnumUtils.writeEnum(out, this.returnCode);
	}

	@Override
	public void read(DataInputView in) throws IOException {
		this.returnCode = EnumUtils.readEnum(in, ReturnCode.class);
	}
}
