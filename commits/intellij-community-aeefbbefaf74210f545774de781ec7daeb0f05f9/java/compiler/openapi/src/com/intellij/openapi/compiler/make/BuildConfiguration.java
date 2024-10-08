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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class BuildConfiguration {

  @NonNls
  public abstract String getArchiveExtension();

  @Nullable
  public abstract String getJarPath();

  @Nullable
  public abstract String getExplodedPath();

  public abstract boolean isJarEnabled();

  public abstract boolean isExplodedEnabled();

  @Deprecated
  public boolean willBuildExploded() {
    return isExplodedEnabled() && getExplodedPath() != null;
  }

}