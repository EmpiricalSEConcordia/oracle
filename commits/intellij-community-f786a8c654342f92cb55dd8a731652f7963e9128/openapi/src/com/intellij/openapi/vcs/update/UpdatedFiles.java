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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsBundle;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UpdatedFiles implements JDOMExternalizable {
  private final List<FileGroup> myGroups = new ArrayList<FileGroup>();

  private UpdatedFiles() {
  }

  public FileGroup registerGroup(FileGroup fileGroup) {
    FileGroup existing = getGroupById(fileGroup.getId());
    if (existing != null) return existing;
    myGroups.add(fileGroup);
    return fileGroup;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    FileGroup.writeGroupsToElement(myGroups, element);
  }

  public void readExternal(Element element) throws InvalidDataException {
    FileGroup.readGroupsFromElement(myGroups, element);
  }

  public boolean isEmpty() {
    for (Iterator<FileGroup> iterator = myGroups.iterator(); iterator.hasNext();) {
      FileGroup fileGroup = iterator.next();
      if (!fileGroup.isEmpty()) return false;
    }
    return true;
  }


  public FileGroup getGroupById(String id) {
    if (id == null) return null;
    return findByIdIn(myGroups, id);
  }

  private FileGroup findByIdIn(List<FileGroup> groups, String id) {
    for (Iterator<FileGroup> iterator = groups.iterator(); iterator.hasNext();) {
      FileGroup fileGroup = iterator.next();
      if (id.equals(fileGroup.getId())) return fileGroup;
      FileGroup foundInChildren = findByIdIn(fileGroup.getChildren(), id);
      if (foundInChildren != null) return foundInChildren;
    }
    return null;
  }

  public List<FileGroup> getTopLevelGroups() {
    return myGroups;
  }

  public static UpdatedFiles create() {
    UpdatedFiles result = new UpdatedFiles();
    FileGroup updatedFromServer = result.registerGroup(new FileGroup(VcsBundle.message("update.group.name.updated.from.server"), VcsBundle.message("status.group.name.changed.on.server"), false, FileGroup.CHANGED_ON_SERVER_ID, false));

    updatedFromServer.addChild(new FileGroup(VcsBundle.message("update.group.name.updated"), VcsBundle.message("status.group.name.changed"), false, FileGroup.UPDATED_ID, false));
    updatedFromServer.addChild(new FileGroup(VcsBundle.message("update.group.name.created"), VcsBundle.message("status.group.name.created"), false, FileGroup.CREATED_ID, false));
    updatedFromServer.addChild(new FileGroup(VcsBundle.message("update.group.name.deleted"), VcsBundle.message("status.group.name.deleted"), false, FileGroup.REMOVED_FROM_REPOSITORY_ID, true));
    updatedFromServer.addChild(new FileGroup(VcsBundle.message("update.group.name.restored"), VcsBundle.message("status.group.name.will.be.restored"), false, FileGroup.RESTORED_ID, false));

    result.registerGroup(new FileGroup(VcsBundle.message("update.group.name.modified"), VcsBundle.message("status.group.name.modified"), false, FileGroup.MODIFIED_ID, false));

    result.registerGroup(new FileGroup(VcsBundle.message("update.group.name.merged.with.conflicts"), VcsBundle.message("status.group.name.will.be.merged.with.conflicts"), false, FileGroup.MERGED_WITH_CONFLICT_ID, false));
    result.registerGroup(new FileGroup(VcsBundle.message("update.group.name.merged"), VcsBundle.message("status.group.name.will.be.merged"), false, FileGroup.MERGED_ID, false));
    result.registerGroup(new FileGroup(VcsBundle.message("update.group.name.not.in.repository"), VcsBundle.message("status.group.name.not.in.repository"), true, FileGroup.UNKNOWN_ID, false));
    result.registerGroup(new FileGroup(VcsBundle.message("update.group.name.locally.added"), VcsBundle.message("status.group.name.locally.added"), false, FileGroup.LOCALLY_ADDED_ID, false));
    result.registerGroup(new FileGroup(VcsBundle.message("update.group.name.locally.removed"), VcsBundle.message("status.group.name.locally.removed"), false, FileGroup.LOCALLY_REMOVED_ID, false));
    return result;
  }
}
