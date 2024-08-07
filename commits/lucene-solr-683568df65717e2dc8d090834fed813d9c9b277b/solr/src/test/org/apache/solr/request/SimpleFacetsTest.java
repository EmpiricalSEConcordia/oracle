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

package org.apache.solr.request;

import org.apache.solr.SolrTestCaseJ4;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;


public class SimpleFacetsTest extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml","schema.xml");
    createIndex();
  }

  static Random rand = new Random(); // TODO: a way to use lucene's newRandom()?
  static int random_commit_percent = 30;
  static int random_dupe_percent = 25;   // some duplicates in the index to create deleted docs

  static void randomCommit(int percent_chance) {
    if (rand.nextInt(100) <= percent_chance)
      assertU(commit());
  }

  static ArrayList<String[]> pendingDocs = new ArrayList<String[]>();

  // committing randomly gives different looking segments each time
  static void add_doc(String... fieldsAndValues) {
    do {
      pendingDocs.add(fieldsAndValues);      
    } while (rand.nextInt(100) <= random_dupe_percent);

    // assertU(adoc(fieldsAndValues));
    // randomCommit(random_commit_percent);
  }


  static void createIndex() {
    indexSimpleFacetCounts();
    indexDateFacets();
    indexFacetSingleValued();
    indexFacetPrefixMultiValued();
    indexFacetPrefixSingleValued();
    
   Collections.shuffle(pendingDocs, rand);
    for (String[] doc : pendingDocs) {
      assertU(adoc(doc));
      randomCommit(random_commit_percent);
    }
    assertU(commit());
  }

  static void indexSimpleFacetCounts() {
    add_doc("id", "42", "trait_s", "Tool", "trait_s", "Obnoxious",
                 "name", "Zapp Brannigan");
    add_doc("id", "43" ,
                 "title", "Democratic Order of Planets");
    add_doc("id", "44", "trait_s", "Tool",
                 "name", "The Zapper");
    add_doc("id", "45", "trait_s", "Chauvinist",
                 "title", "25 star General");
    add_doc("id", "46", "trait_s", "Obnoxious",
                 "subject", "Defeated the pacifists of the Gandhi nebula");
    add_doc("id", "47", "trait_s", "Pig",
                 "text", "line up and fly directly at the enemy death cannons, clogging them with wreckage!");   
  }

  @Test
  public void testSimpleFacetCounts() {
 
    assertQ("standard request handler returns all matches",
            req("id:[42 TO 47]"),
            "*[count(//doc)=6]"
            );
 
    assertQ("filter results using fq",
            req("q","id:[42 TO 46]",
                "fq", "id:[43 TO 47]"),
            "*[count(//doc)=4]"
            );
    
    assertQ("don't filter results using blank fq",
            req("q","id:[42 TO 46]",
                "fq", " "),
            "*[count(//doc)=5]"
            );
     
    assertQ("filter results using multiple fq params",
            req("q","id:[42 TO 46]",
                "fq", "trait_s:Obnoxious",
                "fq", "id:[43 TO 47]"),
            "*[count(//doc)=1]"
            );
 
    assertQ("check counts for facet queries",
            req("q", "id:[42 TO 47]"
                ,"facet", "true"
                ,"facet.query", "trait_s:Obnoxious"
                ,"facet.query", "id:[42 TO 45]"
                ,"facet.query", "id:[43 TO 47]"
                ,"facet.field", "trait_s"
                )
            ,"*[count(//doc)=6]"
 
            ,"//lst[@name='facet_counts']/lst[@name='facet_queries']"
            ,"//lst[@name='facet_queries']/int[@name='trait_s:Obnoxious'][.='2']"
            ,"//lst[@name='facet_queries']/int[@name='id:[42 TO 45]'][.='4']"
            ,"//lst[@name='facet_queries']/int[@name='id:[43 TO 47]'][.='5']"
 
            ,"//lst[@name='facet_counts']/lst[@name='facet_fields']"
            ,"//lst[@name='facet_fields']/lst[@name='trait_s']"
            ,"*[count(//lst[@name='trait_s']/int)=4]"
            ,"//lst[@name='trait_s']/int[@name='Tool'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Obnoxious'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Pig'][.='1']"
            );

    assertQ("check multi-select facets with naming",
            req("q", "id:[42 TO 47]"
                ,"facet", "true"
                ,"facet.query", "{!ex=1}trait_s:Obnoxious"
                ,"facet.query", "{!ex=2 key=foo}id:[42 TO 45]"    // tag=2 same as 1
                ,"facet.query", "{!ex=3,4 key=bar}id:[43 TO 47]"  // tag=3,4 don't exist
                ,"facet.field", "{!ex=3,1}trait_s"                // 3,1 same as 1
                ,"fq", "{!tag=1,2}id:47"                          // tagged as 1 and 2
                )
            ,"*[count(//doc)=1]"

            ,"//lst[@name='facet_counts']/lst[@name='facet_queries']"
            ,"//lst[@name='facet_queries']/int[@name='{!ex=1}trait_s:Obnoxious'][.='2']"
            ,"//lst[@name='facet_queries']/int[@name='foo'][.='4']"
            ,"//lst[@name='facet_queries']/int[@name='bar'][.='1']"

            ,"//lst[@name='facet_counts']/lst[@name='facet_fields']"
            ,"//lst[@name='facet_fields']/lst[@name='trait_s']"
            ,"*[count(//lst[@name='trait_s']/int)=4]"
            ,"//lst[@name='trait_s']/int[@name='Tool'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Obnoxious'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Pig'][.='1']"
            );

    assertQ("check counts for applied facet queries using filtering (fq)",
            req("q", "id:[42 TO 47]"
                ,"facet", "true"
                ,"fq", "id:[42 TO 45]"
                ,"facet.field", "trait_s"
                ,"facet.query", "id:[42 TO 45]"
                ,"facet.query", "id:[43 TO 47]"
                )
            ,"*[count(//doc)=4]"
            ,"//lst[@name='facet_counts']/lst[@name='facet_queries']"
            ,"//lst[@name='facet_queries']/int[@name='id:[42 TO 45]'][.='4']"
            ,"//lst[@name='facet_queries']/int[@name='id:[43 TO 47]'][.='3']"
            ,"*[count(//lst[@name='trait_s']/int)=4]"
            ,"//lst[@name='trait_s']/int[@name='Tool'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Obnoxious'][.='1']"
            ,"//lst[@name='trait_s']/int[@name='Chauvinist'][.='1']"
            ,"//lst[@name='trait_s']/int[@name='Pig'][.='0']"
            );
 
    assertQ("check counts with facet.zero=false&facet.missing=true using fq",
            req("q", "id:[42 TO 47]"
                ,"facet", "true"
                ,"facet.zeros", "false"
                ,"f.trait_s.facet.missing", "true"
                ,"fq", "id:[42 TO 45]"
                ,"facet.field", "trait_s"
                )
            ,"*[count(//doc)=4]"
            ,"*[count(//lst[@name='trait_s']/int)=4]"
            ,"//lst[@name='trait_s']/int[@name='Tool'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Obnoxious'][.='1']"
            ,"//lst[@name='trait_s']/int[@name='Chauvinist'][.='1']"
            ,"//lst[@name='trait_s']/int[not(@name)][.='1']"
            );

    assertQ("check counts with facet.mincount=1&facet.missing=true using fq",
            req("q", "id:[42 TO 47]"
                ,"facet", "true"
                ,"facet.mincount", "1"
                ,"f.trait_s.facet.missing", "true"
                ,"fq", "id:[42 TO 45]"
                ,"facet.field", "trait_s"
                )
            ,"*[count(//doc)=4]"
            ,"*[count(//lst[@name='trait_s']/int)=4]"
            ,"//lst[@name='trait_s']/int[@name='Tool'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Obnoxious'][.='1']"
            ,"//lst[@name='trait_s']/int[@name='Chauvinist'][.='1']"
            ,"//lst[@name='trait_s']/int[not(@name)][.='1']"
            );

    assertQ("check counts with facet.mincount=2&facet.missing=true using fq",
            req("q", "id:[42 TO 47]"
                ,"facet", "true"
                ,"facet.mincount", "2"
                ,"f.trait_s.facet.missing", "true"
                ,"fq", "id:[42 TO 45]"
                ,"facet.field", "trait_s"
                )
            ,"*[count(//doc)=4]"
            ,"*[count(//lst[@name='trait_s']/int)=2]"
            ,"//lst[@name='trait_s']/int[@name='Tool'][.='2']"
            ,"//lst[@name='trait_s']/int[not(@name)][.='1']"               
            );

    assertQ("check sorted paging",
            req("q", "id:[42 TO 47]"
                ,"facet", "true"
                ,"fq", "id:[42 TO 45]"
                ,"facet.field", "trait_s"
                ,"facet.mincount","0"
                ,"facet.offset","0"
                ,"facet.limit","4"
                )
            ,"*[count(//lst[@name='trait_s']/int)=4]"
            ,"//lst[@name='trait_s']/int[@name='Tool'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Obnoxious'][.='1']"
            ,"//lst[@name='trait_s']/int[@name='Chauvinist'][.='1']"
            ,"//lst[@name='trait_s']/int[@name='Pig'][.='0']"
            );

    assertQ("check sorted paging",
            req("q", "id:[42 TO 47]"
                ,"facet", "true"
                ,"fq", "id:[42 TO 45]"
                ,"facet.field", "trait_s"
                ,"facet.mincount","0"
                ,"facet.offset","0"
                ,"facet.limit","3"
                )
            ,"*[count(//lst[@name='trait_s']/int)=3]"
            ,"//lst[@name='trait_s']/int[@name='Tool'][.='2']"
            ,"//lst[@name='trait_s']/int[@name='Obnoxious'][.='1']"
            ,"//lst[@name='trait_s']/int[@name='Chauvinist'][.='1']"
            );

  }

  public static void indexDateFacets() {
    final String i = "id";
    final String f = "bday";
    final String ff = "a_tdt";
    final String ooo = "00:00:00.000Z";
    final String xxx = "15:15:15.155Z";

    add_doc(i, "201",  f, "1976-07-04T12:08:56.235Z", ff, "1900-01-01T"+ooo);
    add_doc(i, "202",  f, "1976-07-05T00:00:00.000Z", ff, "1976-07-01T"+ooo);
    add_doc(i, "203",  f, "1976-07-15T00:07:67.890Z", ff, "1976-07-04T"+ooo);
    add_doc(i, "204",  f, "1976-07-21T00:07:67.890Z", ff, "1976-07-05T"+ooo);
    add_doc(i, "205",  f, "1976-07-13T12:12:25.255Z", ff, "1976-07-05T"+xxx);
    add_doc(i, "206",  f, "1976-07-03T17:01:23.456Z", ff, "1976-07-07T"+ooo);
    add_doc(i, "207",  f, "1976-07-12T12:12:25.255Z", ff, "1976-07-13T"+ooo);
    add_doc(i, "208",  f, "1976-07-15T15:15:15.155Z", ff, "1976-07-13T"+xxx);
    add_doc(i, "209",  f, "1907-07-12T13:13:23.235Z", ff, "1976-07-15T"+xxx);
    add_doc(i, "2010", f, "1976-07-03T11:02:45.678Z", ff, "2000-01-01T"+ooo);
    add_doc(i, "2011", f, "1907-07-12T12:12:25.255Z");
    add_doc(i, "2012", f, "2007-07-30T07:07:07.070Z");
    add_doc(i, "2013", f, "1976-07-30T22:22:22.222Z");
    add_doc(i, "2014", f, "1976-07-05T22:22:22.222Z");
  }

  @Test
  public void testDateFacets() {
    final String f = "bday";
    final String pre = "//lst[@name='facet_dates']/lst[@name='"+f+"']";

    assertQ("check counts for month of facet by day",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-01T00:00:00.000Z+1MONTH"
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                )
            // 31 days + pre+post+inner = 34
            ,"*[count("+pre+"/int)=34]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='0'  ]"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0'  ]"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='2'  ]"
            // july4th = 2 because exists doc @ 00:00:00.000 on July5
            // (date faceting is inclusive)
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-13T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-14T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-15T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-16T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-17T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-18T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-19T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-21T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-22T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-23T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-24T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-25T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-26T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-27T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-28T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-29T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-30T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-31T00:00:00Z'][.='0']"
            
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='1']"
            ,pre+"/int[@name='between'][.='11']"
            
            );

    assertQ("check counts for month of facet by day with global mincount = 1",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-01T00:00:00.000Z+1MONTH"
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.mincount", "1"
                )
            // 31 days + pre+post+inner = 34
            ,"*[count("+pre+"/int)=11]"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='2'  ]"
            // july4th = 2 because exists doc @ 00:00:00.000 on July5
            // (date faceting is inclusive)
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-13T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-15T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-21T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-30T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='1']"
            ,pre+"/int[@name='between'][.='11']"
            );

    assertQ("check counts for month of facet by day with field mincount = 1",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-01T00:00:00.000Z+1MONTH"
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"f." + f + ".facet.mincount", "2"
                )
            // 31 days + pre+post+inner = 34
            ,"*[count("+pre+"/int)=7]"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='2'  ]"
            // july4th = 2 because exists doc @ 00:00:00.000 on July5
            // (date faceting is inclusive)
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-15T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='1']"
            ,pre+"/int[@name='between'][.='11']"
            );

    assertQ("check before is not inclusive of upper bound by default",
            req("q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start",  "1976-07-05T00:00:00.000Z"
                ,"facet.date.end",    "1976-07-07T00:00:00.000Z"
                ,"facet.date.gap",    "+1DAY"
                ,"facet.date.other",  "all"
                )
            // 2 gaps + pre+post+inner = 5
            ,"*[count("+pre+"/int)=5]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='0'  ]"
            
            ,pre+"/int[@name='before' ][.='5']"
            );
    assertQ("check after is not inclusive of lower bound by default",
            req("q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start",  "1976-07-03T00:00:00.000Z"
                ,"facet.date.end",    "1976-07-05T00:00:00.000Z"
                ,"facet.date.gap",    "+1DAY"
                ,"facet.date.other",  "all"
                )
            // 2 gaps + pre+post+inner = 5
            ,"*[count("+pre+"/int)=5]"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='2'  ]"
            
            ,pre+"/int[@name='after' ][.='8']"
            );
            

    assertQ("check hardend=false",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start",  "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",    "1976-07-13T00:00:00.000Z"
                ,"facet.date.gap",    "+5DAYS"
                ,"facet.date.other",  "all"
                ,"facet.date.hardend","false"
                )
            // 3 gaps + pre+post+inner = 6
            ,"*[count("+pre+"/int)=6]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='5'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='0'  ]"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='4'  ]"
            
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='3']"
            ,pre+"/int[@name='between'][.='9']"
            );

    assertQ("check hardend=true",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start",  "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",    "1976-07-13T00:00:00.000Z"
                ,"facet.date.gap",    "+5DAYS"
                ,"facet.date.other",  "all"
                ,"facet.date.hardend","true"
                )
            // 3 gaps + pre+post+inner = 6
            ,"*[count("+pre+"/int)=6]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='5'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='0'  ]"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='1'  ]"
            
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='6']"
            ,pre+"/int[@name='between'][.='6']"
            );
    
  }

  /** similar to testDateFacets, but a differnet field with test data 
      exactly on on boundary marks */
  @Test
  public void testDateFacetsWithIncludeOption() {
    final String f = "a_tdt";
    final String pre = "//lst[@name='facet_dates']/lst[@name='"+f+"']";

    assertQ("checking counts for lower",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-16T00:00:00.000Z"
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "lower"
                )
            // 15 days + pre+post+inner = 18
            ,"*[count("+pre+"/int)=18]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-13T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-14T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-15T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='1']"
            ,pre+"/int[@name='after'  ][.='1']"
            ,pre+"/int[@name='between'][.='8']"
            );

    assertQ("checking counts for upper",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-16T00:00:00.000Z"
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "upper"
                )
            // 15 days + pre+post+inner = 18
            ,"*[count("+pre+"/int)=18]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-13T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-14T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-15T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='1']"
            ,pre+"/int[@name='between'][.='7']"
            );

    assertQ("checking counts for lower & upper",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-16T00:00:00.000Z"
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "lower"
                ,"facet.date.include", "upper"
                )
            // 15 days + pre+post+inner = 18
            ,"*[count("+pre+"/int)=18]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-13T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-14T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-15T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='1']"
            ,pre+"/int[@name='after'  ][.='1']"
            ,pre+"/int[@name='between'][.='8']"
            );

    assertQ("checking counts for upper & edge",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-16T00:00:00.000Z"
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "upper"
                ,"facet.date.include", "edge"
                )
            // 15 days + pre+post+inner = 18
            ,"*[count("+pre+"/int)=18]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-13T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-14T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-15T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='1']"
            ,pre+"/int[@name='after'  ][.='1']"
            ,pre+"/int[@name='between'][.='8']"
            );

    assertQ("checking counts for upper & outer",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-13T00:00:00.000Z" // smaller now
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "upper"
                ,"facet.date.include", "outer"
                )
            // 12 days + pre+post+inner = 15
            ,"*[count("+pre+"/int)=15]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='4']"
            ,pre+"/int[@name='between'][.='5']"
            );

    assertQ("checking counts for lower & edge",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-13T00:00:00.000Z" // smaller now
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "lower"
                ,"facet.date.include", "edge"
                )
            // 12 days + pre+post+inner = 15
            ,"*[count("+pre+"/int)=15]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='1']"
            ,pre+"/int[@name='after'  ][.='3']"
            ,pre+"/int[@name='between'][.='6']"
            );

    assertQ("checking counts for lower & outer",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-13T00:00:00.000Z" // smaller now
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "lower"
                ,"facet.date.include", "outer"
                )
            // 12 days + pre+post+inner = 15
            ,"*[count("+pre+"/int)=15]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='0']"
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='4']"
            ,pre+"/int[@name='between'][.='5']"
            );

    assertQ("checking counts for lower & edge & outer",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-13T00:00:00.000Z" // smaller now
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "lower"
                ,"facet.date.include", "edge"
                ,"facet.date.include", "outer"
                )
            // 12 days + pre+post+inner = 15
            ,"*[count("+pre+"/int)=15]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='4']"
            ,pre+"/int[@name='between'][.='6']"
            );

    assertQ("checking counts for all",
            req( "q", "*:*"
                ,"rows", "0"
                ,"facet", "true"
                ,"facet.date", f
                ,"facet.date.start", "1976-07-01T00:00:00.000Z"
                ,"facet.date.end",   "1976-07-13T00:00:00.000Z" // smaller now
                ,"facet.date.gap",   "+1DAY"
                ,"facet.date.other", "all"
                ,"facet.date.include", "all"
                )
            // 12 days + pre+post+inner = 15
            ,"*[count("+pre+"/int)=15]"
            ,pre+"/int[@name='1976-07-01T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-02T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-03T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-04T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-05T00:00:00Z'][.='2'  ]"
            ,pre+"/int[@name='1976-07-06T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-07T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='1976-07-08T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-09T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-10T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-11T00:00:00Z'][.='0']"
            ,pre+"/int[@name='1976-07-12T00:00:00Z'][.='1'  ]"
            ,pre+"/int[@name='before' ][.='2']"
            ,pre+"/int[@name='after'  ][.='4']"
            ,pre+"/int[@name='between'][.='6']"
            );
  }

  static void indexFacetSingleValued() {
    indexFacets("40","t_s1");
  }

  @Test
  public void testFacetSingleValued() {
    doFacets("t_s1");
  }
  @Test
  public void testFacetSingleValuedFcs() {
    doFacets("t_s1","facet.method","fcs");
  }

  static void indexFacets(String idPrefix, String f) {
    add_doc("id", idPrefix+"1",  f, "A");
    add_doc("id", idPrefix+"2",  f, "B");
    add_doc("id", idPrefix+"3",  f, "C");
    add_doc("id", idPrefix+"4",  f, "C");
    add_doc("id", idPrefix+"5",  f, "D");
    add_doc("id", idPrefix+"6",  f, "E");
    add_doc("id", idPrefix+"7",  f, "E");
    add_doc("id", idPrefix+"8",  f, "E");
    add_doc("id", idPrefix+"9",  f, "F");
    add_doc("id", idPrefix+"10", f, "G");
    add_doc("id", idPrefix+"11", f, "G");
    add_doc("id", idPrefix+"12", f, "G");
    add_doc("id", idPrefix+"13", f, "G");
    add_doc("id", idPrefix+"14", f, "G");
  }

  public void doFacets(String f, String... params) {
    String pre = "//lst[@name='"+f+"']";
    String notc = "id:[* TO *] -"+f+":C";


    assertQ("check counts for unlimited facet",
            req(params, "q", "id:[* TO *]", "indent","true"
                ,"facet", "true"
                ,"facet.field", f
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=7]"

            ,pre+"/int[@name='G'][.='5']"
            ,pre+"/int[@name='E'][.='3']"
            ,pre+"/int[@name='C'][.='2']"

            ,pre+"/int[@name='A'][.='1']"
            ,pre+"/int[@name='B'][.='1']"
            ,pre+"/int[@name='D'][.='1']"
            ,pre+"/int[@name='F'][.='1']"
            );

    assertQ("check counts for facet with generous limit",
            req(params, "q", "id:[* TO *]"
                ,"facet", "true"
                ,"facet.limit", "100"
                ,"facet.field", f
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=7]"

            ,pre+"/int[1][@name='G'][.='5']"
            ,pre+"/int[2][@name='E'][.='3']"
            ,pre+"/int[3][@name='C'][.='2']"

            ,pre+"/int[@name='A'][.='1']"
            ,pre+"/int[@name='B'][.='1']"
            ,pre+"/int[@name='D'][.='1']"
            ,pre+"/int[@name='F'][.='1']"
            );

    assertQ("check counts for limited facet",
            req(params, "q", "id:[* TO *]"
                ,"facet", "true"
                ,"facet.limit", "2"
                ,"facet.field", f
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=2]"

            ,pre+"/int[1][@name='G'][.='5']"
            ,pre+"/int[2][@name='E'][.='3']"
            );

   assertQ("check offset",
            req(params, "q", "id:[* TO *]"
                ,"facet", "true"
                ,"facet.offset", "1"
                ,"facet.limit", "1"
                ,"facet.field", f
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=1]"

            ,pre+"/int[1][@name='E'][.='3']"
            );

    assertQ("test sorted facet paging with zero (don't count in limit)",
            req(params, "q", "id:[* TO *]"
                ,"fq",notc
                ,"facet", "true"
                ,"facet.field", f
                ,"facet.mincount","1"
                ,"facet.offset","0"
                ,"facet.limit","6"
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=6]"
            ,pre+"/int[1][@name='G'][.='5']"
            ,pre+"/int[2][@name='E'][.='3']"
            ,pre+"/int[3][@name='A'][.='1']"
            ,pre+"/int[4][@name='B'][.='1']"
            ,pre+"/int[5][@name='D'][.='1']"
            ,pre+"/int[6][@name='F'][.='1']"
            );

    assertQ("test sorted facet paging with zero (test offset correctness)",
            req(params, "q", "id:[* TO *]"
                ,"fq",notc
                ,"facet", "true"
                ,"facet.field", f
                ,"facet.mincount","1"
                ,"facet.offset","3"
                ,"facet.limit","2"
                ,"facet.sort","count"
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=2]"
            ,pre+"/int[1][@name='B'][.='1']"
            ,pre+"/int[2][@name='D'][.='1']"
            );

   assertQ("test facet unsorted paging",
            req(params, "q", "id:[* TO *]"
                ,"fq",notc
                ,"facet", "true"
                ,"facet.field", f
                ,"facet.mincount","1"
                ,"facet.offset","0"
                ,"facet.limit","6"
                ,"facet.sort","index"
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=6]"
            ,pre+"/int[1][@name='A'][.='1']"
            ,pre+"/int[2][@name='B'][.='1']"
            ,pre+"/int[3][@name='D'][.='1']"
            ,pre+"/int[4][@name='E'][.='3']"
            ,pre+"/int[5][@name='F'][.='1']"
            ,pre+"/int[6][@name='G'][.='5']"
            );

   assertQ("test facet unsorted paging",
            req(params, "q", "id:[* TO *]"
                ,"fq",notc
                ,"facet", "true"
                ,"facet.field", f
                ,"facet.mincount","1"
                ,"facet.offset","3"
                ,"facet.limit","2"
                ,"facet.sort","index"
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=2]"
            ,pre+"/int[1][@name='E'][.='3']"
            ,pre+"/int[2][@name='F'][.='1']"
            );

    assertQ("test facet unsorted paging, mincount=2",
            req(params, "q", "id:[* TO *]"
                ,"fq",notc
                ,"facet", "true"
                ,"facet.field", f
                ,"facet.mincount","2"
                ,"facet.offset","1"
                ,"facet.limit","2"
                ,"facet.sort","index"
                )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=1]"
            ,pre+"/int[1][@name='G'][.='5']"
            );
  }


  static void indexFacetPrefixMultiValued() {
    indexFacetPrefix("50","t_s");
  }

  @Test
  public void testFacetPrefixMultiValued() {
    doFacetPrefix("t_s", null, "facet.method","enum");
    doFacetPrefix("t_s", null, "facet.method", "enum", "facet.enum.cache.minDf", "3");
    doFacetPrefix("t_s", null, "facet.method", "enum", "facet.enum.cache.minDf", "100");
    doFacetPrefix("t_s", null, "facet.method", "fc");
  }

  static void indexFacetPrefixSingleValued() {
    indexFacetPrefix("60","tt_s1");
  }

  @Test
  public void testFacetPrefixSingleValued() {
    doFacetPrefix("tt_s1", null);
  }
  @Test
  public void testFacetPrefixSingleValuedFcs() {
    doFacetPrefix("tt_s1", null, "facet.method","fcs");
    doFacetPrefix("tt_s1", "{!threads=0}", "facet.method","fcs");   // direct execution
    doFacetPrefix("tt_s1", "{!threads=-1}", "facet.method","fcs");  // default / unlimited threads
    doFacetPrefix("tt_s1", "{!threads=2}", "facet.method","fcs");   // specific number of threads
  }


  static void indexFacetPrefix(String idPrefix, String f) {
    add_doc("id", idPrefix+"1",  f, "AAA");
    add_doc("id", idPrefix+"2",  f, "B");
    add_doc("id", idPrefix+"3",  f, "BB");
    add_doc("id", idPrefix+"4",  f, "BB");
    add_doc("id", idPrefix+"5",  f, "BBB");
    add_doc("id", idPrefix+"6",  f, "BBB");
    add_doc("id", idPrefix+"7",  f, "BBB");
    add_doc("id", idPrefix+"8",  f, "CC");
    add_doc("id", idPrefix+"9",  f, "CC");
    add_doc("id", idPrefix+"10", f, "CCC");
    add_doc("id", idPrefix+"11", f, "CCC");
    add_doc("id", idPrefix+"12", f, "CCC");
    assertU(commit());
  }

  public void doFacetPrefix(String f, String local, String... params) {
    String indent="on";
    String pre = "//lst[@name='"+f+"']";
    String notc = "id:[* TO *] -"+f+":C";
    String lf = local==null ? f : local+f;


    assertQ("test facet.prefix middle, exact match first term",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","B"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=3]"
            ,pre+"/int[1][@name='BBB'][.='3']"
            ,pre+"/int[2][@name='BB'][.='2']"
            ,pre+"/int[3][@name='B'][.='1']"
    );

    assertQ("test facet.prefix middle, exact match first term, unsorted",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","index"
                    ,"facet.prefix","B"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=3]"
            ,pre+"/int[1][@name='B'][.='1']"
            ,pre+"/int[2][@name='BB'][.='2']"
            ,pre+"/int[3][@name='BBB'][.='3']"
    );


     assertQ("test facet.prefix middle, exact match first term, unsorted",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","index"
                    ,"facet.prefix","B"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=3]"
            ,pre+"/int[1][@name='B'][.='1']"
            ,pre+"/int[2][@name='BB'][.='2']"
            ,pre+"/int[3][@name='BBB'][.='3']"
    );


    assertQ("test facet.prefix middle, paging",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","1"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","B"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=2]"
            ,pre+"/int[1][@name='BB'][.='2']"
            ,pre+"/int[2][@name='B'][.='1']"
    );

    assertQ("test facet.prefix middle, paging",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","1"
                    ,"facet.limit","1"
                    ,"facet.sort","count"
                    ,"facet.prefix","B"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=1]"
            ,pre+"/int[1][@name='BB'][.='2']"
    );

    assertQ("test facet.prefix middle, paging",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","1"
                    ,"facet.limit","1"
                    ,"facet.sort","count"
                    ,"facet.prefix","B"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=1]"
            ,pre+"/int[1][@name='BB'][.='2']"
    );

    assertQ("test facet.prefix end, not exact match",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","C"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=2]"
            ,pre+"/int[1][@name='CCC'][.='3']"
            ,pre+"/int[2][@name='CC'][.='2']"
    );

    assertQ("test facet.prefix end, exact match",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","CC"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=2]"
            ,pre+"/int[1][@name='CCC'][.='3']"
            ,pre+"/int[2][@name='CC'][.='2']"
    );

    assertQ("test facet.prefix past end",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","X"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=0]"
    );

    assertQ("test facet.prefix past end",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","1"
                    ,"facet.limit","-1"
                    ,"facet.sort","count"
                    ,"facet.prefix","X"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=0]"
    );

    assertQ("test facet.prefix at start, exact match",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","AAA"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=1]"
            ,pre+"/int[1][@name='AAA'][.='1']"
    );
    assertQ("test facet.prefix at Start, not exact match",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","AA"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=1]"
            ,pre+"/int[1][@name='AAA'][.='1']"
    );
    assertQ("test facet.prefix at Start, not exact match",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","AA"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=1]"
            ,pre+"/int[1][@name='AAA'][.='1']"
    );    
    assertQ("test facet.prefix before start",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","0"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","999"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=0]"
    );

    assertQ("test facet.prefix before start",
            req(params, "q", "id:[* TO *]"
                    ,"indent",indent
                    ,"facet","true"
                    ,"facet.field", lf
                    ,"facet.mincount","0"
                    ,"facet.offset","2"
                    ,"facet.limit","100"
                    ,"facet.sort","count"
                    ,"facet.prefix","999"
            )
            ,"*[count(//lst[@name='facet_fields']/lst/int)=0]"
    );

  }
}
