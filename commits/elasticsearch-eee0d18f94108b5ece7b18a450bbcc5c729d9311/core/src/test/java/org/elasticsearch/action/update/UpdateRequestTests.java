/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package org.elasticsearch.action.update;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.Version;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptService.ScriptType;
import org.elasticsearch.test.ESTestCase;

import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class UpdateRequestTests extends ESTestCase {
    public void testUpdateRequest() throws Exception {
        UpdateRequest request = new UpdateRequest("test", "type", "1");
        // simple script
        request.fromXContent(XContentFactory.jsonBuilder().startObject()
                .field("script", "script1")
                .endObject());
        Script script = request.script();
        assertThat(script, notNullValue());
        assertThat(script.getScript(), equalTo("script1"));
        assertThat(script.getType(), equalTo(ScriptType.INLINE));
        assertThat(script.getLang(), equalTo(Script.DEFAULT_SCRIPT_LANG));
        Map<String, Object> params = script.getParams();
        assertThat(params, nullValue());

        // simple verbose script
        request.fromXContent(XContentFactory.jsonBuilder().startObject()
                .startObject("script").field("inline", "script1").endObject()
                .endObject());
        script = request.script();
        assertThat(script, notNullValue());
        assertThat(script.getScript(), equalTo("script1"));
        assertThat(script.getType(), equalTo(ScriptType.INLINE));
        assertThat(script.getLang(), equalTo(Script.DEFAULT_SCRIPT_LANG));
        params = script.getParams();
        assertThat(params, nullValue());

        // script with params
        request = new UpdateRequest("test", "type", "1");
        request.fromXContent(XContentFactory.jsonBuilder().startObject()
            .startObject("script")
                .field("inline", "script1")
                .startObject("params")
                    .field("param1", "value1")
                .endObject()
            .endObject().endObject());
        script = request.script();
        assertThat(script, notNullValue());
        assertThat(script.getScript(), equalTo("script1"));
        assertThat(script.getType(), equalTo(ScriptType.INLINE));
        assertThat(script.getLang(), equalTo(Script.DEFAULT_SCRIPT_LANG));
        params = script.getParams();
        assertThat(params, notNullValue());
        assertThat(params.size(), equalTo(1));
        assertThat(params.get("param1").toString(), equalTo("value1"));

        request = new UpdateRequest("test", "type", "1");
        request.fromXContent(XContentFactory.jsonBuilder().startObject().startObject("script")
            .startObject("params").field("param1", "value1").endObject()
            .field("inline", "script1").endObject().endObject());
        script = request.script();
        assertThat(script, notNullValue());
        assertThat(script.getScript(), equalTo("script1"));
        assertThat(script.getType(), equalTo(ScriptType.INLINE));
        assertThat(script.getLang(), equalTo(Script.DEFAULT_SCRIPT_LANG));
        params = script.getParams();
        assertThat(params, notNullValue());
        assertThat(params.size(), equalTo(1));
        assertThat(params.get("param1").toString(), equalTo("value1"));

        // script with params and upsert
        request = new UpdateRequest("test", "type", "1");
        request.fromXContent(XContentFactory.jsonBuilder().startObject()
            .startObject("script")
                .startObject("params")
                    .field("param1", "value1")
                .endObject()
                .field("inline", "script1")
            .endObject()
            .startObject("upsert")
                .field("field1", "value1")
                .startObject("compound")
                    .field("field2", "value2")
                .endObject()
            .endObject().endObject());
        script = request.script();
        assertThat(script, notNullValue());
        assertThat(script.getScript(), equalTo("script1"));
        assertThat(script.getType(), equalTo(ScriptType.INLINE));
        assertThat(script.getLang(), equalTo(Script.DEFAULT_SCRIPT_LANG));
        params = script.getParams();
        assertThat(params, notNullValue());
        assertThat(params.size(), equalTo(1));
        assertThat(params.get("param1").toString(), equalTo("value1"));
        Map<String, Object> upsertDoc = XContentHelper.convertToMap(request.upsertRequest().source(), true).v2();
        assertThat(upsertDoc.get("field1").toString(), equalTo("value1"));
        assertThat(((Map) upsertDoc.get("compound")).get("field2").toString(), equalTo("value2"));

        request = new UpdateRequest("test", "type", "1");
        request.fromXContent(XContentFactory.jsonBuilder().startObject()
            .startObject("upsert")
                .field("field1", "value1")
                .startObject("compound")
                    .field("field2", "value2")
                .endObject()
            .endObject()
            .startObject("script")
                .startObject("params")
                    .field("param1", "value1")
                .endObject()
                .field("inline", "script1")
            .endObject().endObject());
        script = request.script();
        assertThat(script, notNullValue());
        assertThat(script.getScript(), equalTo("script1"));
        assertThat(script.getType(), equalTo(ScriptType.INLINE));
        assertThat(script.getLang(), equalTo(Script.DEFAULT_SCRIPT_LANG));
        params = script.getParams();
        assertThat(params, notNullValue());
        assertThat(params.size(), equalTo(1));
        assertThat(params.get("param1").toString(), equalTo("value1"));
        upsertDoc = XContentHelper.convertToMap(request.upsertRequest().source(), true).v2();
        assertThat(upsertDoc.get("field1").toString(), equalTo("value1"));
        assertThat(((Map) upsertDoc.get("compound")).get("field2").toString(), equalTo("value2"));

        // script with doc
        request = new UpdateRequest("test", "type", "1");
        request.fromXContent(XContentFactory.jsonBuilder().startObject()
            .startObject("doc").field("field1", "value1").startObject("compound")
            .field("field2", "value2").endObject().endObject().endObject());
        Map<String, Object> doc = request.doc().sourceAsMap();
        assertThat(doc.get("field1").toString(), equalTo("value1"));
        assertThat(((Map) doc.get("compound")).get("field2").toString(), equalTo("value2"));
    }

    // Related to issue 3256
    public void testUpdateRequestWithTTL() throws Exception {
        TimeValue providedTTLValue = TimeValue.parseTimeValue(randomTimeValue(), null, "ttl");

        UpdateHelper updateHelper = new UpdateHelper(null, logger);

        // We just upsert one document with ttl
        IndexRequest indexRequest = new IndexRequest("test", "type1", "1")
                .source(jsonBuilder().startObject().field("foo", "bar").endObject())
                .ttl(providedTTLValue);
        UpdateRequest updateRequest = new UpdateRequest("test", "type1", "1")
                .doc(jsonBuilder().startObject().field("fooz", "baz").endObject())
                .upsert(indexRequest);

        // We simulate that the document is not existing yet
        GetResult getResult = new GetResult("test", "type1", "1", 0, false, null, null);
        UpdateHelper.Result result = updateHelper.prepare(new ShardId("test", "_na_", 0),updateRequest, getResult);
        Streamable action = result.action();
        assertThat(action, instanceOf(IndexRequest.class));
        IndexRequest indexAction = (IndexRequest) action;
        assertThat(indexAction.ttl(), is(providedTTLValue));

        // We just upsert one document with ttl using a script
        indexRequest = new IndexRequest("test", "type1", "2")
                .source(jsonBuilder().startObject().field("foo", "bar").endObject())
                .ttl(providedTTLValue);
        updateRequest = new UpdateRequest("test", "type1", "2")
                .upsert(indexRequest)
                .script(new Script(";"))
                .scriptedUpsert(true);

        // We simulate that the document is not existing yet
        getResult = new GetResult("test", "type1", "2", 0, false, null, null);
        result = updateHelper.prepare(new ShardId("test", "_na_", 0), updateRequest, getResult);
        action = result.action();
        assertThat(action, instanceOf(IndexRequest.class));
        indexAction = (IndexRequest) action;
        assertThat(indexAction.ttl(), is(providedTTLValue));
    }

    // Related to issue #15822
    public void testInvalidBodyThrowsParseException() throws Exception {
        UpdateRequest request = new UpdateRequest("test", "type", "1");
        try {
            request.fromXContent(new byte[] { (byte) '"' });
            fail("Should have thrown a ElasticsearchParseException");
        } catch (ElasticsearchParseException e) {
            assertThat(e.getMessage(), equalTo("Failed to derive xcontent"));
        }
    }

    // Related to issue 15338
    public void testFieldsParsing() throws Exception {
        UpdateRequest request = new UpdateRequest("test", "type1", "1")
                .fromXContent(new BytesArray("{\"doc\": {\"field1\": \"value1\"}, \"fields\": \"_source\"}"));
        assertThat(request.doc().sourceAsMap().get("field1").toString(), equalTo("value1"));
        assertThat(request.fields(), arrayContaining("_source"));

        request = new UpdateRequest("test", "type2", "2")
                .fromXContent(new BytesArray("{\"doc\": {\"field2\": \"value2\"}, \"fields\": [\"field1\", \"field2\"]}"));
        assertThat(request.doc().sourceAsMap().get("field2").toString(), equalTo("value2"));
        assertThat(request.fields(), arrayContaining("field1", "field2"));
    }

    public void testFetchSourceParsing() throws Exception {
        UpdateRequest request = new UpdateRequest("test", "type1", "1");
        request.fromXContent(
            XContentFactory.jsonBuilder().startObject().field("_source", true).endObject()
        );
        assertThat(request.fetchSource(), notNullValue());
        assertThat(request.fetchSource().includes().length, equalTo(0));
        assertThat(request.fetchSource().excludes().length, equalTo(0));
        assertThat(request.fetchSource().fetchSource(), equalTo(true));

        request.fromXContent(
            XContentFactory.jsonBuilder().startObject().field("_source", false).endObject()
        );
        assertThat(request.fetchSource(), notNullValue());
        assertThat(request.fetchSource().includes().length, equalTo(0));
        assertThat(request.fetchSource().excludes().length, equalTo(0));
        assertThat(request.fetchSource().fetchSource(), equalTo(false));

        request.fromXContent(
            XContentFactory.jsonBuilder().startObject().field("_source", "path.inner.*").endObject()
        );
        assertThat(request.fetchSource(), notNullValue());
        assertThat(request.fetchSource().fetchSource(), equalTo(true));
        assertThat(request.fetchSource().includes().length, equalTo(1));
        assertThat(request.fetchSource().excludes().length, equalTo(0));
        assertThat(request.fetchSource().includes()[0], equalTo("path.inner.*"));

        request.fromXContent(
            XContentFactory.jsonBuilder().startObject()
                .startObject("_source")
                    .field("includes", "path.inner.*")
                    .field("excludes", "another.inner.*")
                .endObject()
            .endObject()
        );
        assertThat(request.fetchSource(), notNullValue());
        assertThat(request.fetchSource().fetchSource(), equalTo(true));
        assertThat(request.fetchSource().includes().length, equalTo(1));
        assertThat(request.fetchSource().excludes().length, equalTo(1));
        assertThat(request.fetchSource().includes()[0], equalTo("path.inner.*"));
        assertThat(request.fetchSource().excludes()[0], equalTo("another.inner.*"));
    }
}
