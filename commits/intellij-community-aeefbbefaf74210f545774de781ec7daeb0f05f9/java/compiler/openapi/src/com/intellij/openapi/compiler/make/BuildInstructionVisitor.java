/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.compiler.make;


public abstract class BuildInstructionVisitor {
  public boolean visitInstruction(BuildInstruction instruction) throws Exception {
    return true;
  }
  public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
    return visitInstruction(instruction);
  }
  public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws Exception {
    return visitFileCopyInstruction(instruction);
  }
  public boolean visitCompoundBuildInstruction(CompoundBuildInstruction instruction) throws Exception {
    return visitInstruction(instruction);
  }
}