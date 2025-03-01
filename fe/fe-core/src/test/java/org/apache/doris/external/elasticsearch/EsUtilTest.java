// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.external.elasticsearch;

import org.apache.doris.catalog.Column;
import org.apache.doris.catalog.EsTable;
import org.apache.doris.catalog.PrimitiveType;
import org.apache.doris.common.ExceptionChecker;

import mockit.Expectations;
import mockit.Injectable;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Test for es util.
 **/
public class EsUtilTest extends EsTestCase {

    private List<Column> columns = new ArrayList<>();

    private String jsonStr = "{\"settings\": {\n"
            + "               \"index\": {\n"
            + "                  \"bpack\": {\n"
            + "                     \"partition\": {\n"
            + "                        \"upperbound\": \"12\"\n"
            + "                     }\n"
            + "                  },\n"
            + "                  \"number_of_shards\": \"5\",\n"
            + "                  \"provided_name\": \"indexa\",\n"
            + "                  \"creation_date\": \"1539328532060\",\n"
            + "                  \"number_of_replicas\": \"1\",\n"
            + "                  \"uuid\": \"plNNtKiiQ9-n6NpNskFzhQ\",\n"
            + "                  \"version\": {\n"
            + "                     \"created\": \"5050099\"\n"
            + "                  }\n"
            + "               }\n"
            + "            }}";

    /**
     * Init columns.
     **/
    @Before
    public void setUp() {
        Column k1 = new Column("k1", PrimitiveType.BIGINT);
        Column k2 = new Column("k2", PrimitiveType.VARCHAR);
        Column k3 = new Column("k3", PrimitiveType.VARCHAR);
        columns.add(k1);
        columns.add(k2);
        columns.add(k3);
    }

    @Test
    public void testExtractFieldsNormal() throws Exception {

        // ES version < 7.0
        EsTable esTableBefore7X = fakeEsTable("fake", "test", "doc", columns);
        SearchContext searchContext = new SearchContext(esTableBefore7X);
        EsUtil.resolveFields(searchContext, loadJsonFromFile("data/es/test_index_mapping.json"));
        Assert.assertEquals("k3.keyword", searchContext.fetchFieldsContext().get("k3"));
        Assert.assertEquals("k3.keyword", searchContext.docValueFieldsContext().get("k3"));
        Assert.assertEquals("k1", searchContext.docValueFieldsContext().get("k1"));
        Assert.assertEquals("k2", searchContext.docValueFieldsContext().get("k2"));

        // ES version >= 7.0
        EsTable esTableAfter7X = fakeEsTable("fake", "test", "_doc", columns);
        SearchContext searchContext1 = new SearchContext(esTableAfter7X);
        EsUtil.resolveFields(searchContext1, loadJsonFromFile("data/es/test_index_mapping_after_7x.json"));
        Assert.assertEquals("k3.keyword", searchContext1.fetchFieldsContext().get("k3"));
        Assert.assertEquals("k3.keyword", searchContext1.docValueFieldsContext().get("k3"));
        Assert.assertEquals("k1", searchContext1.docValueFieldsContext().get("k1"));
        Assert.assertEquals("k2", searchContext1.docValueFieldsContext().get("k2"));
    }

    @Test
    public void testTypeNotExist() throws Exception {
        EsTable table = fakeEsTable("fake", "test", "not_exists", columns);
        SearchContext searchContext = new SearchContext(table);
        // type not exists
        ExceptionChecker.expectThrows(DorisEsException.class,
                () -> EsUtil.resolveFields(searchContext, loadJsonFromFile("data/es/test_index_mapping.json")));
    }

    @Test
    public void testWorkFlow(@Injectable EsRestClient client) throws Exception {
        EsTable table = fakeEsTable("fake", "test", "doc", columns);
        SearchContext searchContext1 = new SearchContext(table);
        String jsonMapping = loadJsonFromFile("data/es/test_index_mapping.json");
        new Expectations(client) {
            {
                client.getMapping(anyString);
                minTimes = 0;
                result = jsonMapping;
            }
        };
        MappingPhase mappingPhase = new MappingPhase(client);
        ExceptionChecker.expectThrowsNoException(() -> mappingPhase.execute(searchContext1));
        ExceptionChecker.expectThrowsNoException(() -> mappingPhase.postProcess(searchContext1));
        Assert.assertEquals("k3.keyword", searchContext1.fetchFieldsContext().get("k3"));
        Assert.assertEquals("k3.keyword", searchContext1.docValueFieldsContext().get("k3"));
        Assert.assertEquals("k1", searchContext1.docValueFieldsContext().get("k1"));
        Assert.assertEquals("k2", searchContext1.docValueFieldsContext().get("k2"));

    }

    @Test
    public void testMultTextFields() throws Exception {
        EsTable esTableAfter7X = fakeEsTable("fake", "test", "_doc", columns);
        SearchContext searchContext = new SearchContext(esTableAfter7X);
        EsUtil.resolveFields(searchContext, loadJsonFromFile("data/es/test_index_mapping_field_mult_analyzer.json"));
        Assert.assertFalse(searchContext.docValueFieldsContext().containsKey("k3"));
    }

    @Test
    public void testGetJsonObject() {
        JSONObject json = (JSONObject) JSONValue.parse(jsonStr);
        JSONObject upperBoundSetting = EsUtil.getJsonObject(json, "settings.index.bpack.partition", 0);
        Assert.assertTrue(upperBoundSetting.containsKey("upperbound"));
        Assert.assertEquals("12", (String) upperBoundSetting.get("upperbound"));

        JSONObject unExistKey = EsUtil.getJsonObject(json, "set", 0);
        Assert.assertNull(unExistKey);

        JSONObject singleKey = EsUtil.getJsonObject(json, "settings", 0);
        Assert.assertTrue(singleKey.containsKey("index"));
    }

    @Test(expected = ClassCastException.class)
    public void testGetJsonObjectWithException() {
        JSONObject json = (JSONObject) JSONValue.parse(jsonStr);
        // only support json object could not get string value directly from this api, exception will be threw
        EsUtil.getJsonObject(json, "settings.index.bpack.partition.upperbound", 0);
    }

}
