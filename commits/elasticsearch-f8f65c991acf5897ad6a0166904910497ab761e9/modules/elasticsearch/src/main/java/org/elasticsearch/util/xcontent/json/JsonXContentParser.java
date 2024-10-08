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

package org.elasticsearch.util.xcontent.json;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.util.xcontent.XContentParser;
import org.elasticsearch.util.xcontent.XContentType;
import org.elasticsearch.util.xcontent.support.AbstractXContentParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * @author kimchy (shay.banon)
 */
public class JsonXContentParser extends AbstractXContentParser {

    private final JsonParser parser;

    public JsonXContentParser(JsonParser parser) {
        this.parser = parser;
    }

    @Override public XContentType contentType() {
        return XContentType.JSON;
    }

    @Override public Token nextToken() throws IOException {
        return convertToken(parser.nextToken());
    }

    @Override public void skipChildren() throws IOException {
        parser.skipChildren();
    }

    @Override public Token currentToken() {
        return convertToken(parser.getCurrentToken());
    }

    @Override public NumberType numberType() throws IOException {
        return convertNumberType(parser.getNumberType());
    }

    @Override public String currentName() throws IOException {
        return parser.getCurrentName();
    }

    @Override protected boolean doBooleanValue() throws IOException {
        return parser.getBooleanValue();
    }

    @Override public String text() throws IOException {
        return parser.getText();
    }

    @Override public char[] textCharacters() throws IOException {
        return parser.getTextCharacters();
    }

    @Override public int textLength() throws IOException {
        return parser.getTextLength();
    }

    @Override public int textOffset() throws IOException {
        return parser.getTextOffset();
    }

    @Override public Number numberValue() throws IOException {
        return parser.getNumberValue();
    }

    @Override public byte byteValue() throws IOException {
        return parser.getByteValue();
    }

    @Override public short doShortValue() throws IOException {
        return parser.getShortValue();
    }

    @Override public int doIntValue() throws IOException {
        return parser.getIntValue();
    }

    @Override public long doLongValue() throws IOException {
        return parser.getLongValue();
    }

    @Override public BigInteger bigIntegerValue() throws IOException {
        return parser.getBigIntegerValue();
    }

    @Override public float doFloatValue() throws IOException {
        return parser.getFloatValue();
    }

    @Override public double doDoubleValue() throws IOException {
        return parser.getDoubleValue();
    }

    @Override public BigDecimal decimalValue() throws IOException {
        return parser.getDecimalValue();
    }

    @Override public byte[] binaryValue() throws IOException {
        return parser.getBinaryValue();
    }

    @Override public void close() {
        try {
            parser.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private NumberType convertNumberType(JsonParser.NumberType numberType) {
        switch (numberType) {
            case INT:
                return NumberType.INT;
            case LONG:
                return NumberType.LONG;
            case FLOAT:
                return NumberType.FLOAT;
            case DOUBLE:
                return NumberType.DOUBLE;
            case BIG_DECIMAL:
                return NumberType.BIG_DECIMAL;
            case BIG_INTEGER:
                return NumberType.BIG_INTEGER;
        }
        throw new ElasticSearchIllegalStateException("No matching token for number_type [" + numberType + "]");
    }

    private Token convertToken(JsonToken token) {
        if (token == null) {
            return null;
        }
        switch (token) {
            case FIELD_NAME:
                return Token.FIELD_NAME;
            case VALUE_FALSE:
            case VALUE_TRUE:
                return Token.VALUE_BOOLEAN;
            case VALUE_STRING:
                return Token.VALUE_STRING;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return Token.VALUE_NUMBER;
            case VALUE_NULL:
                return Token.VALUE_NULL;
            case START_OBJECT:
                return Token.START_OBJECT;
            case END_OBJECT:
                return Token.END_OBJECT;
            case START_ARRAY:
                return Token.START_ARRAY;
            case END_ARRAY:
                return Token.END_ARRAY;
        }
        throw new ElasticSearchIllegalStateException("No matching token for json_token [" + token + "]");
    }
}
