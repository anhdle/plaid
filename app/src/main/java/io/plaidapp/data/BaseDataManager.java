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
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.plaidapp.BuildConfig;
import io.plaidapp.data.api.DenvelopingConverter;
import io.plaidapp.data.api.dribbble.DribbbleSearchConverter;
import io.plaidapp.data.api.dribbble.DribbbleSearchService;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Base class for loading data; extending types are responsible for providing implementations of
 * {@link #onDataLoaded(Object)} to do something with the data and {@link #cancelLoading()} to
 * cancel any activity.
 */
public abstract class BaseDataManager<T> implements DataLoadingSubject {

    private final AtomicInteger loadingCount;
    private DribbbleSearchService dribbbleSearchApi;
    private List<DataLoadingSubject.DataLoadingCallbacks> loadingCallbacks;

    public BaseDataManager(@NonNull Context context) {
        loadingCount = new AtomicInteger(0);
    }

    public abstract void onDataLoaded(T data);

    public abstract void cancelLoading();

    @Override
    public boolean isDataLoading() {
        return loadingCount.get() > 0;
    }

    public DribbbleSearchService getDribbbleSearchApi() {
        if (dribbbleSearchApi == null) createDribbbleSearchApi();
        return dribbbleSearchApi;
    }

    @Override
    public void registerCallback(DataLoadingSubject.DataLoadingCallbacks callback) {
        if (loadingCallbacks == null) {
            loadingCallbacks = new ArrayList<>(1);
        }
        loadingCallbacks.add(callback);
    }

    @Override
    public void unregisterCallback(DataLoadingSubject.DataLoadingCallbacks callback) {
        if (loadingCallbacks != null && loadingCallbacks.contains(callback)) {
            loadingCallbacks.remove(callback);
        }
    }

    protected void loadStarted() {
        Log.v("loadingcountstartbef", String.valueOf(loadingCount.get()));
        if (0 == loadingCount.getAndIncrement()) {
            dispatchLoadingStartedCallbacks();
        }

        Log.v("loadingcountstartaf", String.valueOf(loadingCount.get()));

    }

    protected void loadFinished() {
        Log.v("loadingcountendbef", String.valueOf(loadingCount.get()));
        if (0 == loadingCount.decrementAndGet()) {
            dispatchLoadingFinishedCallbacks();
        }
        Log.v("loadingcountendaf", String.valueOf(loadingCount.get()));
    }

    protected void resetLoadingCount() {
        loadingCount.set(0);
    }

    protected static void setPage(List<? extends PlaidItem> items, int page) {
        for (PlaidItem item : items) {
            item.page = page;
        }
    }

    protected static void setDataSource(List<? extends PlaidItem> items, String dataSource) {
        for (PlaidItem item : items) {
            item.dataSource = dataSource;
        }
    }

    protected void dispatchLoadingStartedCallbacks() {
        if (loadingCallbacks == null || loadingCallbacks.isEmpty()) return;
        for (DataLoadingCallbacks loadingCallback : loadingCallbacks) {
            loadingCallback.dataStartedLoading();
        }
    }

    protected void dispatchLoadingFinishedCallbacks() {
        if (loadingCallbacks == null || loadingCallbacks.isEmpty()) return;
        for (DataLoadingCallbacks loadingCallback : loadingCallbacks) {
            loadingCallback.dataFinishedLoading();
        }
    }

    private void createDribbbleSearchApi() {
        dribbbleSearchApi = new Retrofit.Builder()
                .baseUrl(DribbbleSearchService.ENDPOINT)
                .addConverterFactory(new DribbbleSearchConverter.Factory())
                .build()
                .create((DribbbleSearchService.class));
    }

}
