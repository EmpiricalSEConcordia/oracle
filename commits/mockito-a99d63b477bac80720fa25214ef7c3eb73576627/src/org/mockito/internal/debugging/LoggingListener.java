/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.debugging;

import org.mockito.internal.invocation.InvocationImpl;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.util.MockitoLogger;

import static org.mockito.internal.util.StringJoiner.join;

public class LoggingListener implements FindingsListener {
    private boolean warnAboutUnstubbed;
    private final MockitoLogger logger;

    public LoggingListener(boolean warnAboutUnstubbed, MockitoLogger logger) {
        this.warnAboutUnstubbed = warnAboutUnstubbed;
        this.logger = logger;
    }

    public void foundStubCalledWithDifferentArgs(InvocationImpl unused, InvocationMatcher unstubbed) {
        logger.log(join(
                " *** Stubbing warnings from Mockito: *** ",
                "",
                "stubbed with those args here   " + unused.getLocation(),
                "BUT called with different args " + unstubbed.getInvocation().getLocation(),
                ""));
    }

    public void foundUnusedStub(InvocationImpl unused) {
        logger.log("This stubbing was never used   " + unused.getLocation() + "\n");
    }

    public void foundUnstubbed(InvocationMatcher unstubbed) {
        if (warnAboutUnstubbed) {
            logger.log(join(
                    "This method was not stubbed ",
                    unstubbed,
                    unstubbed.getInvocation().getLocation(),
                    ""));
        }
    }

    public boolean isWarnAboutUnstubbed() {
        return warnAboutUnstubbed;
    }

    public MockitoLogger getLogger() {
        return logger;
    }
}