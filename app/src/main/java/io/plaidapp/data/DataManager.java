/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.plaidapp.data;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.plaidapp.data.api.dribbble.DribbbleSearchService;
import io.plaidapp.data.api.dribbble.model.Shot;
import io.plaidapp.ui.FilterAdapter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Responsible for loading data from the various sources. Instantiating classes are responsible for
 * providing the {code onDataLoaded} method to do something with the data.
 */
public abstract class DataManager extends BaseDataManager<List<? extends PlaidItem>> {

    private final FilterAdapter filterAdapter;
    private Map<String, Integer> pageIndexes;
    private Map<String, Call> inflight;

    public DataManager(Context context,
                       FilterAdapter filterAdapter) {
        super(context);
        this.filterAdapter = filterAdapter;
        filterAdapter.registerFilterChangedCallback(filterListener);
        setupPageIndexes();
        inflight = new HashMap<>();
    }

    public void loadAllDataSources() {
        for (Source filter : filterAdapter.getFilters()) {
            loadSource(filter);
        }
    }

    @Override
    public void cancelLoading() {
        if (inflight.size() > 0) {
            for (Call call : inflight.values()) {
                call.cancel();
            }
            inflight.clear();
        }
    }

    private final FilterAdapter.FiltersChangedCallbacks filterListener =
            new FilterAdapter.FiltersChangedCallbacks() {
        @Override
        public void onFiltersChanged(Source changedFilter) {
            if (changedFilter.active) {
                loadSource(changedFilter);
            } else { // filter deactivated
                final String key = changedFilter.key;
                if (inflight.containsKey(key)) {
                    final Call call = inflight.get(key);
                    if (call != null) call.cancel();
                    inflight.remove(key);
                }
                // clear the page index for the source
                pageIndexes.put(key, 0);
            }
        }
    };

    private void loadSource(Source source) {
        if (source.active) {
            loadStarted();
            final int page = getNextPageIndex(source.key);
            loadDribbbleSearch((Source.DribbbleSearchSource) source, page);
        }
    }

    private void setupPageIndexes() {
        final List<Source> dateSources = filterAdapter.getFilters();
        Log.v("datasize", String.valueOf(dateSources.size()));
        pageIndexes = new HashMap<>(dateSources.size());
        for (Source source : dateSources) {
            pageIndexes.put(source.key, 0);
        }
    }

    private int getNextPageIndex(String dataSource) {
        int nextPage = 1; // default to one – i.e. for newly added sources
        if (pageIndexes.containsKey(dataSource)) {
            nextPage = pageIndexes.get(dataSource) + 1;
        }
        pageIndexes.put(dataSource, nextPage);
        return nextPage;
    }

    private boolean sourceIsEnabled(String key) {
        return pageIndexes.get(key) != 0;
    }

    private void sourceLoaded(List<? extends PlaidItem> data, int page, String key) {
        loadFinished();
        if (data != null && !data.isEmpty() && sourceIsEnabled(key)) {
            setPage(data, page);
            setDataSource(data, key);
            onDataLoaded(data);
        }
        inflight.remove(key);
    }

    private void loadFailed(String key) {
        loadFinished();
        inflight.remove(key);
    }

    private void loadDribbbleSearch(final Source.DribbbleSearchSource source, final int page) {
        final Call<List<Shot>> searchCall = getDribbbleSearchApi().search(source.query, page,
                DribbbleSearchService.PER_PAGE_DEFAULT, DribbbleSearchService.SORT_RECENT);
        searchCall.enqueue(new Callback<List<Shot>>() {
            @Override
            public void onResponse(Call<List<Shot>> call, Response<List<Shot>> response) {
                if (response.isSuccessful()) {
                    sourceLoaded(response.body(), page, source.key);
                } else {
                    loadFailed(source.key);
                }
            }

            @Override
            public void onFailure(Call<List<Shot>> call, Throwable t) {
                loadFailed(source.key);
            }
        });
        inflight.put(source.key, searchCall);
    }

}
