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

package io.plaidapp.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.plaidapp.R;
import io.plaidapp.data.Source;

/**
 * Manage saving and retrieving data sources from disk.
 */
public class SourceManager {


    public static final String SOURCE_DRIBBBLE_POPULAR = "SOURCE_DRIBBBLE_POPULAR";
    private static final String SOURCES_PREF = "SOURCES_PREF";
    private static final String KEY_SOURCES = "KEY_SOURCES";

    public static List<Source> getSources(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE);
        Set<String> sourceKeys = prefs.getStringSet(KEY_SOURCES, null);
        if (sourceKeys == null) {
            setupDefaultSources(context, prefs.edit());
            return getDefaultSources(context);
        }

        List<Source> sources = new ArrayList<>(sourceKeys.size());
        for (String sourceKey : sourceKeys) {
            if (sourceKey.startsWith(Source.DribbbleSearchSource.DRIBBBLE_QUERY_PREFIX)) {
                sources.add(new Source.DribbbleSearchSource(
                        sourceKey.replace(Source.DribbbleSearchSource.DRIBBBLE_QUERY_PREFIX, ""),
                        prefs.getBoolean(sourceKey, false)));
            } else {
                // TODO improve this O(n2) search
                sources.add(getSource(context, sourceKey, prefs.getBoolean(sourceKey, false)));
            }
        }
        //Collections.sort(sources, new Source.SourceComparator());
        return sources;
    }

    public static void addSource(Source toAdd, Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> sourceKeys = prefs.getStringSet(KEY_SOURCES, null);
        sourceKeys.add(toAdd.key);
        editor.putStringSet(KEY_SOURCES, sourceKeys);
        editor.putBoolean(toAdd.key, toAdd.active);
        editor.apply();
    }

    public static void updateSource(Source source, Context context) {
        SharedPreferences.Editor editor =
                context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE).edit();
        editor.putBoolean(source.key, source.active);
        editor.apply();
    }

    public static void removeSource(Source source, Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> sourceKeys = prefs.getStringSet(KEY_SOURCES, null);
        sourceKeys.remove(source.key);
        editor.putStringSet(KEY_SOURCES, sourceKeys);
        editor.remove(source.key);
        editor.apply();
    }

    private static void setupDefaultSources(Context context, SharedPreferences.Editor editor) {
        ArrayList<Source> defaultSources = getDefaultSources(context);
        Set<String> keys = new HashSet<>(defaultSources.size());
        for (Source source : defaultSources) {
            keys.add(source.key);
            editor.putBoolean(source.key, source.active);
        }
        editor.putStringSet(KEY_SOURCES, keys);
        editor.commit();
    }

    private static @Nullable Source getSource(Context context, String key, boolean active) {
        for (Source source : getDefaultSources(context)) {
            if (source.key.equals(key)) {
                source.active = active;
                return source;
            }
        }
        return null;
    }

    private static ArrayList<Source> getDefaultSources(Context context) {
        ArrayList<Source> defaultSources = new ArrayList<>();
        // 200 sort order range left for DN searches
        //defaultSources.add(new Source.DribbbleSource(SOURCE_DRIBBBLE_POPULAR, 100, context.getString(R.string.source_dribbble_popular), true));
        defaultSources.add(new Source.DribbbleSearchSource(context.getString(R.string
                .source_dribbble_search_material_design), true));
        return defaultSources;
    }

}
