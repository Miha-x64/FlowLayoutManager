package ru.astrocode.flm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Trace;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Orientation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static androidx.recyclerview.widget.RecyclerView.HORIZONTAL;
import static androidx.recyclerview.widget.RecyclerView.VERTICAL;

/**
 * Created by Astrocode on 26.05.18.
 * Enhanced by Mike Gorünóv in 05.2022.
 */
public class FlowLayoutManager extends RecyclerView.LayoutManager implements RecyclerView.SmoothScroller.ScrollVectorProvider {

    public static final Object ELLIPSIS_COUNT_CHANGED_PAYLOAD = new Object();
    private static final List<Object> ELLIPSIS_COUNT_CHANGED_PAYLOAD_LIST = Collections.singletonList(ELLIPSIS_COUNT_CHANGED_PAYLOAD);

    private final static String TAG_FIRST_ITEM_ADAPTER_INDEX = "TAG_FIRST_ITEM_ADAPTER_INDEX";
    private final static String TAG_FIRST_LINE_START_POSITION = "TAG_FIRST_LINE_START_POSITION";

    private final static String ERROR_UNKNOWN_ORIENTATION = "Unknown orientation!";
    private final static String ERROR_BAD_ARGUMENT = "Inappropriate field value!";

    private int mGravity;
    private int mOrientation;

    private int mMaxItemsInLine;
    private int mMaxLines = Integer.MAX_VALUE;
    private boolean mEllipsize = false;
    private int mEllipsisCount = -1;
    private int mLookBack = LookBack.NONE;

    private int mSpacingBetweenItems;
    private int mSpacingBetweenLines;

    private LMHelper mLayoutManagerHelper;

    private final ArrayList<Line> mCurrentLines;

    private int mFirstItemAdapterIndex;
    private int mFirstLineStartPosition;

