/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import java.util.List;

public interface TransactionRunnable {
  void run(List exceptionList);
}
