package org.apache.lucene.util.cache;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple LRU cache implementation that uses a LinkedHashMap.
 * This cache is not synchronized, use {@link Cache#synchronizedCache(Cache)}
 * if needed.
 *
 * @deprecated Lucene's internal use of this class has now
 * switched to {@link DoubleBarrelLRUCache}.
 */
@Deprecated
public class SimpleLRUCache<K,V> extends SimpleMapCache<K,V> {
  private final static float LOADFACTOR = 0.75f;

  /**
   * Creates a last-recently-used cache with the specified size. 
   */
  public SimpleLRUCache(final int cacheSize) {
    super(new LinkedHashMap<K,V>((int) Math.ceil(cacheSize / LOADFACTOR) + 1, LOADFACTOR, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > cacheSize;
      }
    });
  }

}
