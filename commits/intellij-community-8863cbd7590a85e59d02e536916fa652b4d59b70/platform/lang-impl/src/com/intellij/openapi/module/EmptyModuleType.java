/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public class EmptyModuleType extends ModuleType<EmptyModuleBuilder> {
  private static final Icon ICON = AllIcons.Modules.Types.EmptyProjectType;
  @NonNls public static final String EMPTY_MODULE = "EMPTY_MODULE";
  //private static final EmptyModuleType ourInstance = new EmptyModuleType();

  public static EmptyModuleType getInstance() {
    return (EmptyModuleType)ModuleType.EMPTY;
  }

  @SuppressWarnings({"UnusedDeclaration"}) // implicitly instantiated from ModuleType
  public EmptyModuleType() {
    this(EMPTY_MODULE);
  }

  protected EmptyModuleType(@NonNls String id) {
    super(id);
  }

  public EmptyModuleBuilder createModuleBuilder() {
    return new EmptyModuleBuilder();
  }

  public String getName() {
    return ProjectBundle.message("module.type.empty.name");
  }

  public String getDescription() {
    return ProjectBundle.message("module.type.empty.description");
  }

  public Icon getBigIcon() {
    return ICON;
  }

  public Icon getNodeIcon(boolean isOpened) {
    return isOpened ? AllIcons.Nodes.ModuleOpen : AllIcons.Nodes.ModuleClosed;
  }
}
