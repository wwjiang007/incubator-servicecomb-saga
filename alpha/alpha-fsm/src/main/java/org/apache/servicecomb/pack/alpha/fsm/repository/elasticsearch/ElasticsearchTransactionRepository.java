/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.pack.alpha.fsm.repository.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.servicecomb.pack.alpha.core.fsm.repository.model.GlobalTransaction;
import org.apache.servicecomb.pack.alpha.core.fsm.repository.model.PagingGlobalTransactions;
import org.apache.servicecomb.pack.alpha.fsm.metrics.MetricsService;
import org.apache.servicecomb.pack.alpha.fsm.repository.TransactionRepository;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ScrolledPage;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;
import org.springframework.data.elasticsearch.core.query.GetQuery;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;

public class ElasticsearchTransactionRepository implements TransactionRepository {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String INDEX_NAME = "alpha_global_transaction";
  public static final String INDEX_TYPE = "alpha_global_transaction_type";
  private static final long SCROLL_TIMEOUT = 3000;
  private final ElasticsearchTemplate template;
  private final MetricsService metricsService;
  private final ObjectMapper mapper = new ObjectMapper();
  private int batchSize;
  private int batchSizeCounter;
  private int refreshTime;
  private final List<IndexQuery> queries = new ArrayList<>();
  private final Object lock = new Object();

  public ElasticsearchTransactionRepository(
      ElasticsearchTemplate template, MetricsService metricsService, int batchSize,
      int refreshTime) {
    this.template = template;
    this.metricsService = metricsService;
    this.batchSize = batchSize;
    this.refreshTime = refreshTime;
    if (this.refreshTime > 0) {
      new Thread(new RefreshTimer(), "elasticsearch-repository-refresh").start();
    }
    if (!this.template.indexExists(INDEX_NAME)) {
      this.template.createIndex(INDEX_NAME);
    }
  }

  @Override
  public void send(GlobalTransaction transaction) throws Exception {
    synchronized (lock) {
      long begin = System.currentTimeMillis();
      queries.add(convert(transaction));
      batchSizeCounter++;
      metricsService.metrics().doRepositoryReceived();
      if (batchSize == 0 || batchSizeCounter == batchSize) {
        save(begin);
        batchSizeCounter = 0;
      }
    }
  }

  @Override
  public GlobalTransaction getGlobalTransactionByGlobalTxId(String globalTxId) {
    GetQuery getQuery = new GetQuery();
    getQuery.setId(globalTxId);
    GlobalTransactionDocument globalTransaction = this.template
        .queryForObject(getQuery, GlobalTransactionDocument.class);
    return globalTransaction;
  }

  @Override
  public PagingGlobalTransactions getGlobalTransactions(int page, int size) {
    return getGlobalTransactions(null, page, size);
  }

  @Override
  public PagingGlobalTransactions getGlobalTransactions(String state, int page, int size) {
    long start = System.currentTimeMillis();
    List<GlobalTransaction> globalTransactions = new ArrayList();
    QueryBuilder query;
    if (state != null && state.trim().length() > 0) {
      query = QueryBuilders.termQuery("state.keyword", state);
    } else {
      query = QueryBuilders.matchAllQuery();
    }
    SearchQuery searchQuery = new NativeSearchQueryBuilder()
        .withIndices(INDEX_NAME)
        .withTypes(INDEX_TYPE)
        .withQuery(query)
        .withPageable(PageRequest.of(page, size))
        .build();
    ScrolledPage<GlobalTransactionDocument> scroll = (ScrolledPage<GlobalTransactionDocument>) this.template
        .startScroll(SCROLL_TIMEOUT, searchQuery, GlobalTransactionDocument.class,
            searchResultMapper);
    int pageCursor = 0;
    while (scroll.hasContent()) {
      if (pageCursor < page) {
        scroll = (ScrolledPage<GlobalTransactionDocument>) this.template
            .continueScroll(scroll.getScrollId(), SCROLL_TIMEOUT, GlobalTransactionDocument.class,
                searchResultMapper);
        pageCursor++;
      } else {
        for (GlobalTransactionDocument dto : scroll.getContent()) {
          globalTransactions.add(dto);
        }
        break;
      }
    }
    LOG.info("Query total hits {}, return page {}, size {}", scroll.getTotalElements(), page, size);
    this.template.clearScroll(scroll.getScrollId());
    return PagingGlobalTransactions.builder().page(page).size(size).total(scroll.getTotalElements())
        .globalTransactions(globalTransactions).elapsed(System.currentTimeMillis() - start).build();
  }

