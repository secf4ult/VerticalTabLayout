package io.secf4ult.verticaltablayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.lang.ref.WeakReference;

import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING;
import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE;
import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_SETTLING;

public final class VerticalTabLayoutMediator {
    @NonNull
    private final VerticalTabLayout tabLayout;
    @NonNull
    private final ViewPager2 viewPager;
    private final boolean autoRefresh;
    private final TabConfigurationStrategy tabConfigurationStrategy;
    @Nullable
    private RecyclerView.Adapter<?> adapter;
    private boolean attached;

    @Nullable
    private VerticalTabLayoutOnPageChangeCallback onPageChangeCallback;
    @Nullable
    private VerticalTabLayout.OnTabSelectedListener onTabSelectedListener;
    @Nullable
    private RecyclerView.AdapterDataObserver pagerAdapterObserver;

    public interface TabConfigurationStrategy {
        void onConfigureTab(@NonNull VerticalTabLayout.Tab tab, int position);
    }

    public VerticalTabLayoutMediator(
            @NonNull VerticalTabLayout tabLayout,
            @NonNull ViewPager2 viewPager,
            @NonNull TabConfigurationStrategy tabConfigurationStrategy) {
        this(tabLayout, viewPager, true, tabConfigurationStrategy);
    }

    public VerticalTabLayoutMediator(
            @NonNull VerticalTabLayout tabLayout,
            @NonNull ViewPager2 viewPager,
            boolean autoRefresh,
            @NonNull TabConfigurationStrategy tabConfigurationStrategy) {
        this.tabLayout = tabLayout;
        this.viewPager = viewPager;
        this.autoRefresh = autoRefresh;
        this.tabConfigurationStrategy = tabConfigurationStrategy;
    }

    public void attach() {
        if (attached) {
            throw new IllegalStateException("TabLayoutMediator is already attached");
        }
        adapter = viewPager.getAdapter();
        if (adapter == null) {
            throw new IllegalStateException(
                    "TabLayoutMediator attached before ViewPager2 has an " + "adapter");
        }
        attached = true;

        // Add our custom OnPageChangeCallback to the ViewPager
        onPageChangeCallback = new VerticalTabLayoutOnPageChangeCallback(tabLayout);
        viewPager.registerOnPageChangeCallback(onPageChangeCallback);

        // Now we'll add a tab selected listener to set ViewPager's current item
        onTabSelectedListener = new ViewPagerOnTabSelectedListener(viewPager);
        tabLayout.addOnTabSelectedListener(onTabSelectedListener);

        // Now we'll populate ourselves from the pager adapter, adding an observer if
        // autoRefresh is enabled
        if (autoRefresh) {
            // Register our observer on the new adapter
            pagerAdapterObserver = new PagerAdapterObserver();
            adapter.registerAdapterDataObserver(pagerAdapterObserver);
        }

        populateTabsFromPagerAdapter();

        // Now update the scroll position to match the ViewPager's current item
        tabLayout.setScrollPosition(viewPager.getCurrentItem(), 0f, true);
    }

    public void detach() {
        if (autoRefresh && adapter != null) {
            adapter.unregisterAdapterDataObserver(pagerAdapterObserver);
            pagerAdapterObserver = null;
        }
        tabLayout.removeOnTabSelectedListener(onTabSelectedListener);
        viewPager.unregisterOnPageChangeCallback(onPageChangeCallback);
        onTabSelectedListener = null;
        onPageChangeCallback = null;
        adapter = null;
        attached = false;
    }

    private void populateTabsFromPagerAdapter() {
        tabLayout.removeAllTabs();

        if (adapter != null) {
            int adapterCount = adapter.getItemCount();
            for (int i = 0; i < adapterCount; ++i) {
                VerticalTabLayout.Tab tab = tabLayout.newTab();
                tabConfigurationStrategy.onConfigureTab(tab, i);
                tabLayout.addTab(tab, false);
            }
            // Make sure we reflect the currently set ViewPager item
            if (adapterCount > 0) {
                int lastItem = tabLayout.getTabCount() - 1;
                int currItem = Math.min(viewPager.getCurrentItem(), lastItem);
                if (currItem != tabLayout.getSelectedTabPosition()) {
                    tabLayout.selectTab(tabLayout.getTabAt(currItem));
                }
            }
        }
    }

