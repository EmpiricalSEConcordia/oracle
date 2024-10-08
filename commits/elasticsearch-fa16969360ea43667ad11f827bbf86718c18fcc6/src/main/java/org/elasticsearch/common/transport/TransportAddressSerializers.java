/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
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

package org.elasticsearch.common.transport;

import com.google.common.collect.ImmutableMap;
import org.elasticsearch.ElasticsearchIllegalStateException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import java.io.IOException;
import java.lang.reflect.Constructor;

import static org.elasticsearch.common.collect.MapBuilder.newMapBuilder;

/**
 * A global registry of all different types of {@link org.elasticsearch.common.transport.TransportAddress} allowing
 * to perform serialization of them.
 * <p/>
 * <p>By default, adds {@link org.elasticsearch.common.transport.InetSocketTransportAddress}.
 *
 *
 */
public abstract class TransportAddressSerializers {

    private static final ESLogger logger = Loggers.getLogger(TransportAddressSerializers.class);

    private static ImmutableMap<Short, Constructor<? extends TransportAddress>> addressConstructors = ImmutableMap.of();

    static {
        try {
            addAddressType(DummyTransportAddress.INSTANCE);
            addAddressType(new InetSocketTransportAddress());
            addAddressType(new LocalTransportAddress());
        } catch (Exception e) {
            logger.warn("Failed to add InetSocketTransportAddress", e);
        }
    }

    public static synchronized void addAddressType(TransportAddress address) throws Exception {
        if (addressConstructors.containsKey(address.uniqueAddressTypeId())) {
            throw new ElasticsearchIllegalStateException("Address [" + address.uniqueAddressTypeId() + "] already bound");
        }
        Constructor<? extends TransportAddress> constructor = address.getClass().getDeclaredConstructor();
        constructor.setAccessible(true);
        addressConstructors = newMapBuilder(addressConstructors).put(address.uniqueAddressTypeId(), constructor).immutableMap();
    }

    public static TransportAddress addressFromStream(StreamInput input) throws IOException {
        short addressUniqueId = input.readShort();
        Constructor<? extends TransportAddress> constructor = addressConstructors.get(addressUniqueId);
        if (constructor == null) {
            throw new IOException("No transport address mapped to [" + addressUniqueId + "]");
        }
        TransportAddress address;
        try {
            address = constructor.newInstance();
        } catch (Exception e) {
            throw new IOException("Failed to create class with constructor [" + constructor + "]", e);
        }
        address.readFrom(input);
        return address;
    }

    public static void addressToStream(StreamOutput out, TransportAddress address) throws IOException {
        out.writeShort(address.uniqueAddressTypeId());
        address.writeTo(out);
    }
}
