package ru.astrocode.flm;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Astrocode on 26.05.18.
 */
public class FlowLayoutManager extends RecyclerView.LayoutManager implements RecyclerView.SmoothScroller.ScrollVectorProvider {

    // TODO support Gravity.FILL_*, distribute size or space depending on layout params

    public static final Object ELLIPSIS_COUNT_CHANGED_PAYLOAD = new Object();
    private static final List<Object> ELLIPSIS_COUNT_CHANGED_PAYLOAD_LIST = Collections.singletonList(ELLIPSIS_COUNT_CHANGED_PAYLOAD);

    public final static int VERTICAL = OrientationHelper.VERTICAL, HORIZONTAL = OrientationHelper.HORIZONTAL;

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

    private int mSpacingBetweenItems;
    private int mSpacingBetweenLines;

    private LMHelper mLayoutManagerHelper;

    private final ArrayList<Line> mCurrentLines;

    private int mFirstItemAdapterIndex;
    private int mFirstLineStartPosition;

    private static final int[] ATTRS = {
        android.R.attr.orientation, android.R.attr.gravity,
        android.R.attr.maxItemsPerRow, android.R.attr.spacing, android.R.attr.lineSpacingExtra,
        android.R.attr.maxLines, android.R.attr.ellipsize,
    };
    public FlowLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes));
    }
    private FlowLayoutManager(TypedArray ta) {
        this(
            ta.getInt(0, VERTICAL), ta.getInt(1, Gravity.START),
            ta.getInt(2, Integer.MAX_VALUE), ta.getDimensionPixelOffset(3, 0), ta.getDimensionPixelOffset(4, 0));
        maxLines(ta.getInt(5, Integer.MAX_VALUE), ta.getInt(6, 0) == 3 /*ellipsize="end"*/, false);
        ta.recycle();
    }

    public FlowLayoutManager(int orientation) {
        this(orientation, Gravity.START, Integer.MAX_VALUE, 0, 0);
    }

    public FlowLayoutManager(int orientation, int gravity) {
        this(orientation, gravity, Integer.MAX_VALUE, 0, 0);
    }

    private FlowLayoutManager(
        int orientation, int gravity,
        int maxItemsInLine, @Px int spacingBetweenItems, @Px int spacingBetweenLines) {
        mCurrentLines = new ArrayList<>();

        mGravity = gravity;

        mFirstItemAdapterIndex = 0;
        mFirstLineStartPosition = -1;

        if (maxItemsInLine <= 0) throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        mMaxItemsInLine = maxItemsInLine;

        if (mSpacingBetweenItems < 0) {
            throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        }

        mSpacingBetweenItems = spacingBetweenItems;

        if (mSpacingBetweenLines < 0) {
            throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        }

        mSpacingBetweenLines = spacingBetweenLines;

        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException(ERROR_UNKNOWN_ORIENTATION);
        }
        mOrientation = orientation;

        mLayoutManagerHelper = LMHelper.createLayoutManagerHelper(this, orientation, mGravity);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public boolean isAutoMeasureEnabled() {
        return true;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        Line currentLine = null;

        if (mFirstLineStartPosition == -1) {
            mFirstLineStartPosition = mLayoutManagerHelper.getStartAfterPadding();
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
                currentLine.mEndValueOfTheHighestItem > mLayoutManagerHelper.getEndAfterPadding()) {
                break;
            }
        }

        if (mFirstItemAdapterIndex > 0 && currentLine != null) {
            int availableOffset = currentLine.mEndValueOfTheHighestItem - mLayoutManagerHelper.getEnd() + mLayoutManagerHelper.getEndPadding();

            if (availableOffset < 0) {
                if (mOrientation == VERTICAL) {
                    scrollVerticallyBy(availableOffset, recycler, state);
                } else {
                    scrollHorizontallyBy(availableOffset, recycler, state);
                }
            }
        }

    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        Bundle data = (Bundle) state;
        mFirstItemAdapterIndex = data.getInt(TAG_FIRST_ITEM_ADAPTER_INDEX);
        mFirstLineStartPosition = data.getInt(TAG_FIRST_LINE_START_POSITION);
    }

    @Override
    public Parcelable onSaveInstanceState() {
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

    public FlowLayoutManager orientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException(ERROR_UNKNOWN_ORIENTATION);
        }

        if (orientation != mOrientation) {
            assertNotInLayoutOrScroll(null);

            mOrientation = orientation;
            mLayoutManagerHelper = LMHelper.createLayoutManagerHelper(this, orientation, mGravity);

            requestLayout();
        }

        return this;
    }
    /**
     * Return current orientation of the layout manager.
     */
    public int orientation() {
        return mOrientation;
    }

    public FlowLayoutManager maxItemsInLine(int maxItemsInLine) {
        if (maxItemsInLine <= 0) {
            throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        }
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
     * Returns number of hidden views, or -1 if the value is not known yet or ellipsize is disabled
     * @return number of hidden rows
     */
    public int ellipsisCount() {
        return mEllipsisCount;
    }

    public FlowLayoutManager spacingBetweenItems(int spacingBetweenItems) {
        if (spacingBetweenItems < 0) {
            throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        }

        assertNotInLayoutOrScroll(null);
        mSpacingBetweenItems = spacingBetweenItems;
        requestLayout();
        return this;
    }
    public int spacingBetweenItems() {
        return mSpacingBetweenItems;
    }

    public FlowLayoutManager spacingBetweenLines(int spacingBetweenLines) {
        if (spacingBetweenLines < 0) {
            throw new IllegalArgumentException(ERROR_BAD_ARGUMENT);
        }

        assertNotInLayoutOrScroll(null);
        mSpacingBetweenLines = spacingBetweenLines;
        requestLayout();
        return this;
    }
    public int spacingBetweenLines() {
        return mSpacingBetweenLines;
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
        boolean isEndOfLine = false;

        int currentAdapterIndex = startAdapterIndex;
        int currentItemsSize = 0;
        int currentMaxValue = 0;

        Line line = new Line();
        line.mStartValueOfTheHighestItem = start;

        while (!isEndOfLine && currentAdapterIndex < contentItemCount()) {
            final View view = attach(recycler, currentAdapterIndex, -1);
            measureChildWithMargins(view, 0, 0);
            final int widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
            final int heightOrWidth = mLayoutManagerHelper.getDecoratedMeasurement(view);

            if ((currentItemsSize + widthOrHeight) > mLayoutManagerHelper.getLineSize()) {
                detachAndScrapView(view, recycler);
                break;
            }
            if (line.mItemsCount == mMaxItemsInLine) {
                isEndOfLine = true;
            }
            if (heightOrWidth > currentMaxValue) {
                currentMaxValue = heightOrWidth;
                line.mEndValueOfTheHighestItem = line.mStartValueOfTheHighestItem + currentMaxValue;
            }
            line.mItemsCount++;
            currentItemsSize += widthOrHeight + mSpacingBetweenItems;
            currentAdapterIndex++;
        }

        if (lastLine && (mEllipsisCount = mEllipsize ? (contentItemCount() - currentAdapterIndex) : -1) > 0) {
            RecyclerView rv = findRV();
            View view = attach(recycler, contentItemCount(), -1);
            bindAndMeasureEllipsis(view, rv);
            int widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
            int heightOrWidth = mLayoutManagerHelper.getDecoratedMeasurement(view);
            if (heightOrWidth > currentMaxValue) {
                currentMaxValue = heightOrWidth;
                line.mEndValueOfTheHighestItem = line.mStartValueOfTheHighestItem + currentMaxValue;
            }
            line.mItemsCount++;

//            boolean removed = false;
            while (line.mItemsCount > mMaxItemsInLine ||
                (currentItemsSize + widthOrHeight > mLayoutManagerHelper.getLineSize()) && line.mItemsCount > 0) {
                View victim = getChildAt(getChildCount() - 2); // pre-last child
                currentItemsSize -= mLayoutManagerHelper.getDecoratedMeasurementInOther(victim) + mSpacingBetweenItems;
                line.mItemsCount--;
                mEllipsisCount++;
                detachAndScrapView(victim, recycler);

                bindAndMeasureEllipsis(view, rv);
                widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
//                removed = true;
            }
//            if (removed) // TODO re-calculate currentMaxValue
            currentItemsSize += widthOrHeight + mSpacingBetweenItems;
        }

        layoutItemsToEnd(currentItemsSize - mSpacingBetweenItems, currentMaxValue, line);

        return line;
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

    private int layoutItem(int maxItemHeightOrWidth, int startValueOfTheHighestItem, int currentStart, View view) {
        final int widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
        final int heightOrWidth = mLayoutManagerHelper.getDecoratedMeasurement(view);

        int currentStartValue =
            startValueOfTheHighestItem + mLayoutManagerHelper.getPositionOfCurrentItem(maxItemHeightOrWidth, heightOrWidth);

        if (mOrientation == VERTICAL) {
            layoutDecoratedWithMargins(view, currentStart, currentStartValue,
                    currentStart + widthOrHeight, currentStartValue + heightOrWidth);
        } else {
            layoutDecoratedWithMargins(view, currentStartValue, currentStart,
                    currentStartValue + heightOrWidth, currentStart + widthOrHeight);
        }

        return currentStart + widthOrHeight + mSpacingBetweenItems;
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
        boolean isEndOfLine = false;

        int currentAdapterIndex = startAdapterIndex;
        int currentItemsSize = 0;
        int currentMaxValue = 0;

        Line line = new Line();
        line.mEndValueOfTheHighestItem = end;

        while (!isEndOfLine && currentAdapterIndex >= 0) {
            final View view = attach(recycler, currentAdapterIndex, 0);
            measureChildWithMargins(view, 0, 0);
            final int widthOrHeight = mLayoutManagerHelper.getDecoratedMeasurementInOther(view);
            final int heightOrWidth = mLayoutManagerHelper.getDecoratedMeasurement(view);

            if (line.mItemsCount == mMaxItemsInLine || (currentItemsSize + widthOrHeight) >= mLayoutManagerHelper.getLineSize()) {
                isEndOfLine = true;

                if (currentItemsSize == 0) {
                    currentMaxValue = heightOrWidth;

                    line.mStartValueOfTheHighestItem = line.mEndValueOfTheHighestItem - currentMaxValue;
                    line.mItemsCount++;
                } else {
                    detachAndScrapView(view, recycler);
                    continue;
                }
            } else {
                if (heightOrWidth > currentMaxValue) {
                    currentMaxValue = heightOrWidth;
                    line.mStartValueOfTheHighestItem = line.mEndValueOfTheHighestItem - currentMaxValue;
                }
                line.mItemsCount++;
            }

            currentItemsSize += widthOrHeight + mSpacingBetweenItems;

            currentAdapterIndex--;
        }

        layoutItemsToStart(currentItemsSize - mSpacingBetweenItems, currentMaxValue, line);

        return line;
    }

    private View attach(RecyclerView.Recycler recycler, int adapterPosition, int layoutPosition) {
        final View view = recycler.getViewForPosition(adapterPosition);
        addView(view, layoutPosition);
        return view;
    }

    private void layoutItemsToEnd(int itemsSize, int maxItemHeightOrWidth, Line line) {
        int currentStart = mLayoutManagerHelper.getStartPositionOfFirstItem(itemsSize);
        int childCount = getChildCount();
        for (int i = line.mItemsCount; i > 0; i--) {
            currentStart = layoutItem(maxItemHeightOrWidth, line.mStartValueOfTheHighestItem, currentStart, getChildAt(childCount - i));
        }
    }
    private void layoutItemsToStart(int itemsSize, int maxItemHeightOrWidth, Line line) {
        int currentStart = mLayoutManagerHelper.getStartPositionOfFirstItem(itemsSize);
        for (int i = 0; i < line.mItemsCount; i++) {
            currentStart = layoutItem(maxItemHeightOrWidth, line.mStartValueOfTheHighestItem, currentStart, getChildAt(i));
        }
    }

    /**
     * Adds to start (and delete from end) of the recyclerView the required number of lines depending on the offset.
     *
     * @param offset   Original offset.
     * @param recycler
     * @return Real offset.
     */
    private int addLinesToStartAndDeleteFromEnd(int offset, RecyclerView.Recycler recycler) {
        Line line = mCurrentLines.get(0);

        final int availableOffset = line.mStartValueOfTheHighestItem - mLayoutManagerHelper.getStartAfterPadding();

        int currentOffset = Math.max(availableOffset, offset);
        int adapterViewIndex = getPosition(getChildAt(0)) - 1;

        int startValueOfNewLine = line.mStartValueOfTheHighestItem - mSpacingBetweenLines;

        while (adapterViewIndex >= 0) {

            if (currentOffset <= offset) {
                deleteLinesFromEnd(offset, recycler);
                break;
            } else {
                deleteLinesFromEnd(currentOffset, recycler);
            }

            line = addLineToStart(adapterViewIndex, startValueOfNewLine, recycler);
            mCurrentLines.add(0, line);

            startValueOfNewLine = line.mStartValueOfTheHighestItem - mSpacingBetweenLines;

            currentOffset = line.mStartValueOfTheHighestItem;
            adapterViewIndex -= line.mItemsCount;
        }

        return Math.max(currentOffset, offset);
    }

    /**
     * Removes lines from the end. The number of deleted lines depends on the offset.
     *
     * @param offset   Current offset.
     * @param recycler
     */
    private void deleteLinesFromEnd(int offset, RecyclerView.Recycler recycler) {
        Line lineToDel = mCurrentLines.get(mCurrentLines.size() - 1);

        while (lineToDel != null) {
            if (lineToDel.mStartValueOfTheHighestItem - offset >
                    mLayoutManagerHelper.getEndAfterPadding()) {
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
     * @param recycler
     * @return Real offset.
     */
    private int addLinesToEndAndDeleteFromStart(int offset, RecyclerView.Recycler recycler) {
        Line line = mCurrentLines.get(mCurrentLines.size() - 1);

        int availableOffset = Math.max(0,
            line.mEndValueOfTheHighestItem - mLayoutManagerHelper.getEnd() + mLayoutManagerHelper.getEndPadding());

        int currentOffset = Math.min(availableOffset, offset);
        int adapterViewIndex = getPosition(getChildAt(getChildCount() - 1)) + 1;

        int startValueOfNewLine = line.mEndValueOfTheHighestItem + mSpacingBetweenLines;

        while (adapterViewIndex < contentItemCount() && mCurrentLines.size() < mMaxLines) {

            if (currentOffset >= offset) {
                deleteLinesFromStart(offset, recycler);
                break;
            } else {
                deleteLinesFromStart(currentOffset, recycler);
            }

            line = addLineToEnd(adapterViewIndex, startValueOfNewLine, recycler, mCurrentLines.size() + 1 == mMaxLines);
            mCurrentLines.add(line);

            startValueOfNewLine = line.mEndValueOfTheHighestItem + mSpacingBetweenLines;

            currentOffset = line.mEndValueOfTheHighestItem - mLayoutManagerHelper.getEnd();
            adapterViewIndex += line.mItemsCount;
        }


        return Math.min(currentOffset, offset);
    }

    /**
     * Removes lines from the start. The number of deleted lines depends on the offset.
     *
     * @param offset   Current offset.
     * @param recycler
     */
    private void deleteLinesFromStart(int offset, RecyclerView.Recycler recycler) {
        Line lineToDel = mCurrentLines.get(0);

        while (lineToDel != null) {
            if (lineToDel.mEndValueOfTheHighestItem - offset <
                    mLayoutManagerHelper.getStartAfterPadding()) {
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

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        return mOrientation == VERTICAL ? scrollBy(dy, recycler) : 0;
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        return mOrientation == HORIZONTAL ? scrollBy(dx, recycler) : 0;
    }

    private int scrollBy(int delta, RecyclerView.Recycler recycler) {
        if (getChildCount() <= 0 || delta == 0) {
            return 0;
        }

        int offset = delta > 0
            ? addLinesToEndAndDeleteFromStart(delta, recycler)
            : addLinesToStartAndDeleteFromEnd(delta, recycler);
        if (offset != 0) {
            for (int i = 0; i < mCurrentLines.size(); i++) {
                mCurrentLines.get(i).offset(-offset);
            }
            mLayoutManagerHelper.offsetChildren(-offset);
        }
        updateScrollPosition();
        return offset;
    }

    private void updateScrollPosition() {
        final View firstView = getChildAt(0);

        mFirstLineStartPosition = mLayoutManagerHelper.getDecoratedStart(firstView);
        mFirstItemAdapterIndex = getPosition(firstView);
    }

    @Override
    public boolean canScrollVertically() {
        return mOrientation == VERTICAL;
    }

    @Override
    public boolean canScrollHorizontally() {
        return mOrientation == HORIZONTAL;
    }

    @Override
    public void scrollToPosition(int position) {
        if (position >= 0 && position <= contentItemCount() - 1) {
            mFirstItemAdapterIndex = position;
            mFirstLineStartPosition = -1;
            requestLayout();
        }
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext());
        linearSmoothScroller.setTargetPosition(position);
        startSmoothScroll(linearSmoothScroller);
    }

    @Override
    public PointF computeScrollVectorForPosition(int targetPosition) {
        if (getChildCount() == 0) {
            return null;
        }

        final int firstChildPos = getPosition(getChildAt(0));
        final int direction = targetPosition < firstChildPos ? -1 : 1;

        if (mOrientation == HORIZONTAL) {
            return new PointF(direction, 0);
        } else {
            return new PointF(0, direction);
        }
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

        abstract int getEnd();

        abstract int getEndPadding();

        abstract int getLineSize();

        abstract int getEndAfterPadding();

        abstract int getStartAfterPadding();

        abstract int getDecoratedStart(View view);

        abstract int getDecoratedMeasurement(View view);

        abstract int getDecoratedMeasurementInOther(View view);

        abstract int getStartPositionOfFirstItem(int itemsSize);

        abstract int getPositionOfCurrentItem(int itemMaxSize, int itemSize);

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

            @Override int getEndAfterPadding() {
                return mLayoutManager.getHeight() - mLayoutManager.getPaddingBottom();
            }

            @Override int getStartAfterPadding() {
                return mLayoutManager.getPaddingTop();
            }

            @Override int getDecoratedStart(View view) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return this.mLayoutManager.getDecoratedTop(view) - params.topMargin;
            }

            @Override int getDecoratedMeasurement(View view) {
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return mLayoutManager.getDecoratedMeasuredHeight(view) + params.topMargin + params.bottomMargin;
            }

            @Override int getDecoratedMeasurementInOther(View view) {
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return mLayoutManager.getDecoratedMeasuredWidth(view) + params.leftMargin + params.rightMargin;
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

            @Override int getPositionOfCurrentItem(int itemMaxSize, int itemSize) {
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

            @Override int getEndAfterPadding() {
                return mLayoutManager.getWidth() - mLayoutManager.getPaddingRight();
            }

            @Override int getStartAfterPadding() {
                return mLayoutManager.getPaddingLeft();
            }

            @Override int getDecoratedStart(View view) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
                return this.mLayoutManager.getDecoratedLeft(view) - params.leftMargin;
            }

            @Override int getDecoratedMeasurement(View view) {
                return mLayoutManager.getDecoratedMeasuredWidth(view);
            }

            @Override int getDecoratedMeasurementInOther(View view) {
                return mLayoutManager.getDecoratedMeasuredHeight(view);
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

            @Override int getPositionOfCurrentItem(int itemMaxSize, int itemSize) {
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
}