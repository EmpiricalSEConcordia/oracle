package org.apache.lucene.analysis.payloads;
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


import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.index.Payload;

import java.io.IOException;


/**
 * Makes the {@link org.apache.lucene.analysis.Token#type()} a payload.
 *
 * Encodes the type using {@link String#getBytes(String)} with "UTF-8" as the encoding
 *
 **/
public class TypeAsPayloadTokenFilter extends TokenFilter {

  public TypeAsPayloadTokenFilter(TokenStream input) {
    super(input);

  }


  public Token next(Token result) throws IOException {
    result = input.next(result);
    if (result != null && result.type() != null && result.type().equals("") == false){
      result.setPayload(new Payload(result.type().getBytes("UTF-8")));
    }
    return result;
  }
}