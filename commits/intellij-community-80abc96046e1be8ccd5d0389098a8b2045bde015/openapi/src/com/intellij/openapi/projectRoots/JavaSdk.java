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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;

public abstract class JavaSdk extends SdkType implements ApplicationComponent {
  public JavaSdk(String name) {
    super(name);
  }

  public static JavaSdk getInstance() {
    return ApplicationManager.getApplication().getComponent(JavaSdk.class);
  }

  public abstract ProjectJdk createJdk(String jdkName, String jdkHome);
}
