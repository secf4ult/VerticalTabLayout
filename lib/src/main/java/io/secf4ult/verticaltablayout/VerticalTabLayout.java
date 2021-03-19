package io.secf4ult.verticaltablayout;

/*
  Vertical TabLayout
 */

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.BoolRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.util.Pools;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.core.view.PointerIconCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.animation.AnimationUtils;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.internal.ViewUtils;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.ripple.RippleUtils;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.MaterialShapeUtils;
import com.google.android.material.tabs.TabItem;
import com.google.android.material.tabs.TabLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.viewpager.widget.ViewPager.SCROLL_STATE_DRAGGING;
import static androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE;
import static androidx.viewpager.widget.ViewPager.SCROLL_STATE_SETTLING;

@SuppressLint("RestrictedApi")
public class VerticalTabLayout extends ScrollView {

    private static final String TAG = "VerticalTabLayout";

    @Dimension(unit = Dimension.DP)
    private static final int DEFAULT_WIDTH_WITH_TEXT_ICON = 72;
    @Dimension(unit = Dimension.DP)
    static final int DEFAULT_GAP_TEXT_ICON = 8;
    @Dimension(unit = Dimension.DP)
    private static final int DEFAULT_WIDTH = 48;
    @Dimension(unit = Dimension.DP)
    private static final int TAB_MIN_HEIGHT_MARGIN = 56;
    @Dimension(unit = Dimension.DP)
    private static final int MIN_INDICATOR_HEIGHT = 24;
    @Dimension(unit = Dimension.DP)
    static final int FIXED_WRAP_GUTTER_MIN = 16;

    private static final int INVALID_HEIGHT = -1;

    private static final int ANIMATION_DURATION = 300;

    private static final Pools.Pool<Tab> tabPool = new Pools.SynchronizedPool<>(16);

    /**
     * Scrollable tabs display a subset of tabs at any given moment, and can contain longer tab labels
     * and a larger number of tabs. They are best used for browsing contexts in touch interfaces when
     * users don’t need to directly compare the tab labels.
     *
     * @see #setTabMode(int)
     * @see #getTabMode()
     */
    public static final int MODE_SCROLLABLE = 0;

    /**
     * Fixed tabs display all tabs concurrently and are best used with content that benefits from
     * quick pivots between tabs. The maximum number of tabs is limited by the view’s height. Fixed
     * tabs have equal height, based on the highest tab label.
     *
     * @see #setTabMode(int)
     * @see #getTabMode()
     */
    public static final int MODE_FIXED = 1;