    /**
     * A {@link ViewPager2.OnPageChangeCallback} class which contains the necessary calls back to the
     * provided {@link VerticalTabLayout} so that the tab position is kept in sync.
     *
     * <p>This class stores the provided TabLayout weakly, meaning that you can use {@link
     * ViewPager2#registerOnPageChangeCallback(ViewPager2.OnPageChangeCallback)} without removing the
     * callback and not cause a leak.
     */
    private static class VerticalTabLayoutOnPageChangeCallback extends ViewPager2.OnPageChangeCallback {

        @NonNull
        private final WeakReference<VerticalTabLayout> tabLayoutRef;
        private int previousScrollState;
        private int scrollState;

        VerticalTabLayoutOnPageChangeCallback(VerticalTabLayout tabLayout) {
            tabLayoutRef = new WeakReference<>(tabLayout);
            reset();
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            previousScrollState = scrollState;
            scrollState = state;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            VerticalTabLayout tabLayout = tabLayoutRef.get();
            if (tabLayout != null) {
                // Only update the text selection if we're not settling, or we are settling after
                // being dragged
                boolean updateText =
                        scrollState != SCROLL_STATE_SETTLING || previousScrollState == SCROLL_STATE_DRAGGING;
                // Update the indicator if we're not settling after being idle. This is caused
                // from a setCurrentItem() call and will be handled by an animation from
                // onPageSelected() instead.
                boolean updateIndicator =
                        !(scrollState == SCROLL_STATE_SETTLING && previousScrollState == SCROLL_STATE_IDLE);
                tabLayout.setScrollPosition(position, positionOffset, updateText, updateIndicator);
            }
        }

        @Override
        public void onPageSelected(int position) {
            VerticalTabLayout tabLayout = tabLayoutRef.get();
            if (tabLayout != null
                    && tabLayout.getSelectedTabPosition() != position
                    && position < tabLayout.getTabCount()) {
                // Select the tab, only updating the indicator if we're not being dragged/settled
                // (since onPageScrolled will handle that).
                boolean updateIndicator =
                        scrollState == SCROLL_STATE_IDLE
                                || (scrollState == SCROLL_STATE_SETTLING && previousScrollState == SCROLL_STATE_IDLE);
                tabLayout.selectTab(tabLayout.getTabAt(position), updateIndicator);
            }
        }

        void reset() {
            previousScrollState = scrollState = SCROLL_STATE_IDLE;
        }
    }

    private static class ViewPagerOnTabSelectedListener implements VerticalTabLayout.OnTabSelectedListener {
        private final ViewPager2 viewPager;

        ViewPagerOnTabSelectedListener(ViewPager2 viewPager) {
            this.viewPager = viewPager;
        }

        @Override
        public void onTabSelected(VerticalTabLayout.Tab tab) {
            viewPager.setCurrentItem(tab.getPosition(), true);
        }

        @Override
        public void onTabUnselected(VerticalTabLayout.Tab tab) {
            // No-op
        }

        @Override
        public void onTabReselected(VerticalTabLayout.Tab tab) {
            // No-op
        }
    }

    private class PagerAdapterObserver extends RecyclerView.AdapterDataObserver {
        PagerAdapterObserver() {
        }

        @Override
        public void onChanged() {
            populateTabsFromPagerAdapter();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            populateTabsFromPagerAdapter();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
            populateTabsFromPagerAdapter();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            populateTabsFromPagerAdapter();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            populateTabsFromPagerAdapter();
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            populateTabsFromPagerAdapter();
        }
    }
}
