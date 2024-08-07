package org.apache.lucene.facet.search.params;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.index.FacetFields;
import org.apache.lucene.facet.index.params.CategoryListParams;
import org.apache.lucene.facet.index.params.PerDimensionIndexingParams;
import org.apache.lucene.facet.search.CategoryListIterator;
import org.apache.lucene.facet.search.PayloadCategoryListIteraor;
import org.apache.lucene.facet.search.cache.CategoryListCache;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.facet.util.MultiCategoryListIterator;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.encoding.IntDecoder;
import org.junit.Test;

/*
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

public class MultiCategoryListIteratorTest extends LuceneTestCase {

  @Test
  public void testMultipleCategoryLists() throws Exception {
    Random random = random();
    int numDimensions = atLeast(random, 2); // at least 2 dimensions
    String[] dimensions = new String[numDimensions];
    for (int i = 0; i < numDimensions; i++) {
      dimensions[i] = "dim" + i;
    }
    
    // build the PerDimensionIndexingParams
    HashMap<CategoryPath,CategoryListParams> clps = new HashMap<CategoryPath,CategoryListParams>();
    for (String dim : dimensions) {
      CategoryPath cp = new CategoryPath(dim);
      CategoryListParams clp = new CategoryListParams(new Term("$" + dim, CategoryListParams.DEFAULT_TERM.bytes()));
      clps.put(cp, clp);
    }
    PerDimensionIndexingParams indexingParams = new PerDimensionIndexingParams(clps);
    
    // index some documents
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter indexWriter = new IndexWriter(indexDir, newIndexWriterConfig(TEST_VERSION_CURRENT, null).setMaxBufferedDocs(2));
    TaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    FacetFields facetFields = new FacetFields(taxoWriter, indexingParams);
    int ndocs = atLeast(random, 10);
    for (int i = 0; i < ndocs; i++) {
      Document doc = new Document();
      int numCategories = random.nextInt(numDimensions) + 1;
      ArrayList<CategoryPath> categories = new ArrayList<CategoryPath>();
      for (int j = 0; j < numCategories; j++) {
        String dimension = dimensions[random.nextInt(dimensions.length)];
        categories.add(new CategoryPath(dimension, Integer.toString(i)));
      }
      facetFields.addFields(doc, categories);
      indexWriter.addDocument(doc);
    }
    IOUtils.close(indexWriter, taxoWriter);
    
    // test the multi iterator
    CategoryListCache clCache = null;
    if (random.nextBoolean()) {
      clCache = new CategoryListCache();
    }
    
    DirectoryReader indexReader = DirectoryReader.open(indexDir);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir);
    CategoryListIterator[] iterators = new CategoryListIterator[numDimensions];
    for (int i = 0; i < iterators.length; i++) {
      CategoryListParams clp = indexingParams.getCategoryListParams(new CategoryPath(dimensions[i]));
      IntDecoder decoder = clp.createEncoder().createMatchingDecoder();
      if (clCache != null && random.nextBoolean()) {
        clCache.loadAndRegister(clp, indexReader, taxoReader, indexingParams);
        iterators[i] = clCache.get(clp).iterator(0); // no partitions
      } else {
        iterators[i] = new PayloadCategoryListIteraor(indexReader, clp.getTerm(), decoder);
      }
    }
    MultiCategoryListIterator cli = new MultiCategoryListIterator(iterators);
    assertTrue("failed to init multi-iterator", cli.init());
    IntsRef ordinals = new IntsRef();
    int maxDoc = indexReader.maxDoc();
    for (int i = 0; i < maxDoc; i++) {
      cli.getOrdinals(i, ordinals);
      assertTrue("document " + i + " does not have categories", ordinals.length > 0);
      for (int j = 0; j < ordinals.length; j++) {
        CategoryPath cp = taxoReader.getPath(ordinals.ints[j]);
        assertNotNull("ordinal " + ordinals.ints[j] + " not found in taxonomy", cp);
        if (cp.length == 2) {
          assertEquals("invalid category for document " + i, i, Integer.parseInt(cp.components[1]));
        }
      }
    }
    
    IOUtils.close(indexReader, taxoReader);
    IOUtils.close(indexDir, taxoDir);
  }
  
}