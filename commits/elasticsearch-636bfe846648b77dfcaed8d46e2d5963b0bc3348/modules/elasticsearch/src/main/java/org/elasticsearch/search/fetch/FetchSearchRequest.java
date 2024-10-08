/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.fetch;

import org.elasticsearch.common.trove.ExtTIntArrayList;
import org.elasticsearch.util.io.stream.StreamInput;
import org.elasticsearch.util.io.stream.StreamOutput;
import org.elasticsearch.util.io.stream.Streamable;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public class FetchSearchRequest implements Streamable {

    private long id;

    private int[] docIds;

    private int size;

    public FetchSearchRequest() {
    }

    public FetchSearchRequest(long id, ExtTIntArrayList list) {
        this.id = id;
        this.docIds = list.unsafeArray();
        this.size = list.size();
    }

    public FetchSearchRequest(long id, int[] docIds) {
        this.id = id;
        this.docIds = docIds;
        this.size = docIds.length;
    }

    public long id() {
        return id;
    }

    public int[] docIds() {
        return docIds;
    }

    public int docIdsSize() {
        return size;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        id = in.readLong();
        size = in.readVInt();
        docIds = new int[size];
        for (int i = 0; i < size; i++) {
            docIds[i] = in.readVInt();
        }
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        out.writeLong(id);
        out.writeVInt(size);
        for (int i = 0; i < size; i++) {
            out.writeVInt(docIds[i]);
        }
    }
}
