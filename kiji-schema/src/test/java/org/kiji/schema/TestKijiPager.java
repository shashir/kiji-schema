/**
 * (c) Copyright 2013 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Iterator;
import java.util.NavigableMap;

import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kiji.schema.KijiDataRequestBuilder.ColumnsDef;
import org.kiji.schema.filter.RegexQualifierColumnFilter;
import org.kiji.schema.filter.TestKijiPaginationFilter;
import org.kiji.schema.impl.KijiColumnPagingNotEnabledException;
import org.kiji.schema.layout.KijiTableLayouts;
import org.kiji.schema.util.ResourceUtils;

public class TestKijiPager extends KijiClientTest {
  private static final Logger LOG = LoggerFactory.getLogger(TestKijiPaginationFilter.class);
  /** Objects to reinstantiate between tests. */
  private KijiTableReader mReader;
  private KijiTable mTable;

  @Before
  public void setupInstance() throws Exception {
    final Kiji kiji = getKiji();
    kiji.createTable(KijiTableLayouts.getLayout(KijiTableLayouts.PAGING_TEST));

    mTable = kiji.openTable("user");
    mReader = mTable.openTableReader();
  }

  @After
  public void teardown() throws IOException {
    ResourceUtils.closeOrLog(mReader);
    ResourceUtils.releaseOrLog(mTable);
  }

  @Test(expected=KijiColumnPagingNotEnabledException.class)
  public void testColumnPagingNotEnabled() throws IOException {
    KijiDataRequestBuilder builder = KijiDataRequest.builder();
    builder.newColumnsDef().withMaxVersions(5).add("info", "name");
    final KijiDataRequest dataRequest = builder.build();
    assertTrue(!dataRequest.isEmpty());
    assertTrue(!dataRequest.isPagingEnabled());
    EntityId meId = mTable.getEntityId(Bytes.toBytes("me"));
    KijiRowData myRowData = mReader.get(meId, dataRequest);
    myRowData.getPager("info", "name");
  }

  /* Test that a pager retrieved for a group type column family acts as expected. */
  @Test
  public void testGroupTypeColumnPaging() throws IOException {
    EntityId id = mTable.getEntityId("me");
    final KijiTableWriter writer = mTable.openTableWriter();
    writer.put(id, "info", "name", 1L, "me");
    writer.put(id, "info", "name", 2L, "me-too");
    writer.put(id, "info", "name", 3L, "me-three");
    writer.put(id, "info", "name", 4L, "me-four");
    writer.put(id, "info", "name", 5L, "me-five");
    ResourceUtils.closeOrLog(writer);

    KijiDataRequestBuilder builder = KijiDataRequest.builder();
    builder.newColumnsDef().withMaxVersions(5).withPageSize(2).add("info", "name");
    final KijiDataRequest dataRequest = builder.build();
    assertTrue(!dataRequest.isEmpty());
    assertTrue(dataRequest.isPagingEnabled());
    assertTrue(dataRequest.getColumn("info", "name").isPagingEnabled());
    EntityId meId = mTable.getEntityId(Bytes.toBytes("me"));
    KijiRowData myRowData = mReader.get(meId, dataRequest);
    KijiPager pager = myRowData.getPager("info", "name");
    assertTrue(pager.hasNext());

    final NavigableMap<Long, CharSequence> resultMap = pager.next().getValues("info", "name");
    assertEquals("The number of returned values is incorrect: ", 2, resultMap.size());
    assertEquals("Incorrect first value of first page:", "me-five", resultMap.get(5L).toString());
    assertEquals("Incorrect second value of first page:", "me-four", resultMap.get(4L).toString());
    assertTrue(pager.hasNext());
    final NavigableMap<Long, CharSequence> resultMap2 = pager.next().getValues("info", "name");
    assertEquals("The number of returned values is incorrect: ", 2 , resultMap2.size());
    assertEquals("Incorrect first value of second page:", "me-three",
        resultMap2.get(3L).toString());
    assertEquals("Incorrect second value of second page:", "me-too", resultMap2.get(2L).toString());

    assertTrue(pager.hasNext());
    final NavigableMap<Long, CharSequence> resultMap3 = pager.next().getValues("info", "name");
    assertEquals("The number of returned values is incorrect: ", 1 , resultMap3.size());
    assertEquals("Incorrect first value of second page:", "me", resultMap3.get(1L).toString());
    ResourceUtils.closeOrLog(pager);
  }

  /* Test that a pager retrieved for a group type column family acts as expected. */
  @Test
  public void testGroupTypeColumnPagingFromScan() throws IOException {
    final EntityId id = mTable.getEntityId("me");
    final KijiTableWriter writer = mTable.openTableWriter();
    try {
      writer.put(id, "info", "name", 1L, "me");
      writer.put(id, "info", "name", 2L, "me-too");
      writer.put(id, "info", "name", 3L, "me-three");
      writer.put(id, "info", "name", 4L, "me-four");
      writer.put(id, "info", "name", 5L, "me-five");
    } finally {
      writer.close();
    }

    final KijiDataRequest dataRequest = KijiDataRequest.builder()
        .addColumns(ColumnsDef.create().withMaxVersions(5).withPageSize(2).add("info", "name"))
        .build();
    assertTrue(!dataRequest.isEmpty());
    assertTrue(dataRequest.isPagingEnabled());
    assertTrue(dataRequest.getColumn("info", "name").isPagingEnabled());

    final KijiRowScanner scanner = mReader.getScanner(dataRequest);
    try {
      final Iterator<KijiRowData> iterator = scanner.iterator();
      assertTrue(iterator.hasNext());
      final KijiRowData myRowData = iterator.next();
      final KijiPager pager = myRowData.getPager("info", "name");
      assertTrue(pager.hasNext());

      final NavigableMap<Long, CharSequence> resultMap = pager.next().getValues("info", "name");
      assertEquals("The number of returned values is incorrect: ", 2, resultMap.size());
      assertEquals("Incorrect first value of first page:", "me-five", resultMap.get(5L).toString());
      assertEquals("Incorrect second value of first page:", "me-four",
          resultMap.get(4L).toString());
      assertTrue(pager.hasNext());
      final NavigableMap<Long, CharSequence> resultMap2 = pager.next().getValues("info", "name");
      assertEquals("The number of returned values is incorrect: ", 2 , resultMap2.size());
      assertEquals("Incorrect first value of second page:", "me-three",
          resultMap2.get(3L).toString());
      assertEquals("Incorrect second value of second page:", "me-too",
          resultMap2.get(2L).toString());

      assertTrue(pager.hasNext());
      final NavigableMap<Long, CharSequence> resultMap3 = pager.next().getValues("info", "name");
      assertEquals("The number of returned values is incorrect: ", 1 , resultMap3.size());
      assertEquals("Incorrect first value of second page:", "me", resultMap3.get(1L).toString());
      ResourceUtils.closeOrLog(pager);

      assertTrue(!iterator.hasNext());
    } finally {
      scanner.close();
    }
  }

  @Test
  public void testMapTypeColumnPaging() throws IOException {
    final EntityId id = mTable.getEntityId("me");
    final KijiTableWriter writer = mTable.openTableWriter();
    writer.put(id, "jobs", "e", 1L, "always coming in 5th");
    writer.put(id, "jobs", "d", 2L, "always coming in 4th");
    writer.put(id, "jobs", "c", 3L, "always coming in 3rd");
    writer.put(id, "jobs", "b", 4L, "always coming in 2nd");
    writer.put(id, "jobs", "a", 5L, "always coming in 1st");
    ResourceUtils.closeOrLog(writer);

    final KijiDataRequestBuilder builder = KijiDataRequest.builder();
    builder.newColumnsDef().withMaxVersions(5).withPageSize(2).addFamily("jobs");
    final KijiDataRequest dataRequest = builder.build();
    assertTrue(!dataRequest.isEmpty());
    assertTrue(dataRequest.isPagingEnabled());
    assertTrue(dataRequest.getColumn("jobs", null).isPagingEnabled());
    final EntityId meId = mTable.getEntityId(Bytes.toBytes("me"));
    final KijiRowData myRowData = mReader.get(meId, dataRequest);
    final KijiPager pager = myRowData.getPager("jobs");
    assertTrue(pager.hasNext());

    final NavigableMap<String, NavigableMap<Long, CharSequence>> resultMap
        = pager.next().getValues("jobs");
    assertEquals("The number of returned values is incorrect: ", 2, resultMap.entrySet().size());
    assertEquals("Incorrect first value of first page:", "always coming in 1st",
        resultMap.get("a").get(5L).toString());
    assertEquals("Incorrect second value of first page:", "always coming in 2nd",
        resultMap.get("b").get(4L).toString());
    assertTrue(pager.hasNext());
    final NavigableMap<String, NavigableMap<Long, CharSequence>> resultMap2
        = pager.next().getValues("jobs");
    assertEquals("The number of returned values is incorrect: ", 2 , resultMap2.entrySet().size());
    assertEquals("Incorrect first value of second page:", "always coming in 3rd",
        resultMap2.get("c").get(3L).toString());
    assertEquals("Incorrect second value of second page:", "always coming in 4th",
        resultMap2.get("d").get(2L).toString());

    assertTrue(pager.hasNext());
    final NavigableMap<String, NavigableMap<Long, CharSequence>> resultMap3
        = pager.next().getValues("jobs");
    assertEquals("The number of returned values is incorrect: ", 1 , resultMap3.entrySet().size());
    assertEquals("Incorrect first value of second page:", "always coming in 5th",
        resultMap3.get("e").get(1L).toString());
    assertTrue(!pager.hasNext());
    ResourceUtils.closeOrLog(pager);
  }

 /** Test that paging does not clobber user defined filters. */
  @Test
  public void testUserDefinedFilters() throws IOException {
    final EntityId id = mTable.getEntityId("me");
    final KijiTableWriter writer = mTable.openTableWriter();
    writer.put(id, "jobs", "b", 1L, "always coming in 5th");
    writer.put(id, "jobs", "b", 2L, "always coming in 4th");
    writer.put(id, "jobs", "b", 3L, "always coming in 3rd");
    writer.put(id, "jobs", "a", 4L, "always coming in 2nd");
    writer.put(id, "jobs", "a", 5L, "always coming in 1st");
    ResourceUtils.closeOrLog(writer);

    final KijiDataRequestBuilder builder = KijiDataRequest.builder();
    builder.newColumnsDef().withMaxVersions(5).withPageSize(2)
        .withFilter(new RegexQualifierColumnFilter("b")).addFamily("jobs");
    KijiPager pager = null;
    try {
      final KijiDataRequest dataRequest = builder.build();
      assertTrue(!dataRequest.isEmpty());
      assertTrue(dataRequest.isPagingEnabled());
      assertTrue(dataRequest.getColumn("jobs", null).isPagingEnabled());
      final EntityId meId = mTable.getEntityId(Bytes.toBytes("me"));
      LOG.debug("DataRequest is [{}]", dataRequest.toString());
      final KijiRowData myRowData = mReader.get(meId, dataRequest);
      pager = myRowData.getPager("jobs");
      assertTrue(pager.hasNext());
      final NavigableMap<String, NavigableMap<Long, CharSequence>> resultMap
          = pager.next().getValues("jobs");
      assertEquals("The number of returned values is incorrect: ", 2, resultMap.get("b").size());
      assertEquals("Incorrect first value of first page:", "always coming in 3rd",
          resultMap.get("b").get(3L).toString());
      assertEquals("Incorrect second value of first page:", "always coming in 4th",
          resultMap.get("b").get(2L).toString());

      assertTrue(pager.hasNext());
      final NavigableMap<String, NavigableMap<Long, CharSequence>> resultMap2
          = pager.next().getValues("jobs");
          assertEquals("The number of returned values is incorrect: ", 1, resultMap2.get("b")
              .size());
          assertEquals("Incorrect first value of second page:", "always coming in 5th",
          resultMap2.get("b").get(1L).toString());
      assertTrue(!pager.hasNext());
    } finally {
      ResourceUtils.closeOrLog(pager);
    }
  }
}
