/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 05-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.InspectionApplication;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.SmartRefElementPointerImpl;
import com.intellij.util.ArrayUtil;
import com.thoughtworks.xstream.io.xml.XppReader;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OfflineViewParseUtil {
  @NonNls private static final String PACKAGE = "package";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String HINTS = "hints";
  @NonNls private static final String LINE = "line";
  @NonNls private static final String MODULE = "module";

  private OfflineViewParseUtil() {
  }

  public static Map<String, Set<OfflineProblemDescriptor>> parse(final String problems) {
    final TObjectIntHashMap<String> fqName2IdxMap = new TObjectIntHashMap<String>();
    final Map<String, Set<OfflineProblemDescriptor>> package2Result = new THashMap<String, Set<OfflineProblemDescriptor>>();
    final XppReader reader = new XppReader(new StringReader(problems));
    try {
      while(reader.hasMoreChildren()) {
        reader.moveDown(); //problem
        final OfflineProblemDescriptor descriptor = new OfflineProblemDescriptor();
        boolean added = false;
        while(reader.hasMoreChildren()) {
          reader.moveDown();
          if (SmartRefElementPointerImpl.ENTRY_POINT.equals(reader.getNodeName())) {
            descriptor.setType(reader.getAttribute(SmartRefElementPointerImpl.TYPE_ATTR));
            final String fqName = reader.getAttribute(SmartRefElementPointerImpl.FQNAME_ATTR);
            descriptor.setFQName(fqName);

            if (!fqName2IdxMap.containsKey(fqName)) {
              fqName2IdxMap.put(fqName, 0);
            }
            int idx = fqName2IdxMap.get(fqName);
            descriptor.setProblemIndex(idx);
            fqName2IdxMap.put(fqName, idx + 1);

            final List<String> parentTypes = new ArrayList<String>();
            final List<String> parentNames = new ArrayList<String>();
            int deep = 0;
            while (true) {
              if (reader.hasMoreChildren()) {
                reader.moveDown();
                parentTypes.add(reader.getAttribute(SmartRefElementPointerImpl.TYPE_ATTR));
                parentNames.add(reader.getAttribute(SmartRefElementPointerImpl.FQNAME_ATTR));                
                deep ++;
              } else {
                while (deep-- > 0) {
                  reader.moveUp();
                }
                break;
              }
            }
            if (!parentTypes.isEmpty() && !parentNames.isEmpty()) {
              descriptor.setParentType(ArrayUtil.toStringArray(parentTypes));
              descriptor.setParentFQName(ArrayUtil.toStringArray(parentNames));
            }
          }
          if (DESCRIPTION.equals(reader.getNodeName())) {
            descriptor.setDescription(reader.getValue());
          }
          if (LINE.equals(reader.getNodeName())) {
            descriptor.setLine(Integer.parseInt(reader.getValue()));
          }
          if (MODULE.equals(reader.getNodeName())) {
            descriptor.setModule(reader.getValue());
          }
          if (HINTS.equals(reader.getNodeName())) {
            while(reader.hasMoreChildren()) {
              reader.moveDown();
              List<String> hints = descriptor.getHints();
              if (hints == null) {
                hints = new ArrayList<String>();
                descriptor.setHints(hints);
              }
              hints.add(reader.getAttribute("value"));
              reader.moveUp();
            }
          }
          if (PACKAGE.equals(reader.getNodeName())) {
            appendDescriptor(package2Result, reader.getValue(), descriptor);
            added = true;
          }
          while(reader.hasMoreChildren()) {
            reader.moveDown();
            if (PACKAGE.equals(reader.getNodeName())) {
              appendDescriptor(package2Result, reader.getValue(), descriptor);
              added = true;
            }
            reader.moveUp();
          }
          reader.moveUp();
        }
        if (!added) appendDescriptor(package2Result, "", descriptor);
        reader.moveUp();
      }
    }
    finally {
      reader.close();
    }
    return package2Result;
  }

  private static void appendDescriptor(final Map<String, Set<OfflineProblemDescriptor>> package2Result,
                                       final String packageName,
                                       final OfflineProblemDescriptor descriptor) {
    Set<OfflineProblemDescriptor> descriptors = package2Result.get(packageName);
    if (descriptors == null) {
      descriptors = new THashSet<OfflineProblemDescriptor>();
      package2Result.put(packageName, descriptors);
    }
    descriptors.add(descriptor);
  }

  @Nullable
  public static String parseProfileName(String descriptors) {
    final XppReader reader = new XppReader(new StringReader(descriptors));
    try {
      return reader.getAttribute(InspectionApplication.PROFILE);
    }
    catch (Exception e) {
      return null;
    }
    finally {
      reader.close();
    }
  }
}