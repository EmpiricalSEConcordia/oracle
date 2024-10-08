package org.apache.lucene.store;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.Collection;

public abstract class FilterDirectory extends Directory {
  private final Directory delegate;
  
  public FilterDirectory(Directory delegate) {
    this.delegate = delegate;
  }
  
  @Override
  public String[] listAll() throws IOException {
    return delegate.listAll();
  }

  @Override
  public boolean fileExists(String name) throws IOException {
    return delegate.fileExists(name);
  }

  @Override
  public long fileModified(String name) throws IOException {
    return delegate.fileModified(name);
  }
  
  @Override
  public void touchFile(String name) throws IOException {
    delegate.touchFile(name);
  }

  @Override
  public void deleteFile(String name) throws IOException {
    delegate.deleteFile(name);
  }

  @Override
  public long fileLength(String name) throws IOException {
    return delegate.fileLength(name);
  }

  @Override
  public IndexOutput createOutput(String name) throws IOException {
    return delegate.createOutput(name);
  }

  @Override
  public IndexInput openInput(String name) throws IOException {
    return delegate.openInput(name);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
  
  @Deprecated @Override
  public void sync(String name) throws IOException { // TODO 4.0 kill me
    delegate.sync(name);
  }
  
  public void sync(Collection<String> names) throws IOException { // TODO 4.0 make me abstract
    delegate.sync(names);
  }

  public IndexInput openInput(String name, int bufferSize) throws IOException {
    return delegate.openInput(name, bufferSize);
  }
  
  public Lock makeLock(String name) {
    return delegate.makeLock(name);
  }
  
  public void clearLock(String name) throws IOException {
    delegate.clearLock(name);
  }
  
  public void setLockFactory(LockFactory lockFactory) {
    delegate.setLockFactory(lockFactory);
  }
  
  public LockFactory getLockFactory() {
    return delegate.getLockFactory();
  }
  
  public String getLockID() {
    return delegate.getLockID();
  }
  
  public void copy(Directory to, String src, String dest) throws IOException {
    delegate.copy(to, src, dest);
  }
}