  public Map<String, Long> getTransactionStatistics() {
    TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders
        .terms("count_group_by_state").field("state.keyword");
    SearchQuery searchQuery = new NativeSearchQueryBuilder()
        .withIndices(INDEX_NAME)
        .addAggregation(termsAggregationBuilder)
        .build();
    return this.template.query(searchQuery, response -> {
      Map<String, Long> statistics = new HashMap<>();
      if (response.getHits().totalHits > 0) {
        final StringTerms groupState = response.getAggregations().get("count_group_by_state");
        statistics = groupState.getBuckets()
            .stream()
            .collect(Collectors.toMap(MultiBucketsAggregation.Bucket::getKeyAsString,
                MultiBucketsAggregation.Bucket::getDocCount));
      }
      return statistics;
    });
  }

  @Override
  public List<GlobalTransaction> getSlowGlobalTransactionsTopN(int n) {
    // ElasticsearchTemplate.prepareScroll() does not add sorting https://jira.spring.io/browse/DATAES-457
    ObjectMapper jsonMapper = new ObjectMapper();
    List<GlobalTransaction> globalTransactions = new ArrayList();
    IndicesStatsResponse indicesStatsResponse = this.template.getClient().admin().indices().prepareStats(INDEX_NAME).get();
    if(indicesStatsResponse.getIndices().get(INDEX_NAME).getTotal().docs.getCount()>0){
      SearchResponse response = this.template.getClient().prepareSearch(INDEX_NAME)
          .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
          .setQuery(QueryBuilders.matchAllQuery())
          .addSort(SortBuilders.fieldSort("durationTime").order(SortOrder.DESC))
          .setFrom(0).setSize(n).setExplain(true)
          .get();
      response.getHits().forEach(hit -> {
        try {
          GlobalTransactionDocument dto = jsonMapper
              .readValue(hit.getSourceAsString(), GlobalTransactionDocument.class);
          globalTransactions.add(dto);
        } catch (Exception e) {
          LOG.error(e.getMessage(), e);
        }
      });
    }
    return globalTransactions;
  }

  private final SearchResultMapper searchResultMapper = new SearchResultMapper() {
    @Override
    public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> aClass,
        Pageable pageable) {
      List<GlobalTransactionDocument> result = new ArrayList<>();
      for (SearchHit hit : response.getHits()) {
        if (response.getHits().getHits().length <= 0) {
          return new AggregatedPageImpl<T>(Collections.EMPTY_LIST, pageable,
              response.getHits().getTotalHits(), response.getScrollId());
        }
        GlobalTransactionDocument globalTransactionDocument = null;
        try {
          globalTransactionDocument = mapper.readValue(hit.getSourceAsString(),
              GlobalTransactionDocument.class);
        } catch (IOException e) {
          throw new RuntimeException(e.getMessage(), e);
        }
        result.add(globalTransactionDocument);
      }
      if (result.isEmpty()) {
        return new AggregatedPageImpl<T>(Collections.EMPTY_LIST, pageable,
            response.getHits().getTotalHits(), response.getScrollId());
      }
      return new AggregatedPageImpl<T>((List<T>) result, pageable,
          response.getHits().getTotalHits(), response.getScrollId());
    }
  };

  private IndexQuery convert(GlobalTransaction transaction) throws JsonProcessingException {
    IndexQuery indexQuery = new IndexQuery();
    indexQuery.setId(transaction.getGlobalTxId());
    indexQuery.setSource(mapper.writeValueAsString(transaction));
    indexQuery.setIndexName(INDEX_NAME);
    indexQuery.setType(INDEX_TYPE);
    return indexQuery;
  }

  private void save(long begin) {
    template.bulkIndex(queries);
    template.refresh(INDEX_NAME);
    metricsService.metrics().doRepositoryAccepted(queries.size());
    long end = System.currentTimeMillis();
    metricsService.metrics().doRepositoryAvgTime((end - begin) / queries.size());
    if (LOG.isDebugEnabled()) {
      LOG.debug("save queries={}, received={}, accepted={}", queries.size(),
          metricsService.metrics().getRepositoryReceived(),
          metricsService.metrics().getRepositoryAccepted());
    }
    queries.clear();
  }

  class RefreshTimer implements Runnable {

    @Override
    public void run() {
      while (true) {
        try {
          synchronized (lock) {
            if (!queries.isEmpty()) {
              save(System.currentTimeMillis());
            }
          }
        } catch (Exception e) {
          LOG.error(e.getMessage(), e);
        } finally {
          try {
            Thread.sleep(refreshTime);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error(e.getMessage(), e);
          }
        }
      }
    }
  }
}
