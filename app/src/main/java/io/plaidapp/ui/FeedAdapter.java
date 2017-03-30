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

package io.plaidapp.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.SharedElementCallback;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.plaidapp.R;
import io.plaidapp.data.DataLoadingSubject;
import io.plaidapp.data.PlaidItem;
import io.plaidapp.data.PlaidItemSorting;
import io.plaidapp.data.api.dribbble.ShotWeigher;
import io.plaidapp.data.api.dribbble.model.Shot;
import io.plaidapp.ui.widget.BadgedFourThreeImageView;
import io.plaidapp.util.ObservableColorMatrix;
import io.plaidapp.util.TransitionUtils;
import io.plaidapp.util.ViewUtils;
import io.plaidapp.util.glide.DribbbleTarget;

import static io.plaidapp.util.AnimUtils.getFastOutSlowInInterpolator;

/**
 * Adapter for displaying a grid of {@link PlaidItem}s.
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
                         implements DataLoadingSubject.DataLoadingCallbacks {

    public static final int REQUEST_CODE_VIEW_SHOT = 5407;

    private static final int TYPE_DRIBBBLE_SHOT = 1;
    private static final int TYPE_LOADING_MORE = -1;

    // we need to hold on to an activity ref for the shared element transitions :/
    private final Activity host;
    private final LayoutInflater layoutInflater;
    //private final PlaidItemSorting.PlaidItemComparator comparator;

    private final @Nullable DataLoadingSubject dataLoading;
    private final int columns;
    private final ColorDrawable[] shotLoadingPlaceholders;
    private final @ColorInt int initialGifBadgeColor;

    private List<PlaidItem> items;
    private boolean showLoadingMore = false;
    //private ShotWeigher shotWeigher;

    public FeedAdapter(Activity hostActivity,
                       DataLoadingSubject dataLoading,
                       int columns) {
        this.host = hostActivity;
        this.dataLoading = dataLoading;
        dataLoading.registerCallback(this);
        this.columns = columns;

        layoutInflater = LayoutInflater.from(host);
        //comparator = new PlaidItemSorting.PlaidItemComparator();
        items = new ArrayList<>();
        setHasStableIds(true);

        // get the dribbble shot placeholder colors & badge color from the theme
        final TypedArray a = host.obtainStyledAttributes(R.styleable.DribbbleFeed);
        final int loadingColorArrayId =
                a.getResourceId(R.styleable.DribbbleFeed_shotLoadingPlaceholderColors, 0);
        if (loadingColorArrayId != 0) {
            int[] placeholderColors = host.getResources().getIntArray(loadingColorArrayId);
            shotLoadingPlaceholders = new ColorDrawable[placeholderColors.length];
            for (int i = 0; i < placeholderColors.length; i++) {
                shotLoadingPlaceholders[i] = new ColorDrawable(placeholderColors[i]);
            }
        } else {
            shotLoadingPlaceholders = new ColorDrawable[] { new ColorDrawable(Color.DKGRAY) };
        }
        final int initialGifBadgeColorId =
                a.getResourceId(R.styleable.DribbbleFeed_initialBadgeColor, 0);
        initialGifBadgeColor = initialGifBadgeColorId != 0 ?
                ContextCompat.getColor(host, initialGifBadgeColorId) : 0x40ffffff;
        a.recycle();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_DRIBBBLE_SHOT:
                return createDribbbleShotHolder(parent);
            case TYPE_LOADING_MORE:
                return new LoadingMoreHolder(
                        layoutInflater.inflate(R.layout.infinite_loading, parent, false));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case TYPE_DRIBBBLE_SHOT:
                bindDribbbleShotHolder(
                        (Shot) getItem(position), (DribbbleShotHolder) holder, position);
                break;
            case TYPE_LOADING_MORE:
                bindLoadingViewHolder((LoadingMoreHolder) holder, position);
                break;
        }
    }

    @Override
    public void onViewRecycled(RecyclerView.ViewHolder holder) {
        if (holder instanceof DribbbleShotHolder) {
            // reset the badge & ripple which are dynamically determined
            DribbbleShotHolder shotHolder = (DribbbleShotHolder) holder;
            //shotHolder.image.setBadgeColor(initialGifBadgeColor);
            //shotHolder.image.showBadge(false);
            shotHolder.image.setForeground(
                    ContextCompat.getDrawable(host, R.drawable.mid_grey_ripple));
        }
    }

    @NonNull
    private DribbbleShotHolder createDribbbleShotHolder(ViewGroup parent) {
        final DribbbleShotHolder holder = new DribbbleShotHolder(
                layoutInflater.inflate(R.layout.dribbble_shot_item, parent, false));
        //holder.image.setBadgeColor(initialGifBadgeColor);

        // play animated GIFs whilst touched
        holder.image.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // check if it's an event we care about, else bail fast
                final int action = event.getAction();
                if (!(action == MotionEvent.ACTION_DOWN
                        || action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL)) return false;

                // get the image and check if it's an animated GIF
                final Drawable drawable = holder.image.getDrawable();
                if (drawable == null) return false;
                GifDrawable gif = null;
                if (drawable instanceof GifDrawable) {
                    gif = (GifDrawable) drawable;
                } else if (drawable instanceof TransitionDrawable) {
                    // we fade in images on load which uses a TransitionDrawable; check its layers
                    TransitionDrawable fadingIn = (TransitionDrawable) drawable;
                    for (int i = 0; i < fadingIn.getNumberOfLayers(); i++) {
                        if (fadingIn.getDrawable(i) instanceof GifDrawable) {
                            gif = (GifDrawable) fadingIn.getDrawable(i);
                            break;
                        }
                    }
                }
                if (gif == null) return false;
                // GIF found, start/stop it on press/lift
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        gif.start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        gif.stop();
                        break;
                }
                return false;
            }
        });
        return holder;
    }

    private void bindDribbbleShotHolder(final Shot shot,
                                        final DribbbleShotHolder holder,
                                        int position) {
        if(position == 0) {
            StaggeredGridLayoutManager.LayoutParams layoutParams = (StaggeredGridLayoutManager.LayoutParams) holder.itemView.getLayoutParams();
            layoutParams.setFullSpan(true);
        }
        final int[] imageSize = shot.images.bestSize();
        Glide.with(host)
                .load(shot.images.best())
                .listener(new RequestListener<String, GlideDrawable>() {

                    @Override
                    public boolean onResourceReady(GlideDrawable resource,
                                                   String model,
                                                   Target<GlideDrawable> target,
                                                   boolean isFromMemoryCache,
                                                   boolean isFirstResource) {
                        if (!shot.hasFadedIn) {
                            holder.image.setHasTransientState(true);
                            final ObservableColorMatrix cm = new ObservableColorMatrix();
                            final ObjectAnimator saturation = ObjectAnimator.ofFloat(
                                    cm, ObservableColorMatrix.SATURATION, 0f, 1f);
                            saturation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener
                                    () {
                                @Override
                                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                    // just animating the color matrix does not invalidate the
                                    // drawable so need this update listener.  Also have to create a
                                    // new CMCF as the matrix is immutable :(
                                    holder.image.setColorFilter(new ColorMatrixColorFilter(cm));
                                }
                            });
                            saturation.setDuration(2000L);
                            saturation.setInterpolator(getFastOutSlowInInterpolator(host));
                            saturation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    holder.image.clearColorFilter();
                                    holder.image.setHasTransientState(false);
                                }
                            });
                            saturation.start();
                            shot.hasFadedIn = true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onException(Exception e, String model, Target<GlideDrawable>
                            target, boolean isFirstResource) {
                        return false;
                    }
                })
                .placeholder(shotLoadingPlaceholders[position % shotLoadingPlaceholders.length])
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .fitCenter()
                .override(imageSize[0], imageSize[1])
                .into(new DribbbleTarget(holder.image, false));
        // need both placeholder & background to prevent seeing through shot as it fades in
        holder.image.setBackground(
                shotLoadingPlaceholders[position % shotLoadingPlaceholders.length]);
        //holder.image.showBadge(shot.animated);
        // need a unique transition name per shot, let's use it's url
        holder.image.setTransitionName(shot.html_url);
    }

    private void bindLoadingViewHolder(LoadingMoreHolder holder, int position) {
        // only show the infinite load progress spinner if there are already items in the
        // grid i.e. it's not the first item & data is being loaded
        holder.progress.setVisibility((position > 0 && dataLoading.isDataLoading())
                ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public int getItemViewType(int position) {
        if (position < getDataItemCount()
                && getDataItemCount() > 0) {
            PlaidItem item = getItem(position);
            if (item instanceof Shot) {
                return TYPE_DRIBBBLE_SHOT;
            }
        }
        return TYPE_LOADING_MORE;
    }

    private PlaidItem getItem(int position) {
        return items.get(position);
    }

    public int getItemColumnSpan(int position) {
        switch (getItemViewType(position)) {
            case TYPE_LOADING_MORE:
                return columns;
            default:
                return getItem(position).colspan;
        }
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    /**
     * Main entry point for adding items to this adapter. Takes care of de-duplicating items and
     * sorting them (depending on the data source). Will also expand some items to span multiple
     * grid columns.
     */
    public void addAndResort(List<? extends PlaidItem> newItems) {
        //weighItems(newItems);
        deduplicateAndAdd(newItems);
        //sort();
        //expandPopularItems();
        //notifyDataSetChanged();
    }

    /**
     * De-dupe as the same item can be returned by multiple feeds
     */
    private void deduplicateAndAdd(List<? extends PlaidItem> newItems) {
        final int count = getDataItemCount();
        for (PlaidItem newItem : newItems) {
            boolean add = true;
            for (int i = 0; i < count; i++) {
                PlaidItem existingItem = getItem(i);
                if (existingItem.equals(newItem)) {
                    add = false;
                    break;
                }
            }
            if (add) {
                add(newItem);
            }
        }
    }

    private void add(PlaidItem item) {
        items.add(item);
        Handler handler = new Handler();
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                notifyItemInserted(items.size() - 1);
            }
        };
        handler.post(r);
    }


    public void removeDataSource(String dataSource) {
        for (int i = items.size() - 1; i >= 0; i--) {
            PlaidItem item = items.get(i);
            if (dataSource.equals(item.dataSource)) {
                items.remove(i);
            }
        }

        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) == TYPE_LOADING_MORE) {
            return -1L;
        }
        return getItem(position).id;
    }

    public int getItemPosition(final long itemId) {
        for (int position = 0; position < items.size(); position++) {
            if (getItem(position).id == itemId) return position;
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemCount() {
        return getDataItemCount() + (showLoadingMore ? 1 : 0);
    }

    /**
     * The shared element transition to dribbble shots & dn stories can intersect with the FAB.
     * This can cause a strange layers-passing-through-each-other effect. On return hide the FAB
     * and animate it back in after the transition.
     */
    private void setGridItemContentTransitions(View gridItem) {
        final View fab = host.findViewById(R.id.fab);
        if (!ViewUtils.viewsIntersect(gridItem, fab)) return;

        Transition reenter = TransitionInflater.from(host)
                .inflateTransition(R.transition.grid_overlap_fab_reenter);
        reenter.addListener(new TransitionUtils.TransitionListenerAdapter() {

            @Override
            public void onTransitionEnd(Transition transition) {
                // we only want these content transitions in certain cases so clear out when done.
                host.getWindow().setReenterTransition(null);
            }
        });
        host.getWindow().setReenterTransition(reenter);
    }

    public int getDataItemCount() {
        return items.size();
    }

    private int getLoadingMoreItemPosition() {
        return showLoadingMore ? getItemCount() - 1 : RecyclerView.NO_POSITION;
    }

    /**
     * Which ViewHolder types require a divider decoration
     */

    @Override
    public void dataStartedLoading() {
        if (showLoadingMore) return;
        showLoadingMore = true;

        Handler handler = new Handler();
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                notifyItemInserted(getLoadingMoreItemPosition());
            }
        };
        handler.post(r);


    }

    @Override
    public void dataFinishedLoading() {
        if (!showLoadingMore) return;
        final int loadingPos = getLoadingMoreItemPosition();
        showLoadingMore = false;
        Handler handler = new Handler();
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                notifyItemRemoved(loadingPos);
            }
        };
        handler.post(r);



    }

    public static SharedElementCallback createSharedElementReenterCallback(
            @NonNull Context context) {
        final String shotTransitionName = context.getString(R.string.transition_shot);
        final String shotBackgroundTransitionName =
                context.getString(R.string.transition_shot_background);
        return new SharedElementCallback() {

            /**
             * We're performing a slightly unusual shared element transition i.e. from one view
             * (image in the grid) to two views (the image & also the background of the details
             * view, to produce the expand effect). After changing orientation, the transition
             * system seems unable to map both shared elements (only seems to map the shot, not
             * the background) so in this situation we manually map the background to the
             * same view.
             */
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                if (sharedElements.size() != names.size()) {
                    // couldn't map all shared elements
                    final View sharedShot = sharedElements.get(shotTransitionName);
                    if (sharedShot != null) {
                        // has shot so add shot background, mapped to same view
                        sharedElements.put(shotBackgroundTransitionName, sharedShot);
                    }
                }
            }
        };
    }

    /* package */ static class DribbbleShotHolder extends RecyclerView.ViewHolder {

        BadgedFourThreeImageView image;

        DribbbleShotHolder(View itemView) {
            super(itemView);
            image = (BadgedFourThreeImageView) itemView;
        }

    }

    /* package */ static class LoadingMoreHolder extends RecyclerView.ViewHolder {

        ProgressBar progress;

        LoadingMoreHolder(View itemView) {
            super(itemView);
            progress = (ProgressBar) itemView;
        }

    }

}