    // this crap is sorted, touch with care
    private static final int[] ATTRS = {
        android.R.attr.ellipsize,
        android.R.attr.gravity,
        android.R.attr.orientation,
        android.R.attr.spacing,
        android.R.attr.maxItemsPerRow,
        android.R.attr.maxLines,
        android.R.attr.lineSpacingExtra,
    };
    @SuppressLint("ResourceType")
    public FlowLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes));
    }
    @SuppressLint("ResourceType")
    private FlowLayoutManager(TypedArray ta) {
        this(
            ta.getInt(2, VERTICAL), ta.getInt(1, Gravity.START),
            ta.getInt(4, Integer.MAX_VALUE), ta.getDimensionPixelOffset(3, 0), ta.getDimensionPixelOffset(6, 0));
        maxLines(ta.getInt(5, Integer.MAX_VALUE), ta.getInt(0, 0) == 3 /*ellipsize="end"*/, false);
        ta.recycle();
    }

    public FlowLayoutManager(@Orientation int orientation) {
        this(orientation, Gravity.START, Integer.MAX_VALUE, 0, 0);
    }

    public FlowLayoutManager(@Orientation int orientation, int gravity) {
        this(orientation, gravity, Integer.MAX_VALUE, 0, 0);
    }

    private FlowLayoutManager(
        @Orientation int orientation, int gravity,
        int maxItemsInLine, @Px int spacingBetweenItems, @Px int spacingBetweenLines) {
        mCurrentLines = new ArrayList<>();

        mGravity = gravity;

        mFirstItemAdapterIndex = 0;
        mFirstLineStartPosition = -1;

        if (maxItemsInLine <= 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        mMaxItemsInLine = maxItemsInLine;

        if (mSpacingBetweenItems < 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        mSpacingBetweenItems = spacingBetweenItems;

        if (mSpacingBetweenLines < 0)
            throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        mSpacingBetweenLines = spacingBetweenLines;

        if (orientation != HORIZONTAL && orientation != VERTICAL)
            throw new IllegalArgumentException(ERROR_UNKNOWN_ORIENTATION);
        mOrientation = orientation;

        mLayoutManagerHelper = LMHelper.createLayoutManagerHelper(this, orientation, mGravity);
    }

    @Override public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override public boolean isAutoMeasureEnabled() {
        return true;
    }

    @Override public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        Trace.beginSection("FLM#onLayoutChildren");
        Line currentLine = null;

        if (mFirstLineStartPosition == -1) {
            mFirstLineStartPosition = mLayoutManagerHelper.getStartPadding();
        }

        int topOrLeft = mFirstLineStartPosition;

        detachAndScrapAttachedViews(recycler);
        mCurrentLines.clear();

        for (int i = mFirstItemAdapterIndex;
             i < contentItemCount() && mCurrentLines.size() < mMaxLines;
             i += currentLine.mItemsCount) {

            currentLine = addLineToEnd(i, topOrLeft, recycler, mCurrentLines.size() + 1 == mMaxLines);
            mCurrentLines.add(currentLine);

            topOrLeft = mSpacingBetweenLines + currentLine.mEndValueOfTheHighestItem;

            if (mLayoutManagerHelper.isFinite() &&
                currentLine.mEndValueOfTheHighestItem > mLayoutManagerHelper.getEnd()) {
                break;
            }
        }

        if (mFirstItemAdapterIndex > 0 && currentLine != null) {
            int availableOffset =
                currentLine.mEndValueOfTheHighestItem - mLayoutManagerHelper.getEnd() + mLayoutManagerHelper.getEndPadding();

            if (availableOffset < 0) {
                if (mOrientation == VERTICAL) scrollVerticallyBy(availableOffset, recycler, state);
                else scrollHorizontallyBy(availableOffset, recycler, state);
            }
        }
        Trace.endSection();
    }

    @Override public void onRestoreInstanceState(Parcelable state) {
        Bundle data = (Bundle) state;
        mFirstItemAdapterIndex = data.getInt(TAG_FIRST_ITEM_ADAPTER_INDEX);
        mFirstLineStartPosition = data.getInt(TAG_FIRST_LINE_START_POSITION);
    }

    @Override public Parcelable onSaveInstanceState() {
        Bundle data = new Bundle(2);
        data.putInt(TAG_FIRST_ITEM_ADAPTER_INDEX, mFirstItemAdapterIndex);
        data.putInt(TAG_FIRST_LINE_START_POSITION, mFirstLineStartPosition);
        return data;
    }

    /**
     * Change orientation of the layout manager
     *
     * @param orientation New orientation.
     */

    public FlowLayoutManager orientation(@Orientation int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL)
            throw new IllegalArgumentException(ERROR_UNKNOWN_ORIENTATION);
        assertNotInLayoutOrScroll(null);

        if (orientation != mOrientation) {
            mOrientation = orientation;
            mLayoutManagerHelper = LMHelper.createLayoutManagerHelper(this, orientation, mGravity);

            requestLayout();
        }

        return this;
    }
    /**
     * Return current orientation of the layout manager.
     */
    @Orientation public int orientation() {
        return mOrientation;
    }

    public FlowLayoutManager maxItemsInLine(int maxItemsInLine) {
        if (maxItemsInLine <= 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        assertNotInLayoutOrScroll(null);
        mMaxItemsInLine = maxItemsInLine;
        requestLayout();
        return this;
    }
    public int maxItemsInLine() {
        return mMaxItemsInLine;
    }

    /**
     * Update maxLine constraint.
     * @param maxLines  max line count
     * @param ellipsize show “ellipsis” view (using last adapter element)
     * @param notify    notify “ellipsis” view changed (leads to animated change) (applicable only if {@param ellipsize})
     */
    public FlowLayoutManager maxLines(int maxLines, boolean ellipsize, boolean notify) {
        if (maxLines <= 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        assertNotInLayoutOrScroll(null);
        if (mMaxLines != maxLines || mEllipsize != ellipsize) {
            mMaxLines = maxLines;
            mEllipsisCount = -1;
            if ((mEllipsize = ellipsize) && notify) {
                RecyclerView rv;
                RecyclerView.Adapter<?> a;
                if ((rv = findRV()) != null && (a = rv.getAdapter()) != null)
                    a.notifyItemChanged(getItemCount() - 1, ELLIPSIS_COUNT_CHANGED_PAYLOAD);
            }
            requestLayout();
        }
        return this;
    }
    public int maxLines() {
        return mMaxLines;
    }
    public boolean ellipsize() {
        return mEllipsize;
    }

    /**
     * Avoid having different layout when scrolling back.
     * Sacrifice performance by laying out extra views.
     * Super useful when there are several MATCH_PARENT items which separate the layout into different sub-flows.
     * Consider increasing {@link androidx.recyclerview.widget.RecyclerView.RecycledViewPool#setMaxRecycledViews(int, int) scrap size}
     * to average section size so the pool can recycle properly.
     * @param count max number of views to lay out
     */
    public FlowLayoutManager lookBack(@IntRange(from = 0L) @LookBack int count) {
        if (count < 0) throw new IllegalArgumentException();
        assertNotInLayoutOrScroll(null);
        this.mLookBack = count;
        return this;
    }
    @IntRange(from = 0L) public int lookBack() {
        return mLookBack;
    }

    @Retention(RetentionPolicy.SOURCE) @Target(ElementType.PARAMETER) @IntDef({LookBack.NONE, LookBack.EXACT})
    public @interface LookBack { int NONE = 0; int EXACT = Integer.MAX_VALUE; }

    /**
     * Returns number of hidden views, or -1 if the value is not known yet or ellipsize is disabled
     * @return number of hidden rows
     */
    public int ellipsisCount() {
        return mEllipsisCount;
    }

    public FlowLayoutManager spacingBetweenItems(@Px int spacingBetweenItems) {
        if (spacingBetweenItems < 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        assertNotInLayoutOrScroll(null);
        mSpacingBetweenItems = spacingBetweenItems;
        requestLayout();
        return this;
    }
    public int spacingBetweenItems() {
        return mSpacingBetweenItems;
    }

    public FlowLayoutManager spacingBetweenLines(@Px int spacingBetweenLines) {
        if (spacingBetweenLines < 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        assertNotInLayoutOrScroll(null);
        mSpacingBetweenLines = spacingBetweenLines;
        requestLayout();
        return this;
    }
    public int spacingBetweenLines() {
        return mSpacingBetweenLines;
    }

    public FlowLayoutManager spacing(@Px int betweenItems, @Px int betweenLines) {
        if (betweenItems < 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        if (betweenLines < 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        assertNotInLayoutOrScroll(null);
        mSpacingBetweenItems = betweenItems;
        mSpacingBetweenLines = betweenLines;
        requestLayout();
        return this;
    }

    /**
     * Change gravity of the layout manager.
     *
     * @param gravity New gravity.
     */
    public FlowLayoutManager gravity(int gravity) {
        assertNotInLayoutOrScroll(null);

        if (gravity != mGravity) {
            mGravity = gravity;
            mLayoutManagerHelper.mGravity = gravity;

            requestLayout();
        }
        return this;
    }

    /**
     * Return current gravity of the layout manager.
     */
    public int gravity() {
        return mGravity;
    }

    /**
     * Add one line to the end of recyclerView.
     *
     * @param startAdapterIndex Adapter index of first item of new line.
     * @param start             Start position(Top - if orientation is VERTICAL or Left - if orientation is HORIZONTAL) of the new line.
     * @return New line.
     */
    @NonNull
    private Line addLineToEnd(int startAdapterIndex, int start, RecyclerView.Recycler recycler, boolean lastLine) {
        Trace.beginSection("FLM#addLineToEnd");
        int currentAdapterIndex = startAdapterIndex;
        int currentLineSize = 0;
        int currentMaxValue = 0;

        Line line = new Line();
        line.mStartValueOfTheHighestItem = start;

        while (line.mItemsCount != mMaxItemsInLine && currentAdapterIndex < contentItemCount()) {
            final View view = attach(recycler, currentAdapterIndex, -1);
            measureChildWithMargins(view, 0, 0);
            final int widthOrHeight = measureInLine(currentLineSize, view);
            if (widthOrHeight == Integer.MIN_VALUE) {
                detachAndScrapView(view, recycler);
                break;
            }
            final int heightOrWidth = mLayoutManagerHelper.getDecoratedMeasurement(view);
            currentMaxValue = measureLineForItem(currentMaxValue, line, heightOrWidth);
            currentLineSize += widthOrHeight + mSpacingBetweenItems;
            currentAdapterIndex++;
        }

        if (lastLine && (mEllipsisCount = mEllipsize ? (contentItemCount() - currentAdapterIndex) : -1) > 0) {
            Trace.beginSection("FLM#addLineToEnd:ellipsize");
            RecyclerView rv = findRV();
            View view = attach(recycler, contentItemCount(), -1);
            bindAndMeasureEllipsis(view, rv);
            int widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
            int heightOrWidth = mLayoutManagerHelper.getDecoratedMeasurement(view);
            currentMaxValue = measureLineForItem(currentMaxValue, line, heightOrWidth);

//            boolean removed = false;
            while (line.mItemsCount > mMaxItemsInLine ||
                (currentLineSize + widthOrHeight > mLayoutManagerHelper.getLineSize()) && line.mItemsCount > 0) {
                View victim = getChildAt(getChildCount() - 2); // pre-last child
                currentLineSize -= mLayoutManagerHelper.getDecoratedMeasurementInOther(victim) + mSpacingBetweenItems;
                line.mItemsCount--;
                mEllipsisCount++;
                detachAndScrapView(victim, recycler);

                bindAndMeasureEllipsis(view, rv);
                widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
//                removed = true;
            }
//            if (removed) // TODO re-calculate currentMaxValue
            currentLineSize += widthOrHeight + mSpacingBetweenItems;
            Trace.endSection();
        }

        layoutItemsToEnd(currentLineSize - mSpacingBetweenItems, line);
        Trace.endSection();
        return line;
    }

    private static int measureLineForItem(int currentMaxValue, Line line, int heightOrWidth) {
        if (heightOrWidth > currentMaxValue) {
            currentMaxValue = heightOrWidth;
            line.mEndValueOfTheHighestItem = line.mStartValueOfTheHighestItem + currentMaxValue;
        }
        line.mItemsCount++;
        return currentMaxValue;
    }

    private RecyclerView findRV() {
        View ch;
        return (ch = getChildAt(0)) != null ? (RecyclerView) ch.getParent() : null;
    }
    private void bindAndMeasureEllipsis(View view, RecyclerView rv) {
        rv.getAdapter().onBindViewHolder( // go crazy
            rv.findContainingViewHolder(view), contentItemCount(), ELLIPSIS_COUNT_CHANGED_PAYLOAD_LIST
        );
        measureChildWithMargins(view, 0, 0);
    }

    /**
     * Add one line to the start of recyclerView.
     *
     * @param startAdapterIndex Adapter index of first item of new line.
     * @param end               End position(Bottom - if orientation is VERTICAL or Right - if orientation is HORIZONTAL) of the new line.
     * @return New line.
     */
    @NonNull
    private Line addLineToStart(int startAdapterIndex, int end, RecyclerView.Recycler recycler) {
        Trace.beginSection("FLM#addLineToStart");
        int currentAdapterIndex = startAdapterIndex;
        int currentLineSize = 0;
        int currentMaxValue = 0;

        Line line = new Line();
        line.mEndValueOfTheHighestItem = end;

        while (line.mItemsCount != mMaxItemsInLine && currentAdapterIndex >= 0) {
            final View view = attach(recycler, currentAdapterIndex, 0);
            measureChildWithMargins(view, 0, 0);
            final int widthOrHeight = measureInLine(currentLineSize, view);
            if (widthOrHeight == Integer.MIN_VALUE) {
                detachAndScrapView(view, recycler);
                break;
            }
            final int heightOrWidth = mLayoutManagerHelper.getDecoratedMeasurement(view);
            if (heightOrWidth > currentMaxValue) {
                currentMaxValue = heightOrWidth;
                line.mStartValueOfTheHighestItem = line.mEndValueOfTheHighestItem - currentMaxValue;
            }
            line.mItemsCount++;
            currentLineSize += widthOrHeight + mSpacingBetweenItems;
            currentAdapterIndex--;
        }

        layoutItemsToStart(currentLineSize - mSpacingBetweenItems, line);
        Trace.endSection();
        return line;
    }

    private int measureInLine(int currentLineSize, View view) {
        final int widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
        if (currentLineSize != 0 &&
            currentLineSize + widthOrHeight > mLayoutManagerHelper.getLineSize()) {
            return Integer.MIN_VALUE;
        }
        return widthOrHeight;
    }

    private View attach(RecyclerView.Recycler recycler, int adapterPosition, int at) {
        final View view = recycler.getViewForPosition(adapterPosition);
        addView(view, at);
        return view;
    }

    private void layoutItemsToEnd(int itemsSize, Line line) {
        int currentStart = mLayoutManagerHelper.getStartPositionOfFirstItem(itemsSize);
        int max = line.biggest();
        int childCount = getChildCount();
        for (int i = line.mItemsCount; i > 0; i--) {
            currentStart += mSpacingBetweenItems + mLayoutManagerHelper
                .layoutItem(max, line.mStartValueOfTheHighestItem, currentStart, childCount - i);
        }
    }
    private void layoutItemsToStart(int itemsSize, Line line) {
        layoutItemsToStart(itemsSize, line, 0);
    }
    private void layoutItemsToStart(int itemsSize, Line line, int viewOffset) {
        int currentStart = mLayoutManagerHelper.getStartPositionOfFirstItem(itemsSize);
        int max = line.biggest();
        for (int i = 0; i < line.mItemsCount; i++) {
            currentStart += mSpacingBetweenItems + mLayoutManagerHelper
                .layoutItem(max, line.mStartValueOfTheHighestItem, currentStart, viewOffset + i);
        }
    }

    /**
     * Adds to start (and delete from end) of the recyclerView the required number of lines depending on the offset.
     *
     * @param offset   Original offset.
     * @return Real offset.
     */
    private int addLinesToStartAndDeleteFromEnd(int offset, RecyclerView.Recycler recycler) {
        Trace.beginSection("FLM#addLinesToStartAndDeleteFromEnd");
        Line line = mCurrentLines.get(0);

        int currentOffset = Math.max(line.mStartValueOfTheHighestItem, offset);
        int adapterViewIndex = getPosition(getChildAt(0)) - 1;

        int startValueOfNewLine = line.mStartValueOfTheHighestItem - mSpacingBetweenLines;

        boolean lookBack = mLookBack > 0;
        while (adapterViewIndex >= 0) {
            int externallyAdded = 0;
            if (currentOffset <= offset) {
                deleteLinesFromEnd(offset, recycler);
                break;
            } else {
                deleteLinesFromEnd(currentOffset, recycler);

                if (lookBack) {
                    externallyAdded = lookBack(adapterViewIndex, startValueOfNewLine, recycler);
                }
            }

            if (externallyAdded == 0) {
                mCurrentLines.add(0, line = addLineToStart(adapterViewIndex, startValueOfNewLine, recycler));
                adapterViewIndex -= line.mItemsCount;
            } else {
                for (int i = externallyAdded - 1; i >= 0; i--)
                    adapterViewIndex -= (line = mCurrentLines.get(i)).mItemsCount;
            }

            startValueOfNewLine = line.mStartValueOfTheHighestItem - mSpacingBetweenLines;
            currentOffset = line.mStartValueOfTheHighestItem;
        }
        Trace.endSection();
        return Math.max(currentOffset - mLayoutManagerHelper.getStartPadding(), offset);
    }
    /** @return number of added lines */
    private int lookBack(int adapterViewIndex, int end, RecyclerView.Recycler recycler) {
        Trace.beginSection("FLM#lookBack");

        // go backwards adding all the necessary views for the chunk
        Trace.beginSection("gather");
        int lookedBack = 0;
        while (adapterViewIndex - lookedBack >= 0 && lookedBack < mLookBack) {
            View v = recycler.getViewForPosition(adapterViewIndex - lookedBack);
            if (mLayoutManagerHelper.getSizeInLine(v) == ViewGroup.LayoutParams.MATCH_PARENT) {
                recycler.recycleView(v);
                break;
            } else {
                lookedBack++;
                addView(v, 0);
            }
        }
        Trace.endSection();

        if (lookedBack == 0) {
            Trace.endSection();
            return 0;
        }

        Trace.beginSection("measure");
        ArrayList<Integer> lineSizes = new ArrayList<>(); // fixme tmp
        int linesAdded = 0; { // now go forward and measure
            int currentLineSize = 0;
            int currentMaxValue = 0;
            Line line = new Line();
            line.mEndValueOfTheHighestItem = end;
            for (int i = 0; i < lookedBack; i++) {
                View view = getChildAt(i);
                measureChildWithMargins(view, 0, 0);
                int widthOrHeight = measureInLine(currentLineSize, view);
                if (widthOrHeight == Integer.MIN_VALUE) {
                    mCurrentLines.add(linesAdded++, line);
                    lineSizes.add(currentLineSize - mSpacingBetweenItems);
                    line = new Line();
                    line.mEndValueOfTheHighestItem = end;
                    currentLineSize = currentMaxValue = 0;
                    widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
                }
                final int heightOrWidth = mLayoutManagerHelper.getDecoratedMeasurement(view);
                if (heightOrWidth > currentMaxValue) {
                    currentMaxValue = heightOrWidth;
                    line.mStartValueOfTheHighestItem = line.mEndValueOfTheHighestItem - currentMaxValue;
                }
                line.mItemsCount++;
                currentLineSize += widthOrHeight + mSpacingBetweenItems;
            }
            mCurrentLines.add(linesAdded++, line);
            lineSizes.add(currentLineSize - mSpacingBetweenItems);
        }
        Trace.endSection();

        // go backwards and lay them out
        Trace.beginSection("layout"); {
            int offset = 0;
            for (int i = linesAdded - 1; i >= 0; i--) {
                Line line = mCurrentLines.get(i);
                line.offset(offset);
                layoutItemsToStart(lineSizes.get(i), line, lookedBack -= line.mItemsCount);
                offset -= line.biggest() + mSpacingBetweenLines;
            }
        }
        Trace.endSection();

        Trace.endSection();
        return linesAdded;
    }

    /**
     * Removes lines from the end. The number of deleted lines depends on the offset.
     *
     * @param offset   Current offset.
     */
    private void deleteLinesFromEnd(int offset, RecyclerView.Recycler recycler) {
        Line lineToDel = mCurrentLines.get(mCurrentLines.size() - 1);

        while (lineToDel != null) {
            if (lineToDel.mStartValueOfTheHighestItem - offset > mLayoutManagerHelper.getEnd()) {
                for (int i = 0; i < lineToDel.mItemsCount; i++) {
                    removeAndRecycleView(getChildAt(getChildCount() - 1), recycler);
                }
                mCurrentLines.remove(lineToDel);
                lineToDel = mCurrentLines.get(mCurrentLines.size() - 1);
            } else {
                lineToDel = null;
            }
        }
    }

    /**
     * Adds to end (and delete from start) of the recyclerView the required number of lines depending on the offset.
     *
     * @param offset   Original offset.
     * @return Real offset.
     */
    private int addLinesToEndAndDeleteFromStart(int offset, RecyclerView.Recycler recycler) {
        Trace.beginSection("FLM#addLinesToEndAndDeleteFromStart");
        Line line = mCurrentLines.get(mCurrentLines.size() - 1);

        int endPadding = mLayoutManagerHelper.getEndPadding();
        int currentOffset = Math.max(0, line.mEndValueOfTheHighestItem - mLayoutManagerHelper.getEnd() + endPadding);
        int adapterViewIndex = getPosition(getChildAt(getChildCount() - 1)) + 1;

        int startValueOfNewLine = line.mEndValueOfTheHighestItem + mSpacingBetweenLines;

        while (adapterViewIndex < contentItemCount() && mCurrentLines.size() < mMaxLines) {

            if ((currentOffset - endPadding) >= offset) {
                deleteLinesFromStart(offset, recycler);
                break;
            } else {
                deleteLinesFromStart(currentOffset - endPadding, recycler);
            }

            line = addLineToEnd(adapterViewIndex, startValueOfNewLine, recycler, mCurrentLines.size() + 1 == mMaxLines);
            mCurrentLines.add(line);

            startValueOfNewLine = line.mEndValueOfTheHighestItem + mSpacingBetweenLines;

            currentOffset = Math.max(0, line.mEndValueOfTheHighestItem - mLayoutManagerHelper.getEnd());
            adapterViewIndex += line.mItemsCount;
        }
        Trace.endSection();
        return Math.min(currentOffset, offset);
    }

    /**
     * Removes lines from the start. The number of deleted lines depends on the offset.
     *
     * @param offset   Current offset.
     */
    private void deleteLinesFromStart(int offset, RecyclerView.Recycler recycler) {
        Line lineToDel = mCurrentLines.get(0);

        while (lineToDel != null) {
            if (lineToDel.mEndValueOfTheHighestItem - offset < 0) {
                for (int i = 0; i < lineToDel.mItemsCount; i++) {
                    removeAndRecycleView(getChildAt(0), recycler);
                }
                mCurrentLines.remove(lineToDel);
                // mItemsInLines.add(lineToDel.mItemsCount);

                lineToDel = mCurrentLines.get(0);
            } else {
                lineToDel = null;
            }
        }
    }

    @Override public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        return mOrientation == VERTICAL ? scrollBy(dy, recycler) : 0;
    }

    @Override public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        return mOrientation == HORIZONTAL ? scrollBy(dx, recycler) : 0;
    }

    private int scrollBy(int delta, RecyclerView.Recycler recycler) {
        if (getChildCount() <= 0 || delta == 0) return 0;

        Trace.beginSection("FLM#scrollBy");
        int offset = delta > 0
            ? addLinesToEndAndDeleteFromStart(delta, recycler)
            : addLinesToStartAndDeleteFromEnd(delta, recycler);
        if (offset != 0) {
            for (int i = 0, size = mCurrentLines.size(); i < size; i++)
                mCurrentLines.get(i).offset(-offset);
            mLayoutManagerHelper.offsetChildren(-offset);
        }
        updateScrollPosition();
        Trace.endSection();
        return offset;
    }

    private void updateScrollPosition() {
        final View firstView = getChildAt(0);

        mFirstLineStartPosition = mLayoutManagerHelper.getDecoratedStart(firstView);
        mFirstItemAdapterIndex = getPosition(firstView);
    }

    @Override public boolean canScrollVertically() {
        return mOrientation == VERTICAL;
    }
    @Override public boolean canScrollHorizontally() {
        return mOrientation == HORIZONTAL;
    }

    @Override public void scrollToPosition(int position) {
        if (position >= 0 && position <= contentItemCount() - 1) {
            mFirstItemAdapterIndex = position;
            mFirstLineStartPosition = -1;
            requestLayout();
        }
    }

    @Override public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext());
        linearSmoothScroller.setTargetPosition(position);
        startSmoothScroll(linearSmoothScroller);
    }

    @Override public PointF computeScrollVectorForPosition(int targetPosition) {
        if (getChildCount() == 0) return null;

        final int firstChildPos = getPosition(getChildAt(0));
        final int direction = targetPosition < firstChildPos ? -1 : 1;

        return mOrientation == HORIZONTAL ? new PointF(direction, 0) : new PointF(0, direction);
    }

    private int contentItemCount() {
        return getItemCount() - (mEllipsize ? 1 : 0);
    }

    /**
     * Representation of line in RecyclerView.
     */
    private final static class Line {

        Line() {}

        int mStartValueOfTheHighestItem;
        int mEndValueOfTheHighestItem;

        int mItemsCount;

        int biggest() {
            return mEndValueOfTheHighestItem - mStartValueOfTheHighestItem;
        }

        void offset(int offset) {
            mStartValueOfTheHighestItem += offset;
            mEndValueOfTheHighestItem += offset;
        }
    }

    /**
     * Orientation and gravity helper.
     */
    private static abstract class LMHelper {

        RecyclerView.LayoutManager mLayoutManager;
        int mGravity;

        LMHelper(RecyclerView.LayoutManager layoutManager, int gravity) {
            mLayoutManager = layoutManager;
            mGravity = gravity;
        }

        final int getTotalSpace() {
            return getEnd() - getStartPadding() - getEndPadding();
        }

        abstract int getEnd();

        abstract int getEndPadding();

        abstract int getLineSize();

        abstract int getStartPadding();

        abstract int getDecoratedStart(View view);
        abstract int getDecoratedEnd(View view);

        abstract int getDecoratedMeasurement(View view);
        abstract int getDecoratedMeasurementInOther(View view);

        abstract int getSizeInLine(View view);

        abstract int getStartPositionOfFirstItem(int itemsSize);

        abstract int layoutItem(int maxItemHeightOrWidth, int startValueOfTheHighestItem, int currentStart, int viewAt);

        abstract void offsetChildren(int amount);

        abstract boolean isFinite();

        static LMHelper createLayoutManagerHelper(RecyclerView.LayoutManager layoutManager, int orientation, int gravity) {
            switch (orientation) {
                case VERTICAL: return new VHelper(layoutManager, gravity);
                case HORIZONTAL: return new HHelper(layoutManager, gravity);
                default: throw new IllegalArgumentException(ERROR_UNKNOWN_ORIENTATION);
            }
        }

        private static final class VHelper extends LMHelper {
            VHelper(RecyclerView.LayoutManager layoutManager, int gravity) {
                super(layoutManager, gravity);
            }

            @Override int getEnd() {
                return mLayoutManager.getHeight();
            }

            @Override int getEndPadding() {
                return mLayoutManager.getPaddingBottom();
            }

            @Override int getLineSize() {
                return mLayoutManager.getWidth() - mLayoutManager.getPaddingLeft() - mLayoutManager.getPaddingRight();
            }

            @Override int getStartPadding() {
                return mLayoutManager.getPaddingTop();
            }

            @Override int getDecoratedStart(View view) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return this.mLayoutManager.getDecoratedTop(view) - params.topMargin;
            }
            @Override int getDecoratedEnd(View view) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return this.mLayoutManager.getDecoratedBottom(view) + params.bottomMargin;
            }

            @Override int getDecoratedMeasurement(View view) {
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return mLayoutManager.getDecoratedMeasuredHeight(view) + params.topMargin + params.bottomMargin;
            }
            @Override int getDecoratedMeasurementInOther(View view) {
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return mLayoutManager.getDecoratedMeasuredWidth(view) + params.leftMargin + params.rightMargin;
            }

            @Override int getSizeInLine(View view) {
                return view.getLayoutParams().width;
            }

            @Override int getStartPositionOfFirstItem(int itemsSize) {
                int horizontalGravity = GravityCompat.getAbsoluteGravity(mGravity, mLayoutManager.getLayoutDirection());
                switch (horizontalGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        return (mLayoutManager.getWidth() - itemsSize) / 2;
                    case Gravity.RIGHT:
                        return mLayoutManager.getWidth() - mLayoutManager.getPaddingRight() - itemsSize;
                    default:
                        return mLayoutManager.getPaddingLeft();
                }
            }

            @Override int layoutItem(
                int maxItemHeightOrWidth, int startValueOfTheHighestItem, int currentStart, int viewAt) {
                View view = mLayoutManager.getChildAt(viewAt);
                final int width = getDecoratedMeasurementInOther(view);
                final int height = getDecoratedMeasurement(view);

                int currentStartValue =
                    startValueOfTheHighestItem + getPositionOfCurrentItem(maxItemHeightOrWidth, height);

                mLayoutManager.layoutDecoratedWithMargins(view, currentStart, currentStartValue,
                    currentStart + width, currentStartValue + height);

                return width;
            }
            private int getPositionOfCurrentItem(int itemMaxSize, int itemSize) {
                switch (mGravity & Gravity.VERTICAL_GRAVITY_MASK) {
                    case Gravity.CENTER_VERTICAL:
                        return (itemMaxSize - itemSize) / 2;
                    case Gravity.BOTTOM:
                        return itemMaxSize - itemSize;
                    default:
                        return  0;
                }
            }

            @Override void offsetChildren(int amount) {
                mLayoutManager.offsetChildrenVertical(amount);
            }

            @Override boolean isFinite() {
                return mLayoutManager.getHeight() != 0 || mLayoutManager.getHeightMode() != MeasureSpec.UNSPECIFIED;
            }
        }

        private static final class HHelper extends LMHelper {

            HHelper(RecyclerView.LayoutManager layoutManager, int gravity) {
                super(layoutManager, gravity);
            }

            @Override int getEnd() {
                return mLayoutManager.getWidth();
            }

            @Override int getEndPadding() {
                return mLayoutManager.getPaddingRight();
            }

            @Override int getLineSize() {
                return mLayoutManager.getHeight() - mLayoutManager.getPaddingTop() - mLayoutManager.getPaddingBottom();
            }

            @Override int getStartPadding() {
                return mLayoutManager.getPaddingLeft();
            }

            @Override int getDecoratedStart(View view) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return this.mLayoutManager.getDecoratedLeft(view) - params.leftMargin;
            }
            @Override int getDecoratedEnd(View view) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return this.mLayoutManager.getDecoratedRight(view) + params.rightMargin;
            }

            @Override int getDecoratedMeasurement(View view) {
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return mLayoutManager.getDecoratedMeasuredWidth(view) + params.leftMargin + params.rightMargin;
            }
            @Override int getDecoratedMeasurementInOther(View view) {
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return mLayoutManager.getDecoratedMeasuredHeight(view) + params.topMargin + params.bottomMargin;
            }

            @Override int getSizeInLine(View view) {
                return view.getLayoutParams().height;
            }

            @Override int getStartPositionOfFirstItem(int itemsSize) {
                int verticalGravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
                switch (verticalGravity) {
                    case Gravity.CENTER_VERTICAL:
                        return  (mLayoutManager.getHeight() - itemsSize) / 2;
                    case Gravity.BOTTOM:
                        return mLayoutManager.getHeight() - mLayoutManager.getPaddingBottom() - itemsSize;
                    default:
                        return mLayoutManager.getPaddingTop();
                }
            }

            @Override int layoutItem(
                int maxItemHeightOrWidth, int startValueOfTheHighestItem, int currentStart, int viewAt) {
                View view = mLayoutManager.getChildAt(viewAt);
                final int height = getDecoratedMeasurementInOther(view);
                final int width = getDecoratedMeasurement(view);

                int currentStartValue =
                    startValueOfTheHighestItem + getPositionOfCurrentItem(maxItemHeightOrWidth, width);

                mLayoutManager.layoutDecoratedWithMargins(view, currentStartValue, currentStart,
                    currentStartValue + width, currentStart + height);

                return height;
            }
            private int getPositionOfCurrentItem(int itemMaxSize, int itemSize) {
                int horizontalGravity = GravityCompat.getAbsoluteGravity(mGravity, mLayoutManager.getLayoutDirection());
                switch (horizontalGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        return (itemMaxSize - itemSize) / 2;
                    case Gravity.RIGHT:
                        return itemMaxSize - itemSize;
                    default:
                        return 0;
                }
            }

            @Override void offsetChildren(int amount) {
                mLayoutManager.offsetChildrenHorizontal(amount);
            }

            @Override boolean isFinite() {
                return mLayoutManager.getWidth() != 0 || mLayoutManager.getWidthMode() != MeasureSpec.UNSPECIFIED;
            }
        }
    }

    // LinearLayoutManager copy-paste, important both for scrollbars and nested scroll

    /**
     * Works the same way as {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}.
     * see {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}
     */
    private boolean mSmoothScrollbarEnabled = true;

    /**
     * When smooth scrollbar is enabled, the position and size of the scrollbar thumb is computed
     * based on the number of visible pixels in the visible items. This however assumes that all
     * list items have similar or equal widths or heights (depending on list orientation).
     * If you use a list in which items have different dimensions, the scrollbar will change
     * appearance as the user scrolls through the list. To avoid this issue,  you need to disable
     * this property.
     *
     * When smooth scrollbar is disabled, the position and size of the scrollbar thumb is based
     * solely on the number of items in the adapter and the position of the visible items inside
     * the adapter. This provides a stable scrollbar as the user navigates through a list of items
     * with varying widths / heights.
     *
     * @param enabled Whether to enable smooth scrollbar.
     * @see #isSmoothScrollbarEnabled()
     */
    public void setSmoothScrollbarEnabled(boolean enabled) { // mimic LLM API
        mSmoothScrollbarEnabled = enabled;
    }
    public FlowLayoutManager smoothScrollbar(boolean enabled) { // compact builder-style
        mSmoothScrollbarEnabled = enabled;
        return this;
    }

    /**
     * Returns the current state of the smooth scrollbar feature. It is enabled by default.
     *
     * @return True if smooth scrollbar is enabled, false otherwise.
     * @see #setSmoothScrollbarEnabled(boolean)
     */
    public boolean isSmoothScrollbarEnabled() {
        return mSmoothScrollbarEnabled;
    }

    @Override public int computeHorizontalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset(state);
    }
    @Override public int computeVerticalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset(state);
    }
    @Override public int computeHorizontalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent(state);
    }
    @Override public int computeVerticalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent(state);
    }
    @Override public int computeHorizontalScrollRange(RecyclerView.State state) {
        return computeScrollRange(state);
    }
    @Override public int computeVerticalScrollRange(RecyclerView.State state) {
        return computeScrollRange(state);
    }

    private int computeScrollOffset(RecyclerView.State state) {
        if (getChildCount() == 0 || state.getItemCount() == 0) return 0;
        return computeScrollOffset(state,
            findFirstVisibleChildClosestToStart(!mSmoothScrollbarEnabled),
            findFirstVisibleChildClosestToEnd(!mSmoothScrollbarEnabled),
            mSmoothScrollbarEnabled);
    }

    private int computeScrollExtent(RecyclerView.State state) {
        if (getChildCount() == 0 || state.getItemCount() == 0) return 0;
        return computeScrollExtent(state,
            findFirstVisibleChildClosestToStart(!mSmoothScrollbarEnabled),
            findFirstVisibleChildClosestToEnd(!mSmoothScrollbarEnabled),
            mSmoothScrollbarEnabled);
    }

    private int computeScrollRange(RecyclerView.State state) {
        if (getChildCount() == 0 || state.getItemCount() == 0) return 0;
        return computeScrollRange(state,
            findFirstVisibleChildClosestToStart(!mSmoothScrollbarEnabled),
            findFirstVisibleChildClosestToEnd(!mSmoothScrollbarEnabled),
            mSmoothScrollbarEnabled);
    }

    /**
     * Convenience method to find the visible child closes to start. Caller should check if it has
     * enough children.
     *
     * @param completelyVisible Whether child should be completely visible or not
     * @return The first visible child closest to start of the layout from user's perspective.
     */
    View findFirstVisibleChildClosestToStart(boolean completelyVisible) {
        return findOneVisibleChild(0, getChildCount(), completelyVisible);
    }

    /**
     * Convenience method to find the visible child closes to end. Caller should check if it has
     * enough children.
     *
     * @param completelyVisible Whether child should be completely visible or not
     * @return The first visible child closest to end of the layout from user's perspective.
     */
    View findFirstVisibleChildClosestToEnd(boolean completelyVisible) {
        return findOneVisibleChild(getChildCount() - 1, -1, completelyVisible);
    }

    // Returns the first child that is visible in the provided index range, i.e. either partially or
    // fully visible depending on the arguments provided. Completely invisible children are not
    // acceptable by this method, but could be returned
    // using #findOnePartiallyOrCompletelyInvisibleChild
    View findOneVisibleChild(int fromIndex, int toIndex, boolean completelyVisible) {
        int preferredBoundsFlag = completelyVisible ? (1 | 2 | 16384 | 8192) : (64 | 256);
        return findOneViewWithinBoundFlags(fromIndex, toIndex, preferredBoundsFlag);
    }

    // ScrollBarHelper copy-paste

    /**
     * @param startChild View closest to start of the list. (top or left)
     * @param endChild   View closest to end of the list (bottom or right)
     */
    int computeScrollOffset(RecyclerView.State state, View startChild, View endChild, boolean smoothScrollbarEnabled) {
        if (startChild == null || endChild == null) return 0;
        final int minPosition = Math.min(getPosition(startChild), getPosition(endChild));
        final int itemsBefore = Math.max(0, minPosition);
        if (!smoothScrollbarEnabled) return itemsBefore;

        LMHelper orientation = mLayoutManagerHelper;
        final int laidOutArea =
            Math.abs(orientation.getDecoratedEnd(endChild) - orientation.getDecoratedStart(startChild));
        final int itemRange =
            Math.abs(getPosition(startChild) - getPosition(endChild)) + 1;
        final float avgSizePerRow = (float) laidOutArea / itemRange;

        return Math.round(
            itemsBefore * avgSizePerRow + (orientation.getStartPadding() - orientation.getDecoratedStart(startChild))
        );
    }

    /**
     * @param startChild View closest to start of the list. (top or left)
     * @param endChild   View closest to end of the list (bottom or right)
     */
    int computeScrollExtent(RecyclerView.State state, View startChild, View endChild, boolean smoothScrollbarEnabled) {
        if (startChild == null || endChild == null) return 0;
        if (!smoothScrollbarEnabled) return Math.abs(getPosition(startChild) - getPosition(endChild)) + 1;
        LMHelper orientation = mLayoutManagerHelper;
        final int extend = orientation.getDecoratedEnd(endChild) - orientation.getDecoratedStart(startChild);
        return Math.min(orientation.getTotalSpace(), extend);
    }

    /**
     * @param startChild View closest to start of the list. (top or left)
     * @param endChild   View closest to end of the list (bottom or right)
     */
    int computeScrollRange(RecyclerView.State state, View startChild, View endChild, boolean smoothScrollbarEnabled) {
        if (startChild == null || endChild == null) return 0;
        if (!smoothScrollbarEnabled) return state.getItemCount();
        // smooth scrollbar enabled. try to estimate better.
        LMHelper orientation = mLayoutManagerHelper;
        final int laidOutArea = orientation.getDecoratedEnd(endChild) - orientation.getDecoratedStart(startChild);
        final int laidOutRange = Math.abs(getPosition(startChild) - getPosition(endChild)) + 1;
        // estimate a size for full list.
        return (int) ((float) laidOutArea / laidOutRange * state.getItemCount());
    }
    
    // ViewBoundsCheck copy-paste

    private final BoundFlags mBoundFlags = new BoundFlags();

    /**
     * Returns the first view starting from fromIndex to toIndex in views whose bounds lie within
     * its parent bounds based on the provided preferredBoundFlags. If no match is found based on
     * the preferred flags, and a nonzero acceptableBoundFlags is specified, the last view whose
     * bounds lie within its parent view based on the acceptableBoundFlags is returned. If no such
     * view is found based on either of these two flags, null is returned.
     *
     * @param fromIndex           The view position index to start the search from.
     * @param toIndex             The view position index to end the search at.
     * @param preferredBoundFlags The flags indicating the preferred match. Once a match is found
     *                            based on this flag, that view is returned instantly.
     * @return The first view that satisfies acceptableBoundFlags or the last view satisfying
     * acceptableBoundFlags boundary conditions.
     */
    View findOneViewWithinBoundFlags(int fromIndex, int toIndex, int preferredBoundFlags) {
        LMHelper orientation = mLayoutManagerHelper;
        final int start = orientation.getStartPadding();
        final int end = orientation.getEnd() - orientation.getEndPadding();
        final int next = toIndex > fromIndex ? 1 : -1;
        View acceptableMatch = null;
        for (int i = fromIndex; i != toIndex; i += next) {
            final View child = getChildAt(i);
            final int childStart = orientation.getDecoratedStart(child);
            final int childEnd = orientation.getDecoratedEnd(child);
            mBoundFlags.setBounds(start, end, childStart, childEnd);
            if (preferredBoundFlags != 0) {
                mBoundFlags.resetFlags();
                mBoundFlags.addFlags(preferredBoundFlags);
                if (mBoundFlags.boundsMatch()) {
                    // found a perfect match
                    return child;
                }
            }
            mBoundFlags.resetFlags();
            mBoundFlags.addFlags(320);
            if (mBoundFlags.boundsMatch()) {
                acceptableMatch = child;
            }
        }
        return acceptableMatch;
    }

    private static final class BoundFlags {
        BoundFlags() {}
        int mBoundFlags = 0;
        int mRvStart, mRvEnd, mChildStart, mChildEnd;

        void setBounds(int rvStart, int rvEnd, int childStart, int childEnd) {
            mRvStart = rvStart;
            mRvEnd = rvEnd;
            mChildStart = childStart;
            mChildEnd = childEnd;
        }

        void addFlags(int flags) {
            mBoundFlags |= flags;
        }

        void resetFlags() {
            mBoundFlags = 0;
        }

        private static int compare(int x, int y) {
            if (x > y) return 1;
            if (x == y) return 2;
            return 4;
        }

        boolean boundsMatch() {
            return match(0, mChildStart, mRvStart) &&
                match(4, mChildStart, mRvEnd) &&
                match(8, mChildEnd, mRvStart) &&
                match(12, mChildEnd, mRvEnd);
        }

        private boolean match(int i, int mChildStart, int mRvStart) {
            return (mBoundFlags & (7 << i)) == 0 || (mBoundFlags & (compare(mChildStart, mRvStart) << i)) != 0;
        }
    }

}