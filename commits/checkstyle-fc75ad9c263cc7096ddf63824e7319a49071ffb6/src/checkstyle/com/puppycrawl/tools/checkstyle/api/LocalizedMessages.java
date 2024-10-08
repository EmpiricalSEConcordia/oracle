////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2002  Oliver Burn
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle.api;

// TODO: check that this class is in the right package
// as soon as architecture has settled. At the time of writing
// this class is not necessary as a part of the public api

import java.util.Collections;
import java.util.ArrayList;

/**
 * Collection of messages.
 * @author <a href="mailto:checkstyle@puppycrawl.com">Oliver Burn</a>
 * @version 1.0
 */
public final class LocalizedMessages
{
    /** contains the messages logged **/
    private final ArrayList mMessages = new ArrayList();

    /** @return the logged messages **/
    public LocalizedMessage[] getMessages()
    {
        Collections.sort(mMessages);
        return (LocalizedMessage[])
            mMessages.toArray(new LocalizedMessage[mMessages.size()]);
    }

    /** Reset the object **/
    public void reset()
    {
        mMessages.clear();
    }

    /**
     * Logs a message to be reported
     * @param aMsg the message to log
     **/
    public void add(LocalizedMessage aMsg)
    {
        if (!mMessages.contains(aMsg)) {
            mMessages.add(aMsg);
        }
    }

    /** @return the number of messages */
    public int size()
    {
        return mMessages.size();
    }
}
