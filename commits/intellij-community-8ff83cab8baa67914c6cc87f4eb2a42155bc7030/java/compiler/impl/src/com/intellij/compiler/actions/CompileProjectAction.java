/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler.actions;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.build.BuildStatusNotificationAdapter;
import com.intellij.openapi.build.BuildSystemManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;

public class CompileProjectAction extends CompileActionBase {
  protected void doAction(DataContext dataContext, final Project project) {
    BuildSystemManager.getInstance(project).rebuildProject(new BuildStatusNotificationAdapter() {
      @Override
      public void finished(boolean aborted, int errors, int warnings) {
        if (aborted || project.isDisposed()) {
          return;
        }

        String text = getTemplatePresentation().getText();
        LocalHistory.getInstance().putSystemLabel(
          project, CompilerBundle.message(errors == 0 ? "rebuild.lvcs.label.no.errors" : "rebuild.lvcs.label.with.errors", text));
      }
    });
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    presentation.setEnabled(e.getProject() != null);
  }
}