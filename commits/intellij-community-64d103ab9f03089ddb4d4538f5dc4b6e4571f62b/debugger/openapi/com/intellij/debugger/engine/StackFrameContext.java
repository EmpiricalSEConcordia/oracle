/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.jdi.StackFrameProxy;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 3, 2003
 * Time: 5:58:58 PM
 * To change this template use Options | File Templates.
 */
public interface StackFrameContext {
  StackFrameProxy  getFrameProxy();
  DebugProcess     getDebugProcess();
}
