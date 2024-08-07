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

package org.elasticsearch.util.settings.loader;

import org.elasticsearch.util.xcontent.XContentFactory;
import org.elasticsearch.util.xcontent.XContentParser;
import org.elasticsearch.util.xcontent.XContentType;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.util.collect.Lists.*;
import static org.elasticsearch.util.collect.Maps.*;

/**
 * Settings loader that loads (parses) the settings in a json format by flattening them
 * into a map.
 *
 * @author kimchy (shay.banon)
 */
public class JsonSettingsLoader implements SettingsLoader {

    @Override public Map<String, String> load(String source) throws IOException {
        XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(source);
        try {
            return load(parser);
        } finally {
            parser.close();
        }
    }

    @Override public Map<String, String> load(byte[] source) throws IOException {
        XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(source);
        try {
            return load(parser);
        } finally {
            parser.close();
        }
    }

    public Map<String, String> load(XContentParser jp) throws IOException {
        StringBuilder sb = new StringBuilder();
        Map<String, String> settings = newHashMap();
        List<String> path = newArrayList();
        jp.nextToken();
        serializeObject(settings, sb, path, jp, null);
        return settings;
    }

    private void serializeObject(Map<String, String> settings, StringBuilder sb, List<String> path, XContentParser parser, String objFieldName) throws IOException {
        if (objFieldName != null) {
            path.add(objFieldName);
        }

        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.START_OBJECT) {
                serializeObject(settings, sb, path, parser, currentFieldName);
            } else if (token == XContentParser.Token.START_ARRAY) {
                serializeArray(settings, sb, path, parser, currentFieldName);
            } else if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.VALUE_NULL) {
                // ignore this
            } else {
                serializeValue(settings, sb, path, parser, currentFieldName);

            }
        }

        if (objFieldName != null) {
            path.remove(path.size() - 1);
        }
    }

    private void serializeArray(Map<String, String> settings, StringBuilder sb, List<String> path, XContentParser parser, String fieldName) throws IOException {
        XContentParser.Token token;
        int counter = 0;
        while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
            if (token == XContentParser.Token.START_OBJECT) {
                serializeObject(settings, sb, path, parser, fieldName + '.' + (counter++));
            } else if (token == XContentParser.Token.START_ARRAY) {
                serializeArray(settings, sb, path, parser, fieldName + '.' + (counter++));
            } else if (token == XContentParser.Token.FIELD_NAME) {
                fieldName = parser.currentName();
            } else if (token == XContentParser.Token.VALUE_NULL) {
                // ignore
            } else {
                serializeValue(settings, sb, path, parser, fieldName + '.' + (counter++));
            }
        }
    }

    private void serializeValue(Map<String, String> settings, StringBuilder sb, List<String> path, XContentParser parser, String fieldName) throws IOException {
        sb.setLength(0);
        for (String pathEle : path) {
            sb.append(pathEle).append('.');
        }
        sb.append(fieldName);
        settings.put(sb.toString(), parser.text());
    }

}
