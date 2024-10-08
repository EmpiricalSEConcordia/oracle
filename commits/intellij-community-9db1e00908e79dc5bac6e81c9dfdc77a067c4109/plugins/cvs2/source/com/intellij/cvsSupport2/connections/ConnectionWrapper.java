package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.javacvsImpl.io.InputStreamWrapper;
import com.intellij.cvsSupport2.javacvsImpl.io.OutputStreamWrapper;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.io.IStreamLogger;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * author: lesya
 */
public class ConnectionWrapper implements IConnection {
  protected final IConnection mySourceConnection;
  private InputStream myInputStreamWrapper;
  private OutputStreamWrapper myOutputStreamWrapper;
  private final ReadWriteStatistics myStatistics;
  private final ICvsCommandStopper myCommandStopper;
  @NonNls private static final String CVS_DONT_READ_IN_THREAD_PROPERTY = "cvs.dont.read.in.thread";

  public ConnectionWrapper(IConnection sourceConnection, ReadWriteStatistics statistics, ICvsCommandStopper commandStopper) {
    mySourceConnection = sourceConnection;
    myStatistics = statistics;
    myCommandStopper = commandStopper;
  }

  public InputStream getInputStream() {
    if (myInputStreamWrapper == null) {
      if (Boolean.TRUE.toString().equals(System.getProperty(CVS_DONT_READ_IN_THREAD_PROPERTY))) {
        myInputStreamWrapper = mySourceConnection.getInputStream();
      }
      else {
        myInputStreamWrapper = new InputStreamWrapper(mySourceConnection.getInputStream(),
                                                      myCommandStopper,
                                                      myStatistics);
      }
    }
    return myInputStreamWrapper;
  }

  public OutputStream getOutputStream() {
    if (myOutputStreamWrapper == null) {
      myOutputStreamWrapper = new OutputStreamWrapper(mySourceConnection.getOutputStream(), myStatistics);
    }
    return myOutputStreamWrapper;
  }

  public String getRepository() {
    return mySourceConnection.getRepository();
  }

  public void verify(IStreamLogger streamLogger) throws AuthenticationException {
    mySourceConnection.verify(streamLogger);
  }

  public void open(IStreamLogger streamLogger) throws AuthenticationException {
    mySourceConnection.open(streamLogger);
  }

  public void close() throws IOException {
    if (myInputStreamWrapper != null) {
      myInputStreamWrapper.close();
      myInputStreamWrapper = null;
      myOutputStreamWrapper = null;
    }
    mySourceConnection.close();
  }
}