    /**
     * Auto-sizing tabs behave like MODE_FIXED with GRAVITY_CENTER while the tabs fit within the
     * VerticalTabLayout's content height. Fixed tabs have equal height, based on the highest tab label. Once the
     * tabs outgrow the view's height, auto-sizing tabs behave like MODE_SCROLLABLE, allowing for a
     * dynamic number of tabs without requiring additional layout logic.
     *
     * @see #setTabMode(int)
     * @see #getTabMode()
     */
    public static final int MODE_AUTO = 2;

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef({MODE_SCROLLABLE, MODE_AUTO, MODE_FIXED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    /**
     * If a tab is instantiated with {@link Tab#setText(CharSequence)}, and this mode is set, the text
     * will be saved and utilized for the content description, but no visible labels will be created.
     *
     * @see Tab#setTabLabelVisibility(int)
     */
    public static final int TAB_LABEL_VISIBILITY_UNLABELED = 0;

    /**
     * This mode is set by default. If a tab is instantiated with {@link Tab#setText(CharSequence)}, a
     * visible label will be created.
     *
     * @see Tab#setTabLabelVisibility(int)
     */
    public static final int TAB_LABEL_VISIBILITY_LABELED = 1;

    /**
     * hide
     */
    @IntDef({TAB_LABEL_VISIBILITY_LABELED, TAB_LABEL_VISIBILITY_UNLABELED})
    public @interface LabelVisibility {
    }

    /**
     * Gravity used to fill the {@link VerticalTabLayout} as much as possible. This option only takes effect
     * when used with {@link #MODE_FIXED} on non-landscape screens less than 600dp wide.
     *
     * @see #setTabGravity(int)
     * @see #getTabGravity()
     */
    public static final int GRAVITY_FILL = 0;

    /**
     * Gravity used to lay out the tabs in the center of the {@link VerticalTabLayout}.
     *
     * @see #setTabGravity(int)
     * @see #getTabGravity()
     */
    public static final int GRAVITY_CENTER = 1;

    /**
     * hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef(
            flag = true,
            value = {GRAVITY_FILL, GRAVITY_CENTER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TabGravity {
    }

    /**
     * Indicator gravity used to align the tab selection indicator to the left of the {@link
     * VerticalTabLayout}. This will only take effect if the indicator width is set via the custom
     * indicator drawable's intrinsic width (preferred). Otherwise, the indicator will not be shown.
     * This is the default value.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabIndicatorGravity
     * @see #setSelectedTabIndicatorGravity(int)
     * @see #getTabIndicatorGravity()
     */
    public static final int INDICATOR_GRAVITY_LEFT = 0;

    /**
     * Indicator gravity used to align the tab selection indicator to the center of the {@link
     * VerticalTabLayout}. This will only take effect if the indicator width is set via the custom indicator
     * drawable's intrinsic width (preferred), the indicator will not be shown.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabIndicatorGravity
     * @see #setSelectedTabIndicatorGravity(int)
     * @see #getTabIndicatorGravity()
     */
    public static final int INDICATOR_GRAVITY_CENTER = 1;

    /**
     * Indicator gravity used to align the tab selection indicator to the right of the {@link
     * VerticalTabLayout}. This will only take effect if the indicator width is set via the custom indicator
     * drawable's intrinsic width (preferred). Otherwise, the indicator will not be shown.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabIndicatorGravity
     * @see #setSelectedTabIndicatorGravity(int)
     * @see #getTabIndicatorGravity()
     */
    public static final int INDICATOR_GRAVITY_RIGHT = 2;

    /**
     * Indicator gravity used to stretch the tab selection indicator across the entire height and
     * width of the {@link VerticalTabLayout}. This will disregard {@code tabIndicatorHeight} and the
     * indicator drawable's intrinsic width, if set.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabIndicatorGravity
     * @see #setSelectedTabIndicatorGravity(int)
     * @see #getTabIndicatorGravity()
     */
    public static final int INDICATOR_GRAVITY_STRETCH = 3;

    @RestrictTo(LIBRARY_GROUP)
    @IntDef(
            value = {
                    INDICATOR_GRAVITY_LEFT,
                    INDICATOR_GRAVITY_CENTER,
                    INDICATOR_GRAVITY_RIGHT,
                    INDICATOR_GRAVITY_STRETCH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TabIndicatorGravity {
    }

    /**
     * Callback interface invoked when a tab's selection state changes.
     */
    public interface OnTabSelectedListener<T extends Tab> {
        /**
         * Called when a tab enters the selected state.
         *
         * @param tab The tab that was selected
         */
        void onTabSelected(T tab);

        /**
         * Called when a tab exits the selected state.
         *
         * @param tab The tab that was unselected
         */
        void onTabUnselected(T tab);

        /**
         * Called when a tab that is already selected is chosen again by the user. Some applications may
         * use this action to return to the top level of a category.
         *
         * @param tab The tab that was reselected.
         */
        void onTabReselected(T tab);
    }

    private final ArrayList<Tab> tabs = new ArrayList<>();
    @Nullable
    private Tab selectedTab;

    private final RectF tabViewContentBounds = new RectF();

    @NonNull
    private final SlidingTabIndicator slidingTabIndicator;

    int tabPaddingStart;
    int tabPaddingTop;
    int tabPaddingEnd;
    int tabPaddingBottom;

    int tabTextAppearance;
    ColorStateList tabTextColors;
    ColorStateList tabIconTint;
    ColorStateList tabRippleColorStateList;
    @Nullable
    Drawable tabSelectedIndicator;

    android.graphics.PorterDuff.Mode tabIconTintMode;
    float tabTextSize;
    float tabTextMultiLineSize;

    final int tabBackgroundResId;

    int tabMaxHeight = Integer.MAX_VALUE;
    private final int requestedTabMinHeight;
    private final int requestedTabMaxHeight;
    private final int scrollableTabMinHeight;

    private final int contentInsetTop;

    int tabGravity;
    int tabIndicatorAnimationDuration;
    @TabIndicatorGravity
    int tabIndicatorGravity;
    @Mode
    int mode;
    boolean inlineLabel;
    boolean tabIndicatorFullWidth;
    boolean unboundedRipple;

    @Nullable
    private OnTabSelectedListener selectedListener;

    private final ArrayList<OnTabSelectedListener> selectedListeners = new ArrayList<>();
    @Nullable
    private OnTabSelectedListener currentVpSelectedListener;

    private ValueAnimator scrollAnimator;

    @Nullable
    ViewPager viewPager;
    @Nullable
    private PagerAdapter pagerAdapter;
    private DataSetObserver pagerAdapterObserver;
    private PageChangeListener pageChangeListener;
    private AdapterChangeListener adapterChangeListener;
    private boolean setupViewPagerImplicitly;

    // Pool we use as a simple RecyclerBin
    private final Pools.Pool<TabView> tabViewPool = new Pools.SimplePool<>(12);


    public VerticalTabLayout(@NonNull Context context) {
        this(context, null);
    }

    public VerticalTabLayout(@NonNull Context context,
                             @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.tabStyle);
    }

    public VerticalTabLayout(@NonNull Context context,
                             @Nullable AttributeSet attrs,
                             int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // CHANGE
        // Disable the scroll bar
        setVerticalScrollBarEnabled(false);

        // Add the TabStrip
        slidingTabIndicator = new SlidingTabIndicator(context);
        super.addView(
                slidingTabIndicator,
                0,
                new ScrollView.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        TypedArray a =
                ThemeEnforcement.obtainStyledAttributes(
                        context,
                        attrs,
                        R.styleable.TabLayout,
                        defStyleAttr,
                        R.style.Widget_Design_TabLayout,
                        R.styleable.TabLayout_tabTextAppearance);

        if (getBackground() instanceof ColorDrawable) {
            ColorDrawable background = (ColorDrawable) getBackground();
            MaterialShapeDrawable materialShapeDrawable = new MaterialShapeDrawable();
            materialShapeDrawable.setFillColor(ColorStateList.valueOf(background.getColor()));
            materialShapeDrawable.initializeElevationOverlay(context);
            materialShapeDrawable.setElevation(ViewCompat.getElevation(this));
            ViewCompat.setBackground(this, materialShapeDrawable);
        }

        // CHANGE
        slidingTabIndicator.setSelectedIndicatorWidth(
                a.getDimensionPixelSize(R.styleable.TabLayout_tabIndicatorHeight, -1));
        slidingTabIndicator.setSelectedIndicatorColor(
                a.getColor(R.styleable.TabLayout_tabIndicatorColor, 0)
        );
        setSelectedTabIndicator(
                MaterialResources.getDrawable(context, a, R.styleable.TabLayout_tabIndicator)
        );
        setSelectedTabIndicatorGravity(
                a.getInt(R.styleable.TabLayout_tabIndicatorGravity, INDICATOR_GRAVITY_LEFT)
        );
        setTabIndicatorFullWidth(a.getBoolean(R.styleable.TabLayout_tabIndicatorFullWidth, true));

        tabPaddingStart =
                tabPaddingTop =
                        tabPaddingEnd =
                                tabPaddingBottom = a.getDimensionPixelSize(R.styleable.TabLayout_tabPadding, 0);
        tabPaddingStart =
                a.getDimensionPixelSize(R.styleable.TabLayout_tabPaddingStart, tabPaddingStart);
        tabPaddingEnd =
                a.getDimensionPixelSize(R.styleable.TabLayout_tabPaddingEnd, tabPaddingEnd);
        tabPaddingTop =
                a.getDimensionPixelSize(R.styleable.TabLayout_tabPaddingTop, tabPaddingTop);
        tabPaddingBottom =
                a.getDimensionPixelSize(R.styleable.TabLayout_tabPaddingBottom, tabPaddingBottom);

        tabTextAppearance =
                a.getResourceId(R.styleable.TabLayout_tabTextAppearance, R.style.TextAppearance_Design_Tab);

        // Text colors/sizes come from the text appearance first
        final TypedArray ta =
                context.obtainStyledAttributes(
                        tabTextAppearance, R.styleable.TextAppearance
                );
        try {
            tabTextSize =
                    ta.getDimensionPixelSize(
                            R.styleable.TextAppearance_android_textSize, 0
                    );
            tabTextColors =
                    MaterialResources.getColorStateList(
                            context,
                            ta,
                            R.styleable.TextAppearance_android_textColor
                    );
        } finally {
            ta.recycle();
        }

        if (a.hasValue(R.styleable.TabLayout_tabTextColor)) {
            // We have an explicit selected text color set, so we need to make merge it with the
            // current colors. This is exposed so that developers can use theme attributes to set
            // this (theme attrs in ColorStateLists are Lollipop+)
            tabTextColors = MaterialResources.getColorStateList(context, a, R.styleable.TabLayout_tabTextColor);
        }

        if (a.hasValue(R.styleable.TabLayout_tabSelectedTextColor)) {
            final int selected = a.getColor(R.styleable.TabLayout_tabSelectedTextColor, 0);
            tabTextColors = createColorStateList(tabTextColors.getDefaultColor(), selected);
        }

        tabIconTint =
                MaterialResources.getColorStateList(context, a, R.styleable.TabLayout_tabIconTint);
        tabIconTintMode =
                ViewUtils.parseTintMode(a.getInt(R.styleable.TabLayout_tabIconTintMode, -1), null);

        tabRippleColorStateList =
                MaterialResources.getColorStateList(context, a, R.styleable.TabLayout_tabRippleColor);

        tabIndicatorAnimationDuration =
                a.getInt(R.styleable.TabLayout_tabIndicatorAnimationDuration, ANIMATION_DURATION);

        // CHANGE
        requestedTabMinHeight =
                a.getDimensionPixelSize(R.styleable.TabLayout_tabMinWidth, INVALID_HEIGHT);
        requestedTabMaxHeight =
                a.getDimensionPixelSize(R.styleable.TabLayout_tabMaxWidth, INVALID_HEIGHT);
        tabBackgroundResId = a.getResourceId(R.styleable.TabLayout_tabBackground, 0);
        // CHANGE
        contentInsetTop = a.getDimensionPixelSize(R.styleable.TabLayout_tabContentStart, 0);
        // noinspection WrongConstant
        mode = a.getInt(R.styleable.TabLayout_tabMode, MODE_FIXED);
        tabGravity = a.getInt(R.styleable.TabLayout_tabGravity, GRAVITY_FILL);
        inlineLabel = a.getBoolean(R.styleable.TabLayout_tabInlineLabel, false);
        unboundedRipple = a.getBoolean(R.styleable.TabLayout_tabUnboundedRipple, false);
        a.recycle();

        // TODO add attr for these
        final Resources res = getResources();
        tabTextMultiLineSize = res.getDimensionPixelSize(R.dimen.design_tab_text_size_2line);
        scrollableTabMinHeight = res.getDimensionPixelSize(R.dimen.design_tab_scrollable_min_width);

        // Now apply the tab mode and gravity
        applyModeAndGravity();
    }

    /**
     * Sets the tab indicator's color for the currently selected tab.
     *
     * @param color color to use for the indicator
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabIndicatorColor
     */
    public void setSelectedTabIndicatorColor(@ColorInt int color) {
        slidingTabIndicator.setSelectedIndicatorColor(color);
    }

    /**
     * Set the scroll position of the tabs. This is useful for when the tabs are being displayed as
     * part of a scrolling container such as {@link androidx.viewpager.widget.ViewPager}.
     *
     * <p>Calling this method does not update the selected tab, it is only used for drawing purposes.
     *
     * @param position           current scroll position
     * @param positionOffset     Value from [0, 1) indicating the offset from {@code position}.
     * @param updateSelectedText Whether to update the text's selected state.
     * @see #setScrollPosition(int, float, boolean, boolean)
     */
    public void setScrollPosition(int position,
                                  float positionOffset,
                                  boolean updateSelectedText) {
        setScrollPosition(position, positionOffset, updateSelectedText, true);
    }

    /**
     * Set the scroll position of the tabs. This is useful for when the tabs are being displayed as
     * part of a scrolling container such as {@link androidx.viewpager.widget.ViewPager}.
     *
     * <p>Calling this method does not update the selected tab, it is only used for drawing purposes.
     *
     * @param position                current scroll position
     * @param positionOffset          Value from [0, 1) indicating the offset from {@code position}.
     * @param updateSelectedText      Whether to update the text's selected state.
     * @param updateIndicatorPosition Whether to set the indicator to the given position and offset.
     * @see #setScrollPosition(int, float, boolean)
     */
    public void setScrollPosition(int position,
                                  float positionOffset,
                                  boolean updateSelectedText,
                                  boolean updateIndicatorPosition) {
        final int roundedPosition = Math.round(position + positionOffset);
        if (roundedPosition < 0 || roundedPosition >= slidingTabIndicator.getChildCount()) {
            return;
        }

        // Set the indicator position, if enabled
        if (updateIndicatorPosition) {
            slidingTabIndicator.setIndicatorPositionFromTabPosition(position, positionOffset);
        }

        // Now update the scroll position, canceling any running animation
        if (scrollAnimator != null && scrollAnimator.isRunning()) {
            scrollAnimator.cancel();
        }
        scrollTo(calculateScrollYForTab(position, positionOffset), 0);

        // Update the 'selected state' view as we scroll, if enabled
        if (updateSelectedText) {
            setSelectedTabView(roundedPosition);
        }
    }

    /**
     * Add a tab to this layout. The tab will be added at the end of the list. If this is the first
     * tab to be added it will become the selected tab.
     *
     * @param tab Tab to add
     */
    public void addTab(@NonNull Tab tab) {
        addTab(tab, tabs.isEmpty());
    }

    /**
     * Add a tab to this layout. The tab will be inserted at <code>position</code>. If this is the
     * first tab to be added it will become the selected tab.
     *
     * @param tab      The tab to add
     * @param position The new position of the tab
     */
    public void addTab(@NonNull Tab tab,
                       int position) {
        addTab(tab, position, tabs.isEmpty());
    }

    /**
     * Add a tab to this layout. The tab will be added at the end of the list.
     *
     * @param tab         Tab to add
     * @param setSelected True if the added tab should become the selected tab.
     */
    public void addTab(@NonNull Tab tab,
                       boolean setSelected) {
        addTab(tab, tabs.size(), setSelected);
    }

    /**
     * Add a tab to this layout. The tab will be inserted at <code>position</code>.
     *
     * @param tab         The tab to add
     * @param position    The new position of the tab
     * @param setSelected True if the added tab should become the selected tab.
     */
    public void addTab(@NonNull Tab tab,
                       int position,
                       boolean setSelected) {
        if (tab.parent != this) {
            throw new IllegalArgumentException("Tab belongs to a different TabLayout.");
        }
        configureTab(tab, position);
        addTabView(tab);

        if (setSelected) {
            tab.select();
        }
    }

    private void addTabFromItemView(@NonNull TabItem item) {
        final Tab tab = newTab();
        if (item.text != null) {
            tab.setText(item.text);
        }
        if (item.icon != null) {
            tab.setIcon(item.icon);
        }
        if (item.customLayout != 0) {
            tab.setCustomView(item.customLayout);
        }
        if (!TextUtils.isEmpty(item.getContentDescription())) {
            tab.setContentDescription(item.getContentDescription());
        }
        addTab(tab);
    }

    /**
     * Add a {@link OnTabSelectedListener} that will be invoked when tab selection changes.
     *
     * <p>Components that add a listener should take care to remove it when finished via {@link
     * #removeOnTabSelectedListener(OnTabSelectedListener)}.
     *
     * @param listener listener to add
     */
    public void addOnTabSelectedListener(@Nullable OnTabSelectedListener listener) {
        if (!selectedListeners.contains(listener)) {
            selectedListeners.add(listener);
        }
    }

    /**
     * Remove the given {@link OnTabSelectedListener} that was previously added via {@link
     * #addOnTabSelectedListener(OnTabSelectedListener)}.
     *
     * @param listener listener to remove
     */
    public void removeOnTabSelectedListener(@Nullable OnTabSelectedListener listener) {
        selectedListeners.remove(listener);
    }

    /**
     * Remove all previously added {@link TabLayout.OnTabSelectedListener}s.
     */
    public void clearOnTabSelectedListeners() {
        selectedListeners.clear();
    }

    /**
     * Create and return a new {@link Tab}. You need to manually add this using {@link #addTab(Tab)}
     * or a related method.
     *
     * @return A new Tab
     * @see #addTab(Tab)
     */
    @NonNull
    public Tab newTab() {
        Tab tab = createTabFromPool();
        tab.parent = this;
        tab.view = createTabView(tab);
        return tab;
    }

    protected Tab createTabFromPool() {
        Tab tab = tabPool.acquire();
        if (tab == null) {
            tab = new Tab();
        }
        return tab;
    }

    protected boolean releaseFromTabPool(Tab tab) {
        return tabPool.release(tab);
    }

    /**
     * Returns the number of tabs currently registered with the action bar.
     *
     * @return Tab count
     */
    public int getTabCount() {
        return tabs.size();
    }

    /**
     * Returns the tab at the specified index.
     */
    @Nullable
    public Tab getTabAt(int index) {
        return (index < 0 || index >= getTabCount()) ? null : tabs.get(index);
    }

    /**
     * Returns the position of the current selected tab.
     *
     * @return selected tab position, or {@code -1} if there isn't a selected tab.
     */
    public int getSelectedTabPosition() {
        return selectedTab != null ? selectedTab.getPosition() : -1;
    }

    /**
     * Remove a tab from the layout. If the removed tab was selected it will be deselected and another
     * tab will be selected if present.
     *
     * @param tab The tab to remove
     */
    public void removeTab(@NonNull Tab tab) {
        if (tab.parent != this) {
            throw new IllegalArgumentException("Tab does not belong to this TabLayout.");
        }
        removeTabAt(tab.getPosition());
    }

    /**
     * Remove a tab from the layout. If the removed tab was selected it will be deselected and another
     * tab will be selected if present.
     *
     * @param position Position of the tab to remove
     */
    public void removeTabAt(int position) {
        final int selectedTabPosition = selectedTab != null ? selectedTab.getPosition() : 0;
        removeTabViewAt(position);

        final Tab removedTab = tabs.remove(position);
        if (removedTab != null) {
            removedTab.reset();
            releaseFromTabPool(removedTab);
        }

        final int newTabCount = tabs.size();
        // reset all remaining tab's position
        for (int i = position; i < newTabCount; ++i) {
            tabs.get(i).setPosition(i);
        }
        // reselect tab
        if (selectedTabPosition == position) {
            selectTab(tabs.isEmpty() ? null : tabs.get(Math.max(0, position - 1)));
        }
    }

    /**
     * Remove all tabs from the action bar and deselect the current tab.
     */
    public void removeAllTabs() {
        for (int i = slidingTabIndicator.getChildCount() - 1; i >= 0; --i) {
            removeTabViewAt(i);
        }

        for (final Iterator<Tab> i = tabs.iterator(); i.hasNext(); ) {
            final Tab tab = i.next();
            i.remove();
            tab.reset();
            releaseFromTabPool(tab);
        }

        selectedTab = null;
    }

    /**
     * Set the behavior mode for the Tabs in this layout. The valid input options are:
     *
     * <ul>
     *   <li>{@link #MODE_FIXED}: Fixed tabs display all tabs concurrently and are best used with
     *       content that benefits from quick pivots between tabs.
     *   <li>{@link #MODE_SCROLLABLE}: Scrollable tabs display a subset of tabs at any given moment,
     *       and can contain longer tab labels and a larger number of tabs. They are best used for
     *       browsing contexts in touch interfaces when users don’t need to directly compare the tab
     *       labels. This mode is commonly used with a {@link androidx.viewpager.widget.ViewPager}.
     * </ul>
     *
     * @param mode one of {@link #MODE_FIXED} or {@link #MODE_SCROLLABLE}.
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabMode
     */
    public void setTabMode(@Mode int mode) {
        if (mode != this.mode) {
            this.mode = mode;
            applyModeAndGravity();
        }
    }

    /**
     * Returns the current mode used by this {@link VerticalTabLayout}.
     *
     * @see #setTabMode(int)
     */
    @Mode
    public int getTabMode() {
        return mode;
    }

    /**
     * Set the gravity to use when laying out the tabs.
     *
     * @param gravity one of {@link #GRAVITY_CENTER} or {@link #GRAVITY_FILL}.
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabGravity
     */
    public void setTabGravity(@TabGravity int gravity) {
        if (tabGravity != gravity) {
            tabGravity = gravity;
            applyModeAndGravity();
        }
    }

    /**
     * The current gravity used for laying out tabs.
     *
     * @return one of {@link #GRAVITY_CENTER} or {@link #GRAVITY_FILL}.
     */
    @TabGravity
    public int getTabGravity() {
        return tabGravity;
    }

    /**
     * Set the indicator gravity used to align the tab selection indicator in the {@link VerticalTabLayout}.
     * You must set the indicator height via the custom indicator drawable's intrinsic height
     * (preferred). Otherwise, the indicator will not be shown
     * unless gravity is set to {@link #INDICATOR_GRAVITY_STRETCH}, in which case it will ignore
     * indicator height and stretch across the entire height and width of the {@link VerticalTabLayout}. This
     * defaults to {@link #INDICATOR_GRAVITY_LEFT} if not set.
     *
     * @param indicatorGravity one of {@link #INDICATOR_GRAVITY_LEFT}, {@link
     *                         #INDICATOR_GRAVITY_CENTER}, {@link #INDICATOR_GRAVITY_RIGHT}, or {@link
     *                         #INDICATOR_GRAVITY_STRETCH}
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabIndicatorGravity
     */
    public void setSelectedTabIndicatorGravity(@TabIndicatorGravity int indicatorGravity) {
        if (tabIndicatorGravity != indicatorGravity) {
            tabIndicatorGravity = indicatorGravity;
            ViewCompat.postInvalidateOnAnimation(slidingTabIndicator);
        }
    }

    /**
     * Get the current indicator gravity used to align the tab selection indicator in the {@link
     * VerticalTabLayout}.
     *
     * @return one of {@link #INDICATOR_GRAVITY_LEFT}, {@link #INDICATOR_GRAVITY_CENTER}, {@link
     * #INDICATOR_GRAVITY_RIGHT}, or {@link #INDICATOR_GRAVITY_STRETCH}
     */
    @TabIndicatorGravity
    public int getTabIndicatorGravity() {
        return tabIndicatorGravity;
    }

    /**
     * Enable or disable option to fit the tab selection indicator to the full width of the tab item
     * rather than to the tab item's content.
     *
     * <p>Defaults to true. If set to false and the tab item has a text label, the selection indicator
     * width will be set to the width of the text label. If the tab item has no text label, but does
     * have an icon, the selection indicator width will be set to the icon. If the tab item has
     * neither of these, or if the calculated width is less than a minimum width value, the selection
     * indicator width will be set to the minimum width value.
     *
     * @param tabIndicatorFullWidth Whether or not to fit selection indicator width to full width of
     *                              the tab item
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabIndicatorFullWidth
     * @see #isTabIndicatorFullWidth()
     */
    public void setTabIndicatorFullWidth(boolean tabIndicatorFullWidth) {
        this.tabIndicatorFullWidth = tabIndicatorFullWidth;
        ViewCompat.postInvalidateOnAnimation(slidingTabIndicator);
    }

    /**
     * Get whether or not selection indicator width is fit to full width of the tab item, or fit to
     * the tab item's content.
     *
     * @return whether or not selection indicator width is fit to the full width of the tab item
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabIndicatorFullWidth
     * @see #setTabIndicatorFullWidth(boolean)
     */
    public boolean isTabIndicatorFullWidth() {
        return tabIndicatorFullWidth;
    }

    /**
     * Set whether tab labels will be displayed inline with tab icons, or if they will be displayed
     * underneath tab icons.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabInlineLabel
     * @see #isInlineLabel()
     */
    public void setInlineLabel(boolean inline) {
        if (inlineLabel != inline) {
            inlineLabel = inline;
            for (int i = 0; i < slidingTabIndicator.getChildCount(); ++i) {
                View child = slidingTabIndicator.getChildAt(i);
                if (child instanceof TabView) {
                    ((TabView) child).updateOrientation();
                }
            }
            applyModeAndGravity();
        }
    }

    /**
     * Set whether tab labels will be displayed inline with tab icons, or if they will be displayed
     * underneath tab icons.
     *
     * @param inlineResourceId Resource ID for boolean inline flag
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabInlineLabel
     * @see #isInlineLabel()
     */
    public void setInlineLabelResource(@BoolRes int inlineResourceId) {
        setInlineLabel(getResources().getBoolean(inlineResourceId));
    }

    /**
     * Returns whether tab labels will be displayed inline with tab icons, or if they will be
     * displayed underneath tab icons.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabInlineLabel
     * @see #setInlineLabel(boolean)
     */
    public boolean isInlineLabel() {
        return inlineLabel;
    }

    /**
     * Set whether this {@link VerticalTabLayout} will have an unbounded ripple effect or if ripple will be
     * bound to the tab item size.
     *
     * <p>Defaults to false.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabUnboundedRipple
     * @see #hasUnboundedRipple()
     */
    public void setUnboundedRipple(boolean unboundedRipple) {
        if (this.unboundedRipple != unboundedRipple) {
            this.unboundedRipple = unboundedRipple;
            for (int i = 0, j = slidingTabIndicator.getChildCount(); i < j; ++i) {
                View child = slidingTabIndicator.getChildAt(i);
                if (child instanceof TabView) {
                    ((TabView) child).updateBackgroundDrawable(getContext());
                }
            }
        }
    }

    /**
     * Set whether this {@link VerticalTabLayout} will have an unbounded ripple effect or if ripple will be
     * bound to the tab item size. Defaults to false.
     *
     * @param unboundedRippleResourceId Resource ID for boolean unbounded ripple value
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabUnboundedRipple
     * @see #hasUnboundedRipple()
     */
    public void setUnboundedRippleResource(@BoolRes int unboundedRippleResourceId) {
        setUnboundedRipple(getResources().getBoolean(unboundedRippleResourceId));
    }

    /**
     * Returns whether this {@link VerticalTabLayout} has an unbounded ripple effect, or if ripple is bound to
     * the tab item size.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabUnboundedRipple
     * @see #setUnboundedRipple(boolean)
     */
    public boolean hasUnboundedRipple() {
        return unboundedRipple;
    }

    /**
     * Sets the text colors for the different states (normal, selected) used for the tabs.
     *
     * @see #getTabTextColors()
     */
    public void setTabTextColors(@Nullable ColorStateList textColor) {
        if (tabTextColors != textColor) {
            tabTextColors = textColor;
            updateAllTabs();
        }
    }

    /**
     * Sets the text colors for the different states (normal, selected) used for the tabs.
     *
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabTextColor
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabSelectedTextColor
     */
    public void setTabTextColors(int normalColor, int selectedColor) {
        setTabTextColors(createColorStateList(normalColor, selectedColor));
    }

    /**
     * Gets the text colors for the different states (normal, selected) used for the tabs.
     */
    @Nullable
    public ColorStateList getTabTextColors() {
        return tabTextColors;
    }

    /**
     * Sets the icon tint for the different states (normal, selected) used for the tabs.
     *
     * @see #getTabIconTint()
     */
    public void setTabIconTint(@Nullable ColorStateList iconTint) {
        if (tabIconTint != iconTint) {
            tabIconTint = iconTint;
            updateAllTabs();
        }
    }

    /**
     * Sets the icon tint resource for the different states (normal, selected) used for the tabs.
     *
     * @param iconTintResourceId A color resource to use as icon tint.
     * @see #getTabIconTint()
     */
    public void setTabIconTintResource(@ColorRes int iconTintResourceId) {
        setTabIconTint(AppCompatResources.getColorStateList(getContext(), iconTintResourceId));
    }

    /**
     * Gets the icon tint for the different states (normal, selected) used for the tabs.
     */
    @Nullable
    public ColorStateList getTabIconTint() {
        return tabIconTint;
    }

    /**
     * Sets the ripple color for this TabLayout.
     *
     * <p>When running on devices with KitKat or below, we draw this color as a filled overlay rather
     * than a ripple.
     *
     * @param color color (or ColorStateList) to use for the ripple
     * @attr ref com.google.android.material.R.styleable#TabLayout_tabRippleColor
     * @see #getTabRippleColor()
     */
    public void setTabRippleColor(@Nullable ColorStateList color) {
        if (tabRippleColorStateList != color) {
            tabRippleColorStateList = color;
            for (int i = 0; i < slidingTabIndicator.getChildCount(); i++) {
                View child = slidingTabIndicator.getChildAt(i);
                if (child instanceof TabView) {
                    ((TabView) child).updateBackgroundDrawable(getContext());
                }
            }
        }
    }

    /**
     * Sets the ripple color resource for this TabLayout.
     *
     * <p>When running on devices with KitKat or below, we draw this color as a filled overlay rather
     * than a ripple.
     *
     * @param tabRippleColorResourceId A color resource to use as ripple color.
     * @see #getTabRippleColor()
     */
    public void setTabRippleColorResource(@ColorRes int tabRippleColorResourceId) {
        setTabRippleColor(
                AppCompatResources.getColorStateList(
                        getContext(),
                        tabRippleColorResourceId));
    }

    /**
     * Returns the ripple color for this VerticalTabLayout.
     *
     * @return the color (or ColorStateList) used for the ripple
     * @see #setTabRippleColor(ColorStateList)
     */
    @Nullable
    public ColorStateList getTabRippleColor() {
        return tabRippleColorStateList;
    }

    /**
     * Sets the selection indicator for this VerticalTabLayout. By default, this is a line along the bottom of
     * the tab. If {@code tabIndicatorColor} is specified via the VerticalTabLayout's style or via {@link
     * #setSelectedTabIndicatorColor(int)} the selection indicator will be tinted that color.
     * Otherwise, it will use the colors specified in the drawable.
     *
     * @param tabSelectedIndicator A drawable to use as the selected tab indicator.
     * @see #setSelectedTabIndicatorColor(int)
     * @see #setSelectedTabIndicator(int)
     */
    public void setSelectedTabIndicator(@Nullable Drawable tabSelectedIndicator) {
        if (this.tabSelectedIndicator != tabSelectedIndicator) {
            this.tabSelectedIndicator = tabSelectedIndicator;
            ViewCompat.postInvalidateOnAnimation(slidingTabIndicator);
        }
    }

    /**
     * Sets the drawable resource to use as the selection indicator for this VerticalTabLayout. By default,
     * this is a line along the bottom of the tab. If {@code tabIndicatorColor} is specified via the
     * VerticalTabLayout's style or via {@link #setSelectedTabIndicatorColor(int)} the selection indicator
     * will be tinted that color. Otherwise, it will use the colors specified in the drawable.
     *
     * @param tabSelectedIndicatorResourceId A drawable resource to use as the selected tab indicator.
     * @see #setSelectedTabIndicatorColor(int)
     * @see #setSelectedTabIndicator(Drawable)
     */
    public void setSelectedTabIndicator(@DrawableRes int tabSelectedIndicatorResourceId) {
        if (tabSelectedIndicatorResourceId != 0) {
            setSelectedTabIndicator(
                    AppCompatResources.getDrawable(
                            getContext(),
                            tabSelectedIndicatorResourceId));
        } else {
            setSelectedTabIndicator(null);
        }
    }

    /**
     * Returns the selection indicator drawable for this TabLayout.
     *
     * @return The drawable used as the tab selection indicator, if set.
     * @see #setSelectedTabIndicator(Drawable)
     * @see #setSelectedTabIndicator(int)
     */
    @Nullable
    public Drawable getTabSelectedIndicator() {
        return tabSelectedIndicator;
    }

    /**
     * The one-stop shop for setting up this {@link VerticalTabLayout} with a {@link ViewPager}.
     *
     * <p>This is the same as calling {@link #setupWithViewPager(ViewPager, boolean)} with
     * auto-refresh enabled.
     *
     * @param viewPager the ViewPager to link to, or {@code null} to clear any previous link
     */
    public void setupWithViewPager(@Nullable ViewPager viewPager) {
        setupWithViewPager(viewPager, true);
    }

    /**
     * The one-stop shop for setting up this {@link VerticalTabLayout} with a {@link ViewPager}.
     *
     * <p>This method will link the given ViewPager and this VerticalTabLayout together so that changes in one
     * are automatically reflected in the other. This includes scroll state changes and clicks. The
     * tabs displayed in this layout will be populated from the ViewPager adapter's page titles.
     *
     * <p>If {@code autoRefresh} is {@code true}, any changes in the {@link PagerAdapter} will trigger
     * this layout to re-populate itself from the adapter's titles.
     *
     * <p>If the given ViewPager is non-null, it needs to already have a {@link PagerAdapter} set.
     *
     * @param viewPager   the ViewPager to link to, or {@code null} to clear any previous link
     * @param autoRefresh whether this layout should refresh its contents if the given ViewPager's
     *                    content changes
     */
    public void setupWithViewPager(@Nullable final ViewPager viewPager, boolean autoRefresh) {
        setupWithViewPager(viewPager, autoRefresh, false);
    }

    private void setupWithViewPager(
            @Nullable final ViewPager viewPager,
            boolean autoRefresh,
            boolean implicitSetup) {
        if (this.viewPager != null) {
            // If we've already been setup with a ViewPager, remove us from it
            if (pageChangeListener != null) {
                this.viewPager.removeOnPageChangeListener(pageChangeListener);
            }
            if (adapterChangeListener != null) {
                this.viewPager.removeOnAdapterChangeListener(adapterChangeListener);
            }
        }

        if (currentVpSelectedListener != null) {
            // If we already have a tab selected listener for the ViewPager, remove it
            removeOnTabSelectedListener(currentVpSelectedListener);
            currentVpSelectedListener = null;
        }

        if (viewPager != null) {
            this.viewPager = viewPager;

            // Add our custom OnPageChangeListener to the ViewPager
            if (pageChangeListener == null) {
                pageChangeListener = new PageChangeListener(this);
            }
            pageChangeListener.reset();
            viewPager.addOnPageChangeListener(pageChangeListener);

            // Now we'll add a tab selected listener to set ViewPager's current item
            currentVpSelectedListener = new ViewPagerOnTabSelectedListener(viewPager);
            addOnTabSelectedListener(currentVpSelectedListener);

            final PagerAdapter adapter = viewPager.getAdapter();
            if (adapter != null) {
                // Now we'll populate ourselves from the pager adapter, adding an observer if
                // autoRefresh is enabled
                setPagerAdapter(adapter, autoRefresh);
            }

            // Add a listener so that we're notified of any adapter changes
            if (adapterChangeListener == null) {
                adapterChangeListener = new AdapterChangeListener();
            }
            adapterChangeListener.setAutoRefresh(autoRefresh);
            viewPager.addOnAdapterChangeListener(adapterChangeListener);

            // Now update the scroll position to match the ViewPager's current item
            setScrollPosition(viewPager.getCurrentItem(), 0f, true);
        } else {
            // We've been given a null ViewPager so we need to clear out the internal state,
            // listeners and observers
            this.viewPager = null;
            setPagerAdapter(null, false);
        }

        setupViewPagerImplicitly = implicitSetup;
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        // Only delay the pressed state if the tabs can scroll
        return getTabScrollRange() > 0;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        MaterialShapeUtils.setParentAbsoluteElevation(this);

        if (viewPager == null) {
            // If we don't have a ViewPager already, check if our parent is a ViewPager to
            // setup with it automatically/implicitly
            final ViewParent vp = getParent();
            if (vp instanceof ViewPager) {
                // If we have a ViewPager parent and we've been added as part of its decor, let's
                // assume that we should automatically setup to display any titles
                setupWithViewPager((ViewPager) vp, true, true);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (setupViewPagerImplicitly) {
            // If we've been setup with a ViewPager implicitly, let's clear out any listeners, etc
            setupWithViewPager(null);
            setupViewPagerImplicitly = false;
        }
    }

    private int getTabScrollRange() {
        return Math.max(
                0,
                slidingTabIndicator.getHeight() - getHeight() - getPaddingTop() - getPaddingBottom()
        );
    }

    void setPagerAdapter(
            @Nullable final PagerAdapter adapter,
            final boolean addObserver) {
        if (pagerAdapter != null && pagerAdapterObserver != null) {
            // If we already have a PagerAdapter, unregister our observer
            pagerAdapter.unregisterDataSetObserver(pagerAdapterObserver);
        }

        pagerAdapter = adapter;

        if (addObserver && adapter != null) {
            // Register our observer on the new adapter
            if (pagerAdapterObserver == null) {
                pagerAdapterObserver = new PagerAdapterObserver();
            }
            adapter.registerDataSetObserver(pagerAdapterObserver);
        }

        // Finally make sure we reflect the new adapter
        populateFromPagerAdapter();
    }

    void populateFromPagerAdapter() {
        removeAllTabs();

        if (pagerAdapter != null) {
            final int adapterCount = pagerAdapter.getCount();
            for (int i = 0; i < adapterCount; ++i) {
                addTab(newTab().setText(pagerAdapter.getPageTitle(i)), false);
            }

            if (viewPager != null && adapterCount > 0) {
                final int curItem = viewPager.getCurrentItem();
                if (curItem != getSelectedTabPosition() && curItem < getTabCount()) {
                    selectTab(getTabAt(curItem));
                }
            }
        }
    }

    private void updateAllTabs() {
        for (int i = 0, z = tabs.size(); i < z; i++) {
            tabs.get(i).updateView();
        }
    }

    @NonNull
    private TabView createTabView(@NonNull final Tab tab) {
        TabView tabView = tabViewPool != null ? tabViewPool.acquire() : null;
        if (tabView == null) {
            tabView = new TabView(getContext());
        }
        tabView.setTab(tab);
        tabView.setFocusable(true);
        tabView.setMinimumHeight(getTabMinHeight());
        if (TextUtils.isEmpty(tab.contentDesc)) {
            tabView.setContentDescription(tab.text);
        } else {
            tabView.setContentDescription(tab.contentDesc);
        }
        return tabView;
    }

    private void configureTab(@NonNull Tab tab, int position) {
        tab.setPosition(position);
        tabs.add(position, tab);

        final int count = tabs.size();
        for (int i = position + 1; i < count; ++i) {
            tabs.get(i).setPosition(i);
        }
    }

    private void addTabView(@NonNull Tab tab) {
        final TabView tabView = tab.view;
        tabView.setSelected(false);
        tabView.setActivated(false);
        slidingTabIndicator.addView(
                tabView,
                tab.getPosition(),
                createLayoutParamsForTabs());
    }

    @Override
    public void addView(View child) {
        addViewInternal(child);
    }

    @Override
    public void addView(View child, int index) {
        addViewInternal(child);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        addViewInternal(child);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        addViewInternal(child);
    }

    private void addViewInternal(final View child) {
        if (child instanceof TabItem) {
            // Add TabItem XML into VerticalTabLayout
            addTabFromItemView((TabItem) child);
        } else {
            throw new IllegalArgumentException("Only TabItem instances can be added to TabLayout");
        }
    }

    @NonNull
    private LinearLayout.LayoutParams createLayoutParamsForTabs() {
        // CHANGE
        final LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        updateTabViewLayoutParams(lp);
        return lp;
    }

    private void updateTabViewLayoutParams(@NonNull LinearLayout.LayoutParams lp) {
        if (mode == MODE_FIXED && tabGravity == GRAVITY_FILL) {
            // CHANGE
            lp.height = 0;
            lp.weight = 1;
        } else {
            lp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            lp.weight = 0;
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setElevation(float elevation) {
        super.setElevation(elevation);

        MaterialShapeUtils.setElevation(this, elevation);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        // Draw tab background layer for each tab item
        for (int i = 0, z = slidingTabIndicator.getChildCount(); i < z; ++i) {
            View tabView = slidingTabIndicator.getChildAt(i);
            if (tabView instanceof TabView) {
                ((TabView) tabView).drawBackground(canvas);
            }
        }

        super.onDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // CHANGE
        // If we have a MeasureSpec which allows us to decide our width, try and use the default
        // width
        final int idealWidth = (int) ViewUtils.dpToPx(getContext(), getDefaultWidth());

        switch (MeasureSpec.getMode(widthMeasureSpec)) {
            case MeasureSpec.AT_MOST:
                if (getChildCount() == 1 && MeasureSpec.getSize(widthMeasureSpec) >= idealWidth) {
                    getChildAt(0).setMinimumWidth(idealWidth);
                }
                break;
            case MeasureSpec.UNSPECIFIED:
                // if width is unspecified, use idealWidth
                widthMeasureSpec =
                        MeasureSpec.makeMeasureSpec(
                                idealWidth + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY
                        );
                break;
            default:
                break;
        }

        final int specHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            // If we don't have an unspecified width spec, use the given size to calculate
            // the max tab width
            tabMaxHeight =
                    requestedTabMaxHeight > 0
                            ? requestedTabMaxHeight
                            : (int) (specHeight - ViewUtils.dpToPx(getContext(), TAB_MIN_HEIGHT_MARGIN));
        }

        // Now super measure itself using the (possibly) modified height spec
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (getChildCount() == 1) {
            // If we're in fixed mode then we need to make the tab strip is the same width as us
            // so we don't scroll
            final View child = getChildAt(0);
            boolean remeasure = false;

            switch (mode) {
                case MODE_AUTO:
                case MODE_SCROLLABLE:
                    // We only need to resize the child if it's smaller than us. This is similar
                    // to fillViewport
                    remeasure = child.getMeasuredHeight() < getMeasuredHeight();
                    break;
                case MODE_FIXED:
                    // Resize the child so that it doesn't scroll
                    remeasure = child.getMeasuredHeight() != getMeasuredHeight();
                    break;
            }

            if (remeasure) {
                // Re-measure the child with a widthSpec set to be exactly our measure width
                int childHeightMeasureSpec =
                        getChildMeasureSpec(
                                heightMeasureSpec,
                                getPaddingTop() + getPaddingBottom(),
                                child.getLayoutParams().height
                        );

                int childWidthMeasureSpec =
                        MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }

    private void removeTabViewAt(int position) {
        final TabView view = (TabView) slidingTabIndicator.getChildAt(position);
        slidingTabIndicator.removeViewAt(position);
        if (view != null) {
            view.reset();
            tabViewPool.release(view);
        }
        requestLayout();
    }

    private void animateToTab(int newPosition) {
        // CHANGE
        if (newPosition == Tab.INVALID_POSITION) {
            return;
        }

        if (getWindowToken() == null
                || !ViewCompat.isLaidOut(this)
                || slidingTabIndicator.childrenNeedLayout()) {
            // If we don't have a window token, or we haven't been laid out yet just draw the new
            // position now
            setScrollPosition(newPosition, 0f, true);
            return;
        }

        final int startScrollY = getScrollY();
        final int targetScrollY = calculateScrollYForTab(newPosition, 0);

        if (startScrollY != targetScrollY) {
            ensureScrollAnimator();

            scrollAnimator.setIntValues(startScrollY, targetScrollY);
            scrollAnimator.start();
        }

        // Now animate the indicator
        slidingTabIndicator.animateIndicatorToPosition(newPosition, tabIndicatorAnimationDuration);
    }

    private void ensureScrollAnimator() {
        if (scrollAnimator == null) {
            scrollAnimator = new ValueAnimator();
            scrollAnimator.setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
            scrollAnimator.setDuration(tabIndicatorAnimationDuration);
            scrollAnimator.addUpdateListener(
                    animator -> {
                        // CHANGE
                        // Scroll animates along Y-axis
                        scrollTo(0, (int) animator.getAnimatedValue());
                    }
            );
        }
    }

    void setScrollAnimatorListener(ValueAnimator.AnimatorListener listener) {
        ensureScrollAnimator();
        scrollAnimator.addListener(listener);
    }

    /**
     * Called when a selected tab is added. Unselects all other tabs in the TabLayout.
     *
     * @param position Position of the selected tab.
     */
    private void setSelectedTabView(int position) {
        final int tabCount = slidingTabIndicator.getChildCount();
        if (position >= 0 && position < tabCount) {
            for (int i = 0; i < tabCount; i++) {
                final View child = slidingTabIndicator.getChildAt(i);
                child.setSelected(i == position);
                child.setActivated(i == position);
            }
        }
    }

    /**
     * Selects the given tab.
     *
     * @param tab The tab to select, or {@code null} to select none.
     * @see #selectTab(Tab, boolean)
     */
    public void selectTab(@Nullable Tab tab) {
        selectTab(tab, true);
    }

    /**
     * Selects the given tab. Will always animate to the selected tab if the current tab is
     * reselected, regardless of the value of {@code updateIndicator}.
     *
     * @param tab             The tab to select, or {@code null} to select none.
     * @param updateIndicator Whether to animate to the selected tab.
     * @see #selectTab(Tab)
     */
    public void selectTab(@Nullable final Tab tab, boolean updateIndicator) {
        final Tab currentTab = selectedTab;

        if (currentTab == tab) {
            if (currentTab != null) {
                // always animate to the selected tab if the current tab is reselected
                dispatchTabReselected(tab);
                animateToTab(tab.getPosition());
            }
        } else {
            final int newPosition = tab != null ? tab.getPosition() : Tab.INVALID_POSITION;
            if (updateIndicator) {
                if ((currentTab == null
                        || currentTab.getPosition() == Tab.INVALID_POSITION)
                        && newPosition != Tab.INVALID_POSITION) {
                    // If we don't currently have a tab, just draw the indicator
                    setScrollPosition(newPosition, 0f, true);
                } else {
                    animateToTab(newPosition);
                }
                if (newPosition != Tab.INVALID_POSITION) {
                    setSelectedTabView(newPosition);
                }
            }
            // Setting selectedTab before dispatching 'tab unselected' events, so that currentTab's state
            // will be interpreted as unselected
            selectedTab = tab;
            if (currentTab != null) {
                dispatchTabUnselected(currentTab);
            }
            if (tab != null) {
                dispatchTabSelected(tab);
            }
        }
    }

    private void dispatchTabSelected(@NonNull final Tab tab) {
        for (int i = selectedListeners.size() - 1; i >= 0; --i) {
            selectedListeners.get(i).onTabSelected(tab);
        }
    }

    private void dispatchTabUnselected(@NonNull final Tab tab) {
        for (int i = selectedListeners.size() - 1; i >= 0; --i) {
            selectedListeners.get(i).onTabUnselected(tab);
        }
    }

    private void dispatchTabReselected(@NonNull final Tab tab) {
        for (int i = selectedListeners.size() - 1; i >= 0; --i) {
            selectedListeners.get(i).onTabReselected(tab);
        }
    }

    private int calculateScrollYForTab(int position, float positionOffset) {
        // Ignore MODE_FIXED
        if (mode == MODE_SCROLLABLE || mode == MODE_AUTO) {
            final View selectedChild = slidingTabIndicator.getChildAt(position);
            final View nextChild =
                    position + 1 < slidingTabIndicator.getChildCount()
                            ? slidingTabIndicator.getChildAt(position + 1)
                            : null;
            // CHANGE
            final int selectedHeight = selectedChild != null ? selectedChild.getHeight() : 0;
            final int nextHeight = nextChild != null ? nextChild.getHeight() : 0;

            // base scroll amount: places center of tab in center of parent
            int scrollBase = selectedChild.getTop() + (selectedHeight / 2) - (getHeight() / 2);
            // offset amount: fraction of the distance between centres of tabs
            int scrollOffset = (int) ((selectedHeight + nextHeight) * 0.5f * positionOffset);

            // For VerticalTabLayout, scroll direction is always from up to down
            return scrollBase + scrollOffset;
        }
        return 0;
    }

    private void applyModeAndGravity() {
        // CHANGE
        int paddingTop = 0;
        if (mode == MODE_SCROLLABLE || mode == MODE_AUTO) {
            // If we're scrollable, or fixed at start, inset using padding
            paddingTop = Math.max(0, contentInsetTop - tabPaddingStart);
        }
        ViewCompat.setPaddingRelative(slidingTabIndicator, 0, paddingTop, 0, 0);

        switch (mode) {
            case MODE_AUTO:
            case MODE_FIXED:
                slidingTabIndicator.setGravity(Gravity.CENTER_HORIZONTAL);
                break;
            case MODE_SCROLLABLE:
                slidingTabIndicator.setGravity(Gravity.START);
                break;
        }

        updateTabViews(true);
    }

    void updateTabViews(final boolean requestLayout) {
        for (int i = 0; i < slidingTabIndicator.getChildCount(); ++i) {
            View child = slidingTabIndicator.getChildAt(i);
            child.setMinimumHeight(getTabMinHeight());
            updateTabViewLayoutParams((LinearLayout.LayoutParams) child.getLayoutParams());
            if (requestLayout) {
                child.requestLayout();
            }
        }
    }

    /**
     * A tab in this layout. Instances can be created via {@link #newTab()}.
     */
    public static class Tab {

        /**
         * An invalid position for a tab.
         *
         * @see #getPosition()
         */
        public static final int INVALID_POSITION = -1;

        @Nullable
        private Object tag;
        @Nullable
        private Drawable icon;
        @Nullable
        private CharSequence text;
        // This represents the content description that has been explicitly set on the Tab or TabItem
        // in XML or through #setContentDescription. If the content description is empty, text should
        // be used as the content description instead, but contentDesc should remain empty.
        @Nullable
        private CharSequence contentDesc;
        private int position = -1;
        @Nullable
        private View customView;
        private @LabelVisibility
        int labelVisibilityMode = TAB_LABEL_VISIBILITY_LABELED;
        @Nullable
        public VerticalTabLayout parent;
        public TabView view;

        public Tab() {
        }

        /**
         * @return This Tab's tag object.
         */
        @Nullable
        public Object getTag() {
            return this.tag;
        }

        /**
         * Give this Tab an arbitrary object to hold for later use.
         *
         * @param tag Object to store
         * @return The current instance for call chaining
         */
        @NonNull
        public Tab setTag(@Nullable Object tag) {
            this.tag = tag;
            return this;
        }

        /**
         * Returns the custom view used for this tab.
         *
         * @see #setCustomView(View)
         * @see #setCustomView(int)
         */
        @Nullable
        public View getCustomView() {
            return customView;
        }

        /**
         * Set a custom view to be used for this tab.
         *
         * <p>If the provided view contains a {@link TextView} with an ID of {@link android.R.id#text1}
         * then that will be updated with the value given to {@link #setText(CharSequence)}. Similarly,
         * if this layout contains an {@link ImageView} with ID {@link android.R.id#icon} then it will
         * be updated with the value given to {@link #setIcon(Drawable)}.
         *
         * @param view Custom view to be used as a tab.
         * @return The current instance for call chaining
         */
        public Tab setCustomView(@Nullable View view) {
            customView = view;
            updateView();
            return this;
        }

        /**
         * Set a custom view to be used for this tab.
         *
         * <p>If the inflated layout contains a {@link TextView} with an ID of {@link
         * android.R.id#text1} then that will be updated with the value given to {@link
         * #setText(CharSequence)}. Similarly, if this layout contains an {@link ImageView} with ID
         * {@link android.R.id#icon} then it will be updated with the value given to {@link
         * #setIcon(Drawable)}.
         *
         * @param resId A layout resource to inflate and use as a custom tab view
         * @return The current instance for call chaining
         */
        @NonNull
        public Tab setCustomView(@LayoutRes int resId) {
            final LayoutInflater inflater = LayoutInflater.from(view.getContext());
            return this.setCustomView(inflater.inflate(resId, view, false));
        }

        /**
         * Return the icon associated with this tab.
         *
         * @return The tab's icon
         */
        @Nullable
        public Drawable getIcon() {
            return icon;
        }

        /**
         * Set the icon displayed on this tab.
         *
         * @param icon The drawable to use as an icon
         * @return The current instance for call chaining
         */
        public Tab setIcon(@Nullable Drawable icon) {
            this.icon = icon;
            if (parent.tabGravity == GRAVITY_CENTER || parent.mode == MODE_AUTO) {
                parent.updateTabViews(true);
            }

            updateView();
            if (BadgeUtils.USE_COMPAT_PARENT
                    && this.view.hasBadgeDrawable()
                    && this.view.badgeDrawable.isVisible()) {
                // Invalidate the TabView if icon visibility has changed and a badge is displayed.
                view.invalidate();
            }
            return this;
        }

        /**
         * Set the icon displayed on this tab.
         *
         * @param resId A resource ID referring to the icon that should be displayed
         * @return The current instance for call chaining
         */
        @NonNull
        public Tab setIcon(@DrawableRes int resId) {
            if (parent == null) {
                throw new IllegalArgumentException("Tab not attached to a TabLayout");
            } else {
                return setIcon(AppCompatResources.getDrawable(parent.getContext(), resId));
            }
        }

        /**
         * Return the current position of this tab in the action bar.
         *
         * @return Current position, or {@link #INVALID_POSITION} if this tab is not currently in the
         * action bar.
         */
        public int getPosition() {
            return position;
        }

        /**
         * Return the position of this tab.
         *
         * @return The tab's position
         */
        void setPosition(int position) {
            this.position = position;
        }

        /**
         * Return the text of this tab.
         *
         * @return The tab's text
         */
        @Nullable
        public CharSequence getText() {
            return text;
        }

        /**
         * Set the text displayed on this tab. Text may be truncated if there is not room to display the
         * entire string.
         *
         * @param text The text to display
         * @return The current instance for call chaining
         */
        @NonNull
        public Tab setText(@Nullable CharSequence text) {
            if (TextUtils.isEmpty(this.contentDesc) && !TextUtils.isEmpty(text)) {
                // If no content description has been set, use the text as the content description of the
                // TabView. If the text is null, don't update the content description.
                view.setContentDescription(text);
            }
            this.text = text;
            updateView();
            return this;
        }

        @NonNull
        public Tab setText(@StringRes int resId) {
            if (parent == null) {
                throw new IllegalArgumentException("Tab not attached to a TabLayout");
            } else {
                return setText(parent.getResources().getText(resId));
            }
        }

        /**
         * Creates an instance of {@link BadgeDrawable} if none exists. Initializes (if needed) and
         * returns the associated instance of {@link BadgeDrawable}.
         *
         * @return an instance of BadgeDrawable associated with {@code Tab}.
         */
        @NonNull
        public BadgeDrawable getOrCreateBadge() {
            return view.getOrCreateBadge();
        }

        /**
         * Removes the {@link BadgeDrawable}. Do nothing if none exists. Consider changing the
         * visibility of the {@link BadgeDrawable} if you only want to hide it temporarily.
         */
        public void removeBadge() {
            view.removeBadge();
        }

        /**
         * Returns an instance of {@link BadgeDrawable} associated with this tab, null if none was
         * initialized.
         */
        @Nullable
        public BadgeDrawable getBadge() {
            return this.view.getBadge();
        }

        /**
         * Sets the visibility mode for the Labels in this Tab. The valid input options are:
         *
         * <ul>
         *   <li>{@link #TAB_LABEL_VISIBILITY_UNLABELED}: Tabs will appear without labels regardless of
         *       whether text is set.
         *   <li>{@link #TAB_LABEL_VISIBILITY_LABELED}: Tabs will appear labeled if text is set.
         * </ul>
         *
         * @param mode one of {@link #TAB_LABEL_VISIBILITY_UNLABELED} or {@link
         *             #TAB_LABEL_VISIBILITY_LABELED}.
         * @return The current instance for call chaining.
         */
        @NonNull
        public Tab setTabLabelVisibility(@LabelVisibility int mode) {
            labelVisibilityMode = mode;
            if (parent.tabGravity == GRAVITY_CENTER || this.parent.mode == MODE_AUTO) {
                parent.updateTabViews(true);
            }
            updateView();
            if (BadgeUtils.USE_COMPAT_PARENT
                    && view.hasBadgeDrawable()
                    && view.badgeDrawable.isVisible()) {
                view.invalidate();
            }
            return this;
        }

        /**
         * Gets the visibility mode for the Labels in this Tab.
         *
         * @return the label visibility mode, one of {@link #TAB_LABEL_VISIBILITY_UNLABELED} or {@link
         * #TAB_LABEL_VISIBILITY_LABELED}.
         * @see #setTabLabelVisibility(int)
         */
        @LabelVisibility
        public int getTabLabelVisibility() {
            return labelVisibilityMode;
        }

        /**
         * Select this tab. Only valid if the tab has been added to the action bar.
         */
        public void select() {
            if (parent == null) {
                throw new IllegalArgumentException("Tab not attached to a VerticalTabLayout");
            }
            parent.selectTab(this);
        }

        /**
         * Returns true if this tab is currently selected.
         */
        public boolean isSelected() {
            if (parent == null) {
                throw new IllegalArgumentException("Tab not attached to a VerticalTabLayout");
            }
            return parent.getSelectedTabPosition() == position;
        }

        /**
         * Set a description of this tab's content for use in accessibility support. If no content
         * description is provided the title will be used.
         *
         * @param resId A resource ID referring to the description text
         * @return The current instance for call chaining
         * @see #setContentDescription(CharSequence)
         * @see #getContentDescription()
         */
        @NonNull
        public Tab setContentDescription(@StringRes int resId) {
            if (parent == null) {
                throw new IllegalArgumentException("Tab not attached to a TabLayout");
            }
            return setContentDescription(parent.getResources().getText(resId));
        }

        /**
         * Set a description of this tab's content for use in accessibility support. If no content
         * description is provided the title will be used.
         *
         * @param contentDesc Description of this tab's content
         * @return The current instance for call chaining
         * @see #setContentDescription(int)
         * @see #getContentDescription()
         */
        @NonNull
        public Tab setContentDescription(@Nullable CharSequence contentDesc) {
            this.contentDesc = contentDesc;
            updateView();
            return this;
        }

        /**
         * Gets a brief description of this tab's content for use in accessibility support.
         *
         * @return Description of this tab's content
         * @see #setContentDescription(CharSequence)
         * @see #setContentDescription(int)
         */
        @Nullable
        public CharSequence getContentDescription() {
            return (view == null) ? null : view.getContentDescription();
        }

        void updateView() {
            if (view != null) {
                view.update();
            }
        }

        void reset() {
            parent = null;
            view = null;
            tag = null;
            icon = null;
            text = null;
            contentDesc = null;
            position = INVALID_POSITION;
            customView = null;
        }
    }

    /**
     * A {@link LinearLayout} containing {@link Tab} instances for use with {@link VerticalTabLayout}.
     */
    public final class TabView extends LinearLayout {
        private Tab tab;
        private TextView textView;
        private ImageView iconView;
        @Nullable
        private View badgeAnchorView;
        @Nullable
        private BadgeDrawable badgeDrawable;

        @Nullable
        private View customView;
        @Nullable
        private TextView customTextView;
        @Nullable
        private ImageView customIconView;
        @Nullable
        private Drawable baseBackgroundDrawable;

        private int defaultMaxLines = 2;

        public TabView(@NonNull Context context) {
            super(context);
            updateBackgroundDrawable(context);
            ViewCompat.setPaddingRelative(
                    this, tabPaddingStart, tabPaddingTop, tabPaddingEnd, tabPaddingBottom);
            setGravity(Gravity.CENTER);
            setOrientation(inlineLabel ? HORIZONTAL : VERTICAL);
            setClickable(true);
            ViewCompat.setPointerIcon(
                    this, PointerIconCompat.getSystemIcon(getContext(), PointerIconCompat.TYPE_HAND));
            ViewCompat.setAccessibilityDelegate(this, null);
        }

        private void updateBackgroundDrawable(Context context) {
            if (tabBackgroundResId != 0) {
                baseBackgroundDrawable = AppCompatResources.getDrawable(context, tabBackgroundResId);
                if (baseBackgroundDrawable != null && baseBackgroundDrawable.isStateful()) {
                    baseBackgroundDrawable.setState(getDrawableState());
                }
            } else {
                baseBackgroundDrawable = null;
            }

            Drawable background;
            GradientDrawable contentDrawable = new GradientDrawable();
            contentDrawable.setColor(Color.TRANSPARENT);

            if (tabRippleColorStateList != null) {
                GradientDrawable maskDrawable = new GradientDrawable();
                maskDrawable.setCornerRadius(0.00001F);
                maskDrawable.setColor(Color.WHITE);

                ColorStateList rippleColor =
                        RippleUtils.convertToRippleDrawableColor(tabRippleColorStateList);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    background =
                            new RippleDrawable(
                                    rippleColor,
                                    unboundedRipple ? null : contentDrawable,
                                    unboundedRipple ? null : maskDrawable);
                } else {
                    Drawable rippleDrawable = DrawableCompat.wrap(maskDrawable);
                    DrawableCompat.setTintList(rippleDrawable, rippleColor);
                    background = new LayerDrawable(new Drawable[]{contentDrawable, rippleDrawable});
                }
            } else {
                background = contentDrawable;
            }

            ViewCompat.setBackground(this, background);
            VerticalTabLayout.this.invalidate();
        }

        /**
         * Draw the background drawable specified by tabBackground attribute onto the canvas provided.
         * This method will draw the background to the full bounds of this TabView. We provide a
         * separate method for drawing this background rather than just setting this background on the
         * TabView so that we can control when this background gets drawn. This allows us to draw the
         * tab background underneath the TabLayout selection indicator, and then draw the TabLayout
         * content (icons + labels) on top of the selection indicator.
         *
         * @param canvas canvas to draw the background on
         */
        private void drawBackground(@NonNull Canvas canvas) {
            if (baseBackgroundDrawable != null) {
                baseBackgroundDrawable.setBounds(getLeft(), getTop(), getRight(), getBottom());
                baseBackgroundDrawable.draw(canvas);
            }
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            boolean changed = false;
            int[] state = getDrawableState();
            if (baseBackgroundDrawable != null && baseBackgroundDrawable.isStateful()) {
                changed = baseBackgroundDrawable.setState(state);
            }

            if (changed) {
                invalidate();
                VerticalTabLayout.this.invalidate();
            }
        }

        @Override
        public boolean performClick() {
            final boolean handled = super.performClick();

            if (tab != null) {
                if (!handled) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                }
                tab.select();
                return true;
            } else {
                return handled;
            }
        }

        @Override
        public void setSelected(final boolean selected) {
            final boolean changed = isSelected() != selected;

            super.setSelected(selected);

            if (changed && selected && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                // Pre-JB we need to manually send the TYPE_VIEW_SELECTED event
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            }

            if (textView != null) {
                textView.setSelected(selected);
            }
            if (iconView != null) {
                iconView.setSelected(selected);
            }
            if (customView != null) {
                customView.setSelected(selected);
            }
        }

        @Override
        public void onInitializeAccessibilityEvent(@NonNull AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);
            // This view masquerades as an action bar tab.
            event.setClassName(ActionBar.Tab.class.getName());
        }

        @TargetApi(14)
        @Override
        public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            // This view masquerades as an action bar tab.
            info.setClassName(ActionBar.Tab.class.getName());
            if (badgeDrawable != null && badgeDrawable.isVisible()) {
                CharSequence customContentDescription = this.getContentDescription();
                info.setContentDescription(
                        customContentDescription + ", " + badgeDrawable.getContentDescription());
            }
        }

        @Override
        public void onMeasure(final int origWidthMeasureSpec, final int origHeightMeasureSpec) {
            // CHANGE
            // As VerticalTabLayout, height should be considered instead
            final int specHeightSize = MeasureSpec.getSize(origHeightMeasureSpec);
            final int specHeightMode = MeasureSpec.getMode(origHeightMeasureSpec);
            final int maxHeight = getTabMaxHeight();

            final int widthMeasureSpec = origWidthMeasureSpec;
            final int heightMeasureSpec;

            if (maxHeight > 0 && (specHeightMode == MeasureSpec.UNSPECIFIED || specHeightSize > maxHeight)) {
                // If we have a max height and a given spec which is either unspecified or
                // larger than the max height, update the height spec using the same mode
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(tabMaxHeight, MeasureSpec.AT_MOST);
            } else {
                // Else, use the original width spec
                heightMeasureSpec = origHeightMeasureSpec;
            }

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            // We need to switch the text size based on whether the text is spanning 2 lines or not
            if (textView != null) {
                float textSize = tabTextSize;
                int maxLines = defaultMaxLines;

                if (iconView != null && iconView.getVisibility() == VISIBLE) {
                    // If the icon view is being displayed, we limit the text to 1 line
                    maxLines = 1;
                } else if (textView != null && textView.getLineCount() > 1) {
                    // Otherwise when we have text which wraps we reduce the text size
                    textSize = tabTextMultiLineSize;
                }

                final float curTextSize = textView.getTextSize();
                final int curLineCount = textView.getLineCount();
                final int curMaxLines = TextViewCompat.getMaxLines(textView);

                if (textSize != curTextSize || (curMaxLines >= 0 && maxLines != curMaxLines)) {
                    // We've got a new text size and/or max lines...
                    boolean updateTextView = true;

                    if (mode == MODE_FIXED && textSize > curTextSize && curLineCount == 1) {
                        // If we're in fixed mode, going up in text size and currently have 1 line
                        // then it's very easy to get into an infinite recursion.
                        // To combat that we check to see if the change in text size
                        // will cause a line count change. If so, abort the size change and stick
                        // to the smaller size.
                        final Layout layout = textView.getLayout();
                        if (layout == null
                                || approximateLineWidth(layout, 0, textSize)
                                > getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) {
                            updateTextView = false;
                        }
                    }

                    if (updateTextView) {
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                        textView.setMaxLines(maxLines);
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    }
                }
            }

        }

        void setTab(@Nullable Tab tab) {
            if (tab != this.tab) {
                this.tab = tab;
                update();
            }
        }

        void reset() {
            setTab(null);
            setSelected(false);
        }

        final void update() {
            final Tab tab = this.tab;
            final View custom = tab != null ? tab.getCustomView() : null;
            if (custom != null) {
                // Make TabView the parent of Tab's custom view
                final ViewParent customParent = custom.getParent();
                // Make sure this TabView is the parent of the customView
                if (customParent != this) {
                    if (customParent != null) {
                        ((ViewGroup) customParent).removeView(custom);
                    }
                    addView(custom);
                }

                customView = custom;
                if (textView != null) {
                    textView.setVisibility(GONE);
                }

                if (iconView != null) {
                    iconView.setVisibility(GONE);
                    iconView.setImageDrawable(null);
                }

                customTextView = custom.findViewById(android.R.id.text1);
                if (customTextView != null) {
                    defaultMaxLines = TextViewCompat.getMaxLines(customTextView);
                }
                customIconView = custom.findViewById(android.R.id.icon);
            } else {
                // We do not have a custom view. Remove one if it already exists
                if (customView != null) {
                    removeView(customView);
                    customView = null;
                }
                customTextView = null;
                customIconView = null;
            }

            if (customView == null) {
                // If there isn't a custom view, we'll us our own in-built layouts
                if (iconView == null) {
                    inflateAndAddDefaultIconView();
                }

                final Drawable icon =
                        (tab != null && tab.getIcon() != null)
                                ? DrawableCompat.wrap(tab.getIcon()).mutate()
                                : null;
                if (icon != null) {
                    DrawableCompat.setTintList(icon, tabIconTint);
                    if (tabIconTintMode != null) {
                        DrawableCompat.setTintMode(icon, tabIconTintMode);
                    }
                }

                if (textView == null) {
                    inflateAndAddDefaultTextView();
                    defaultMaxLines = TextViewCompat.getMaxLines(textView);
                }
                TextViewCompat.setTextAppearance(textView, tabTextAppearance);
                if (tabTextColors != null) {
                    textView.setTextColor(tabTextColors);
                }
                updateTextAndIcon(textView, iconView);

                tryUpdateBadgeAnchor();
                addOnLayoutChangeListener(iconView);
                addOnLayoutChangeListener(textView);
            } else {
                // Else, we'll see if there is a TextView or ImageView present and update them
                if (customTextView != null || customIconView != null) {
                    updateTextAndIcon(customTextView, customIconView);
                }
            }

            if (tab != null && !TextUtils.isEmpty(tab.contentDesc)) {
                setContentDescription(tab.contentDesc);
            }

            setSelected(tab != null && tab.isSelected());
        }

        private void inflateAndAddDefaultIconView() {
            ViewGroup iconViewParent = this;
            if (BadgeUtils.USE_COMPAT_PARENT) {
                iconViewParent = createPreApi18BadgeAnchorRoot();
                addView(iconViewParent, 0);
            }

            iconView = (ImageView) LayoutInflater.from(getContext())
                    .inflate(R.layout.design_layout_tab_icon, iconViewParent, false);
            iconViewParent.addView(iconView, 0);
        }

        private void inflateAndAddDefaultTextView() {
            ViewGroup textViewParent = this;
            if (BadgeUtils.USE_COMPAT_PARENT) {
                textViewParent = createPreApi18BadgeAnchorRoot();
                addView(textViewParent);
            }

            textView = (TextView) LayoutInflater.from(getContext())
                    .inflate(R.layout.design_layout_tab_text, textViewParent, false);
            textViewParent.addView(textView);
        }

        @NonNull
        private FrameLayout createPreApi18BadgeAnchorRoot() {
            FrameLayout frameLayout = new FrameLayout(getContext());
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            frameLayout.setLayoutParams(layoutParams);
            return frameLayout;
        }

        /**
         * Creates an instance of {@link BadgeDrawable} if none exists. Initializes (if needed) and
         * returns the associated instance of {@link BadgeDrawable}.
         *
         * @return an instance of BadgeDrawable associated with {@code Tab}.
         */
        @NonNull
        private BadgeDrawable getOrCreateBadge() {
            // Creates a new instance if one is not already initialized for this TabView.
            if (badgeDrawable == null) {
                badgeDrawable = BadgeDrawable.create(getContext());
            }
            tryUpdateBadgeAnchor();
            if (badgeDrawable == null) {
                throw new IllegalStateException("Unable to create badge");
            }
            return badgeDrawable;
        }

        @Nullable
        private BadgeDrawable getBadge() {
            return badgeDrawable;
        }

        private void removeBadge() {
            if (badgeAnchorView != null) {
                tryRemoveBadgeFromAnchor();
            }
            badgeDrawable = null;
        }

        private void addOnLayoutChangeListener(@Nullable final View view) {
            if (view == null) {
                return;
            }
            view.addOnLayoutChangeListener(
                    (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                        if (view.getVisibility() == VISIBLE) {
                            tryUpdateBadgeDrawableBounds(view);
                        }
                    });
        }

        private void tryUpdateBadgeAnchor() {
            if (!hasBadgeDrawable()) {
                return;
            }
            if (customView != null) {
                // badge anchor is managed by custom view
                tryRemoveBadgeFromAnchor();
            } else {
                if (iconView != null && tab != null && tab.getIcon() != null) {
                    // Priority badge anchor to in-built iconView
                    if (badgeAnchorView != iconView) {
                        tryRemoveBadgeFromAnchor();
                        tryAttachBadgeToAnchor(iconView);
                    } else {
                        tryUpdateBadgeDrawableBounds(iconView);
                    }
                } else if (textView != null
                        && tab != null
                        && tab.getTabLabelVisibility() == TAB_LABEL_VISIBILITY_LABELED) {
                    // Badge anchor is textView
                    if (badgeAnchorView != textView) {
                        tryRemoveBadgeFromAnchor();
                        tryAttachBadgeToAnchor(textView);
                    } else {
                        tryUpdateBadgeDrawableBounds(textView);
                    }
                } else {
                    tryRemoveBadgeFromAnchor();
                }
            }
        }

        private void tryAttachBadgeToAnchor(@Nullable View anchorView) {
            if (!hasBadgeDrawable()) {
                return;
            }
            if (anchorView != null) {
                // Avoid clipping a badge if it's displayed.
                setClipChildren(false);
                setClipToPadding(false);
                BadgeUtils.attachBadgeDrawable(
                        badgeDrawable,
                        anchorView,
                        getCustomParentForBadge(anchorView));
                badgeAnchorView = anchorView;
            }
        }

        private void tryRemoveBadgeFromAnchor() {
            if (!this.hasBadgeDrawable()) {
                return;
            }
            if (badgeAnchorView != null) {
                // Clip children/view to padding when no badge is displayed.
                setClipChildren(true);
                setClipToPadding(true);
                BadgeUtils.detachBadgeDrawable(
                        badgeDrawable,
                        badgeAnchorView,
                        getCustomParentForBadge(this.badgeAnchorView));
                badgeAnchorView = null;
            }
        }

        final void updateOrientation() {
            // horizontal orientation if label is inlined, otherwise vertical
            setOrientation(inlineLabel ? HORIZONTAL : VERTICAL);
            if (customTextView != null || customIconView != null) {
                updateTextAndIcon(customTextView, customIconView);
            } else {
                updateTextAndIcon(textView, iconView);
            }
        }

        private void updateTextAndIcon(
                @Nullable TextView textView,
                @Nullable ImageView iconView) {
            final Drawable icon =
                    (tab != null && tab.getIcon() != null)
                            ? DrawableCompat.wrap(tab.getIcon()).mutate()
                            : null;
            final CharSequence text = tab != null ? tab.getText() : null;

            if (iconView != null) {
                if (icon != null) {
                    iconView.setImageDrawable(icon);
                    iconView.setVisibility(VISIBLE);
                    setVisibility(VISIBLE);
                } else {
                    iconView.setVisibility(GONE);
                    iconView.setImageDrawable(null);
                }
            }

            final boolean hasText = !TextUtils.isEmpty(text);
            if (textView != null) {
                if (hasText) {
                    textView.setText(text);
                    if (tab.labelVisibilityMode == TAB_LABEL_VISIBILITY_LABELED) {
                        textView.setVisibility(VISIBLE);
                    } else {
                        textView.setVisibility(GONE);
                    }
                    setVisibility(VISIBLE);
                } else {
                    textView.setVisibility(GONE);
                    textView.setText(null);
                }
            }

            if (iconView != null) {
                MarginLayoutParams lp = (MarginLayoutParams) iconView.getLayoutParams();
                int iconMargin = 0;
                if (hasText && iconView.getVisibility() == VISIBLE) {
                    // If we're showing both text and icon, add some margin bottom to the icon
                    iconMargin = (int) ViewUtils.dpToPx(getContext(), DEFAULT_GAP_TEXT_ICON);
                }

                if (inlineLabel) {
                    if (iconMargin != MarginLayoutParamsCompat.getMarginEnd(lp)) {
                        MarginLayoutParamsCompat.setMarginEnd(lp, iconMargin);
                        lp.bottomMargin = 0;
                        // Calls resolveLayoutParams(), necessary for layout direction
                        iconView.setLayoutParams(lp);
                        iconView.requestLayout();
                    }
                } else {
                    if (iconMargin != lp.bottomMargin) {
                        lp.bottomMargin = iconMargin;
                        MarginLayoutParamsCompat.setMarginEnd(lp, 0);
                        iconView.setLayoutParams(lp);
                        iconView.requestLayout();
                    }
                }
            }

            final CharSequence contentDesc = tab != null ? tab.contentDesc : null;
            TooltipCompat.setTooltipText(this, hasText ? null : contentDesc);
        }

        private void tryUpdateBadgeDrawableBounds(@NonNull View anchor) {
            // Check that this view is the badge's current anchor view.
            if (hasBadgeDrawable() && anchor == badgeAnchorView) {
                BadgeUtils.setBadgeDrawableBounds(
                        badgeDrawable,
                        anchor,
                        getCustomParentForBadge(anchor));
            }
        }

        private boolean hasBadgeDrawable() {
            return badgeDrawable != null;
        }

        @Nullable
        private FrameLayout getCustomParentForBadge(@NonNull View anchor) {
            if (anchor != iconView && anchor != textView) {
                return null;
            }
            return BadgeUtils.USE_COMPAT_PARENT ? (FrameLayout) anchor.getParent() : null;
        }

        /**
         * Calculates the width of the TabView's content.
         *
         * @return Width of the tab label, if present, or the width of the tab icon, if present. If tabs
         * is in inline mode, returns the sum of both the icon and tab label widths.
         */
        private int getContentHeight() {
            // CHANGE
            boolean initialized = false;
            int top = 0;
            int bottom = 0;

            for (View view : new View[]{textView, iconView, customView}) {
                if (view != null && view.getVisibility() == VISIBLE) {
                    top = initialized ? Math.min(top, view.getTop()) : view.getTop();
                    bottom = initialized ? Math.max(bottom, view.getBottom()) : view.getBottom();
                    initialized = true;
                }
            }
            return bottom - top;
        }

        @Nullable
        public Tab getTab() {
            return tab;
        }

        /**
         * Approximates a given lines width with the new provided text size.
         */
        private float approximateLineWidth(@NonNull Layout layout, int line, float textSize) {
            return layout.getLineWidth(line) * (textSize / layout.getPaint().getTextSize());
        }
    }

    private class SlidingTabIndicator extends LinearLayout {
        private int selectedIndicatorWidth;
        @NonNull
        private final Paint selectedIndicatorPaint;
        @NonNull
        private final GradientDrawable defaultSelectionIndicator;

        int selectedPosition = -1;
        float selectionOffset;

        private int layoutDirection = -1;

        private int indicatorTop = -1;
        private int indicatorBottom = -1;

        private ValueAnimator indicatorAnimator;

        public SlidingTabIndicator(Context context) {
            super(context);
            setWillNotDraw(false);
            // CHANGE
            setOrientation(LinearLayout.VERTICAL);
            selectedIndicatorPaint = new Paint();
            defaultSelectionIndicator = new GradientDrawable();
        }

        void setSelectedIndicatorColor(int color) {
            if (selectedIndicatorPaint.getColor() != color) {
                selectedIndicatorPaint.setColor(color);
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }

        void setSelectedIndicatorWidth(int width) {
            if (selectedIndicatorWidth != width) {
                selectedIndicatorWidth = width;
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }

        boolean childrenNeedLayout() {
            for (int i = 0, z = getChildCount(); i < z; ++i) {
                final View child = getChildAt(i);
                // CHANGE
                if (child.getHeight() <= 0) {
                    return true;
                }
            }
            return false;
        }

        void setIndicatorPositionFromTabPosition(int position, float positionOffset) {
            if (indicatorAnimator != null && indicatorAnimator.isRunning()) {
                indicatorAnimator.cancel();
            }

            selectedPosition = position;
            selectionOffset = positionOffset;
            updateIndicatorPosition();
        }

        float getIndicatorPosition() {
            return selectedPosition + selectionOffset;
        }

        @Override
        public void onRtlPropertiesChanged(int layoutDirection) {
            super.onRtlPropertiesChanged(layoutDirection);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                if (this.layoutDirection != layoutDirection) {
                    requestLayout();
                    this.layoutDirection = layoutDirection;
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            // CHANGE
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
                // ScrollView will first measure use with UNSPECIFIED, and then with
                // EXACTLY. Ignore the first call since anything we do will be overwritten anyway
                return;
            }

            // GRAVITY_CENTER will make all tabs the same height as the largest tab, and center them in the
            // SlidingTabIndicator's height (with a "gutter" of padding on either side). If the Tabs do not
            // fit in the SlidingTabIndicator, then fall back to GRAVITY_FILL behavior.
            if (tabGravity == GRAVITY_CENTER || mode == MODE_AUTO) {
                final int count = getChildCount();

                // First we'll find the tallest tab
                int largestTabHeight = 0;
                for (int i = 0, z = count; i < z; ++i) {
                    View child = getChildAt(i);
                    if (child.getVisibility() == VISIBLE) {
                        largestTabHeight = Math.max(largestTabHeight, child.getMeasuredHeight());
                    }
                }

                if (largestTabHeight <= 0) {
                    // If we don't have a largest child yet, skip until the next measure pass
                    return;
                }

                final int gutter = (int) ViewUtils.dpToPx(getContext(), FIXED_WRAP_GUTTER_MIN);
                boolean remeasure = false;

                if (largestTabHeight * count <= getMeasuredHeight() - gutter * 2) {
                    // If the tabs fit within our height minus gutters, we will set all tabs to have
                    // the same height
                    for (int i = 0; i < count; ++i) {
                        final LinearLayout.LayoutParams lp =
                                (LinearLayout.LayoutParams) getChildAt(i).getLayoutParams();
                        if (lp.height != largestTabHeight || lp.weight != 0) {
                            lp.height = largestTabHeight;
                            lp.weight = 0;
                            remeasure = true;
                        }
                    }
                } else {
                    // If the tabs will wrap to be larger than the height minus gutters, we need
                    // to switch to GRAVITY_FILL.
                    // TODO (b/129799806): This overrides the user TabGravity setting.
                    tabGravity = GRAVITY_FILL;
                    updateTabViews(false);
                    remeasure = true;
                }

                if (remeasure) {
                    // Now re-measure after our changes
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);

            if (indicatorAnimator != null && indicatorAnimator.isRunning()) {
                // If we're currently running an animation, lets cancel it and start a
                // new animation with the remaining duration
                indicatorAnimator.cancel();

                final long duration = indicatorAnimator.getDuration();
                animateIndicatorToPosition(
                        selectedPosition,
                        Math.round(
                                (1f - indicatorAnimator.getAnimatedFraction()) * duration));
            } else {
                // If we've been laid out, update the indicator position
                updateIndicatorPosition();
            }
        }

        private void updateIndicatorPosition() {
            // CHANGE
            final View selectedTitle = getChildAt(selectedPosition);
            int top;
            int bottom;

            if (selectedTitle != null && selectedTitle.getHeight() > 0) {
                top = selectedTitle.getTop();
                bottom = selectedTitle.getBottom();

                // If tabIndicatorFullWidth is not set, indicator's height is the same as the height
                // of the content of the selected tab.
                if (!tabIndicatorFullWidth && selectedTitle instanceof TabView) {
                    calculateTabViewContentBounds((TabView) selectedTitle, tabViewContentBounds);
                    top = (int) tabViewContentBounds.top;
                    bottom = (int) tabViewContentBounds.bottom;
                }

                if (selectionOffset > 0f && selectedPosition < getChildCount() - 1) {
                    // Draw the selection partway between the tabs
                    View nextTitle = getChildAt(selectedPosition + 1);
                    int nextTitleTop = nextTitle.getTop();
                    int nextTitleBottom = nextTitle.getBottom();

                    if (!tabIndicatorFullWidth && nextTitle instanceof TabView) {
                        calculateTabViewContentBounds((TabView) nextTitle, tabViewContentBounds);
                        nextTitleTop = (int) tabViewContentBounds.top;
                        nextTitleBottom = (int) tabViewContentBounds.bottom;
                    }

                    top = (int) (selectionOffset * nextTitleTop + (1.0f - selectionOffset) * top);
                    bottom = (int) (selectionOffset * nextTitleBottom + (1.0f - selectionOffset) * bottom);
                }
            } else {
                top = bottom = -1;
            }

            setIndicatorPosition(top, bottom);
        }

        void setIndicatorPosition(int top, int bottom) {
            if (top != indicatorTop || bottom != indicatorBottom) {
                // If the indicator's left/right has changed, invalidate
                indicatorTop = top;
                indicatorBottom = bottom;
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }

        void animateIndicatorToPosition(final int position, int duration) {
            // CHANGE
            if (indicatorAnimator != null && indicatorAnimator.isRunning()) {
                indicatorAnimator.cancel();
            }

            final View targetView = getChildAt(position);
            if (targetView == null) {
                // If we don't have a view, just update the position now and return
                updateIndicatorPosition();
                return;
            }

            int targetTop = targetView.getTop();
            int targetBottom = targetView.getBottom();

            if (!tabIndicatorFullWidth && targetView instanceof TabView) {
                calculateTabViewContentBounds((TabView) targetView, tabViewContentBounds);
                targetTop = (int) tabViewContentBounds.top;
                targetBottom = (int) tabViewContentBounds.bottom;
            }

            final int finalTargetTop = targetTop;
            final int finalTargetBottom = targetBottom;

            final int startTop = indicatorTop;
            final int startBottom = indicatorBottom;

            if (startTop != finalTargetTop || startBottom != finalTargetBottom) {
                ValueAnimator animator = indicatorAnimator = new ValueAnimator();
                animator.setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
                animator.setDuration(duration);
                animator.setFloatValues(0, 1);
                animator.addUpdateListener(
                        valueAnimator -> {
                            final float fraction = valueAnimator.getAnimatedFraction();
                            setIndicatorPosition(
                                    AnimationUtils.lerp(startTop, finalTargetTop, fraction),
                                    AnimationUtils.lerp(startBottom, finalTargetBottom, fraction));
                        });
                animator.addListener(
                        new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                selectedPosition = position;
                                selectionOffset = 0f;
                            }
                        });
                animator.start();
            }
        }

        /**
         * Given a {@link TabView}, calculate the top and bottom bounds of its content.
         *
         * <p>If only text label is present, calculates the width of the text label. If only icon is
         * present, calculates the width of the icon. If both are present, the text label bounds take
         * precedence. If both are present and inline mode is enabled, the sum of the bounds of the both
         * the text label and icon are calculated. If neither are present or if the calculated
         * difference between the left and right bounds is less than 24dp, then left and right bounds
         * are adjusted such that the difference between them is equal to 24dp.
         *
         * @param tabView {@link TabView} for which to calculate left and right content bounds.
         */
        private void calculateTabViewContentBounds(
                @NonNull TabView tabView,
                @NonNull RectF contentBounds) {
            // CHANGE
            int tabViewContentHeight = tabView.getContentHeight();
            int minIndicatorHeight = (int) ViewUtils.dpToPx(getContext(), MIN_INDICATOR_HEIGHT);

            if (tabViewContentHeight < minIndicatorHeight) {
                tabViewContentHeight = minIndicatorHeight;
            }

            int tabViewCenter = (tabView.getTop() + tabView.getBottom()) / 2;
            int contentTopBounds = tabViewCenter - (tabViewContentHeight / 2);
            int contentBottomBounds = tabViewCenter + (tabViewContentHeight / 2);

            contentBounds.set(0, contentTopBounds, 0, contentBottomBounds);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            // CHANGE
            int indicatorWidth = 0;
            if (tabSelectedIndicator != null) {
                // TODO: change default selected indicator's height to be width
                indicatorWidth = tabSelectedIndicator.getIntrinsicHeight();
            }
            if (selectedIndicatorWidth >= 0) {
                indicatorWidth = selectedIndicatorWidth;
            }

            int indicatorLeft = 0;
            int indicatorRight = 0;

            switch (tabIndicatorGravity) {
                case INDICATOR_GRAVITY_LEFT:
                    indicatorLeft = 0;
                    indicatorRight = indicatorWidth;
                    break;
                case INDICATOR_GRAVITY_CENTER:
                    indicatorLeft = (getWidth() - indicatorWidth) / 2;
                    indicatorRight = (getWidth() + indicatorWidth) / 2;
                    break;
                case INDICATOR_GRAVITY_RIGHT:
                    indicatorLeft = getWidth() - indicatorWidth;
                    indicatorRight = getWidth();
                    break;
                case INDICATOR_GRAVITY_STRETCH:
                    indicatorLeft = 0;
                    indicatorRight = getWidth();
                    break;
                default:
                    break;
            }

            // Draw the selection indicator on top of tab item backgrounds
            if (indicatorTop >= 0 && indicatorBottom > indicatorTop) {
                Drawable selectedIndicator;
                selectedIndicator =
                        DrawableCompat.wrap(
                                tabSelectedIndicator != null
                                        ? tabSelectedIndicator
                                        : defaultSelectionIndicator);
                selectedIndicator.setBounds(indicatorLeft, indicatorTop, indicatorRight, indicatorBottom);
                if (selectedIndicatorPaint != null) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                        // Drawable doesn't implement setTint in API 21
                        selectedIndicator.setColorFilter(
                                selectedIndicatorPaint.getColor(), PorterDuff.Mode.SRC_IN);
                    } else {
                        DrawableCompat.setTint(selectedIndicator, selectedIndicatorPaint.getColor());
                    }
                }
                selectedIndicator.draw(canvas);
            }

            // Draw the tab item contents (icon and label) on top of the background + indicator layers
            super.draw(canvas);
        }
    }

    @NonNull
    private static ColorStateList createColorStateList(
            int defaultColor,
            int selectedColor) {
        final int[][] states = new int[2][];
        final int[] colors = new int[2];
        int i = 0;

        states[i] = SELECTED_STATE_SET;
        colors[i] = selectedColor;
        ++i;

        // Default enabled state
        states[i] = EMPTY_STATE_SET;
        colors[i] = defaultColor;

        return new ColorStateList(states, colors);
    }

    @Dimension(unit = Dimension.DP)
    private int getDefaultWidth() {
        boolean hasIconAndText = false;
        for (int i = 0, count = tabs.size(); i < count; ++i) {
            Tab tab = tabs.get(i);
            if (tab != null && tab.getIcon() != null && !TextUtils.isEmpty(tab.getText())) {
                hasIconAndText = true;
                break;
            }
        }
        // If any tab has icon and text then use DEFAULT_WIDTH_WITH_TEXT_ICON
        return (hasIconAndText && !inlineLabel) ? DEFAULT_WIDTH_WITH_TEXT_ICON : DEFAULT_WIDTH;
    }

    private int getTabMinHeight() {
        if (requestedTabMinHeight != INVALID_HEIGHT) {
            return requestedTabMinHeight;
        }
        return (mode == MODE_SCROLLABLE || mode == MODE_AUTO) ? scrollableTabMinHeight : 0;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        // We don't care about the layout params of any views added to us, since we don't actually
        // add them. The only view we add is the SlidingTabStrip, which is done manually.
        // We return the default layout params so that we don't blow up if we're given a TabItem
        // without android:layout_* values.
        return generateDefaultLayoutParams();
    }

    int getTabMaxHeight() {
        return tabMaxHeight;
    }

    /**
     * A {@link ViewPager.OnPageChangeListener} class which contains the necessary calls back to the
     * provided {@link VerticalTabLayout} so that the tab position is kept in sync.
     *
     * <p>This class stores the provided TabLayout weakly, meaning that you can use {@link
     * ViewPager#addOnPageChangeListener(ViewPager.OnPageChangeListener)
     * addOnPageChangeListener(OnPageChangeListener)} without removing the listener and not cause a
     * leak.
     */
    public static class PageChangeListener implements ViewPager.OnPageChangeListener {
        @NonNull
        private final WeakReference<VerticalTabLayout> tabLayoutRef;
        private int previousScrollState;
        private int scrollState;

        public PageChangeListener(VerticalTabLayout tabLayout) {
            tabLayoutRef = new WeakReference<>(tabLayout);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            final VerticalTabLayout tabLayout = tabLayoutRef.get();
            if (tabLayout != null) {
                // Only update the text selection if we're not settling, or we are settling after
                // being dragged
                final boolean updateText =
                        scrollState != SCROLL_STATE_SETTLING || previousScrollState == SCROLL_STATE_DRAGGING;
                // Update the indicator if we're not settling after being idle. This is caused
                // from a setCurrentItem() call and will be handled by an animation from
                // onPageSelected() instead.
                final boolean updateIndicator =
                        !(scrollState == SCROLL_STATE_SETTLING && previousScrollState == SCROLL_STATE_IDLE);
                tabLayout.setScrollPosition(position, positionOffset, updateText, updateIndicator);
            }
        }

        @Override
        public void onPageSelected(int position) {
            final VerticalTabLayout tabLayout = tabLayoutRef.get();
            if (tabLayout != null
                    && tabLayout.getSelectedTabPosition() != position
                    && position < tabLayout.getTabCount()) {
                // Select the tab, only updating the indicator if we're not being dragged/settled
                // (since onPageScrolled() will handle that)
                final boolean updateIndicator =
                        scrollState == SCROLL_STATE_IDLE
                                || (scrollState == SCROLL_STATE_SETTLING
                                && previousScrollState == SCROLL_STATE_IDLE);
                tabLayout.selectTab(tabLayout.getTabAt(position), updateIndicator);
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            previousScrollState = scrollState;
            scrollState = state;
        }

        void reset() {
            previousScrollState = scrollState = SCROLL_STATE_IDLE;
        }
    }

    /**
     * A {@link OnTabSelectedListener} class which contains the necessary calls back to the
     * provided {@link ViewPager} so that the tab position is kept in sync.
     */
    public static class ViewPagerOnTabSelectedListener implements OnTabSelectedListener {
        private final ViewPager viewPager;

        public ViewPagerOnTabSelectedListener(ViewPager viewPager) {
            this.viewPager = viewPager;
        }

        @Override
        public void onTabSelected(@NonNull Tab tab) {
            viewPager.setCurrentItem(tab.getPosition());
        }

        @Override
        public void onTabUnselected(Tab tab) {
            // No-op
        }

        @Override
        public void onTabReselected(Tab tab) {
            // No-op
        }
    }

    private class PagerAdapterObserver extends DataSetObserver {
        PagerAdapterObserver() {
        }

        @Override
        public void onChanged() {
            populateFromPagerAdapter();
        }

        @Override
        public void onInvalidated() {
            populateFromPagerAdapter();
        }
    }

    private class AdapterChangeListener implements ViewPager.OnAdapterChangeListener {
        private boolean autoRefresh;

        AdapterChangeListener() {
        }

        @Override
        public void onAdapterChanged(
                @NonNull ViewPager viewPager,
                @Nullable PagerAdapter oldAdapter,
                @Nullable PagerAdapter newAdapter) {
            if (VerticalTabLayout.this.viewPager == viewPager) {
                setPagerAdapter(newAdapter, autoRefresh);
            }
        }

        void setAutoRefresh(boolean autoRefresh) {
            this.autoRefresh = autoRefresh;
        }
    }
}
