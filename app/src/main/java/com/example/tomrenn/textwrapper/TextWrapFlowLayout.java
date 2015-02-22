package com.example.tomrenn.textwrapper;

// http://stackoverflow.com/a/4937713/804479

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A ViewGroup that acts like a FlowLayout - but also facilitates text wrapping.
 *
 * ------------ ||||||
 * -----------  |view|
 * ------------ ||||||
 * ~~~~~~~~~~~~~~~~~
 * ~~~~~~~~~~~~~~~~~~~
 * ~~~~~~~~~~~~~~~
 *
 *  -- : main textview
 *  ~~ : wrapped textview
 *
 */
public class TextWrapFlowLayout extends ViewGroup {
    private static final String TAG = "TextWrapFlowLayout";
    public static final int DEFAULT_HORIZONTAL_SPACING = 5;
    public static final int DEFAULT_VERTICAL_SPACING = 5;
    private final int horizontalSpacing;
    private final int verticalSpacing;
    private List<RowMeasurement> currentRows = Collections.emptyList();


    public TextWrapFlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.TextWrapFlowLayout);
        horizontalSpacing = styledAttributes.getDimensionPixelSize(R.styleable.TextWrapFlowLayout_horizontalSpacing,
                DEFAULT_HORIZONTAL_SPACING);
        verticalSpacing = styledAttributes.getDimensionPixelSize(R.styleable.TextWrapFlowLayout_verticalSpacing,
                DEFAULT_VERTICAL_SPACING);
        styledAttributes.recycle();
    }

    // see if WrappedTextView is first or second view, measure the sibiling first,
    // then continue as normal
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int maxInternalWidth = MeasureSpec.getSize(widthMeasureSpec) - getHorizontalPadding();
        final int maxInternalHeight = MeasureSpec.getSize(heightMeasureSpec) - getVerticalPadding();
        List<RowMeasurement> rows = new ArrayList<>();
        RowMeasurement currentRow = new RowMeasurement(maxInternalWidth, widthMode);
        rows.add(currentRow);
        final List<View> children = getLayoutChildren();


        View wrappedView;
        TextView wrappedTextView;
        CharSequence overflowText;

        if (children.get(0) instanceof TextView){
            wrappedTextView = (TextView) children.get(0);
            wrappedView = children.get(1);
        } else if (children.get(1) instanceof TextView){
            wrappedTextView = (TextView) children.get(1);
            wrappedView = children.get(0);
        } else {
            // fixme: gracefully default to FlowLayout behavior instead of whining
            throw new IllegalStateException("The first or second view must be of type TextView");
        }

        // values for child view calculations
        int maxWidth = maxInternalWidth;
        int maxHeight = maxInternalHeight;

        // measure the wrapped view
        LayoutParams wrappedViewParams = (LayoutParams) wrappedView.getLayoutParams();
        int childWidthSpec = createChildMeasureSpec(wrappedViewParams.width, maxWidth, widthMode);
        int childHeightSpec = createChildMeasureSpec(wrappedViewParams.height, maxHeight, heightMode);
        wrappedView.measure(childWidthSpec, childHeightSpec);
        int childWidth = wrappedView.getMeasuredWidth();
        int childHeight = wrappedView.getMeasuredHeight();
        currentRow.addChildDimensions(childWidth, childHeight);

        // wrappedTextView now fills the width gap, and match the height of the wrapped view.
        LayoutParams wrappedTextParams = (LayoutParams) wrappedTextView.getLayoutParams();
        // save the full original text inside the layout params
        if (wrappedTextParams.fullText == null) {
            wrappedTextParams.fullText = wrappedTextView.getText();
        }
        int availableWidth = maxInternalWidth - wrappedView.getMeasuredWidth() - horizontalSpacing;
        childWidthSpec = createChildMeasureSpec(wrappedTextParams.width, availableWidth, widthMode);
        childHeightSpec = createChildMeasureSpec(
                wrappedTextParams.height, wrappedView.getMeasuredHeight(), heightMode);
        wrappedTextView.measure(childWidthSpec, childHeightSpec);
        currentRow.addChildDimensions(wrappedTextView.getMeasuredWidth(),
                wrappedTextView.getMeasuredHeight());


        overflowText = TextWrapHelper.preformWrapping(wrappedTextView, wrappedView.getMeasuredHeight(),
                wrappedTextParams.fullText);


        // set the wrapped text if overflow view defined
        if (wrappedTextParams.overflowTextViewId != -1) {
            TextView overflowTextView = (TextView) findViewById(wrappedTextParams.overflowTextViewId);
            overflowTextView.setText(overflowText);
        }

        for (int i=2; i<children.size(); i++) {
            final View child = children.get(i);

            LayoutParams childLayoutParams = (LayoutParams) child.getLayoutParams();
            maxWidth = maxInternalWidth;
            maxHeight = maxInternalHeight;


            childWidthSpec = createChildMeasureSpec(childLayoutParams.width, maxWidth, widthMode);
            childHeightSpec = createChildMeasureSpec(childLayoutParams.height, maxHeight, heightMode);
            child.measure(childWidthSpec, childHeightSpec);
            childWidth = child.getMeasuredWidth();
            childHeight = child.getMeasuredHeight();

            if (currentRow.wouldExceedMax(childWidth)) {
                currentRow = new RowMeasurement(maxInternalWidth, widthMode);
                rows.add(currentRow);
            }

            currentRow.addChildDimensions(childWidth, childHeight);
        }

        // after all child measuring is done, measure layout height/width

        int longestRowWidth = 0;
        int totalRowHeight = 0;
        for (int index = 0; index < rows.size(); index++) {
            RowMeasurement row = rows.get(index);
            totalRowHeight += row.getHeight();
            if (index < rows.size() - 1) {
                totalRowHeight += verticalSpacing;
            }
            longestRowWidth = Math.max(longestRowWidth, row.getWidth());
        }
        setMeasuredDimension(widthMode == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthMeasureSpec) : longestRowWidth
                + getHorizontalPadding(), heightMode == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightMeasureSpec)
                : totalRowHeight + getVerticalPadding());
        currentRows = Collections.unmodifiableList(rows);
    }

    private int createChildMeasureSpec(int childLayoutParam, int max, int parentMode) {
        int spec;
        if (childLayoutParam == LayoutParams.FILL_PARENT) {
            spec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY);
        } else if (childLayoutParam == LayoutParams.WRAP_CONTENT) {
            spec = MeasureSpec.makeMeasureSpec(max, parentMode == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED
                    : MeasureSpec.AT_MOST);
        } else {
            spec = MeasureSpec.makeMeasureSpec(childLayoutParam, MeasureSpec.EXACTLY);
        }
        // spec: parent's measureSPec
        // padding: extra space in parent not available
        // childDimension: how big child wants, understands match_parent/wrap_content
        getChildMeasureSpec(0, 0, 0);
        return spec;
    }


    @Override
    protected void onLayout(boolean changed, int leftPosition, int topPosition, int rightPosition, int bottomPosition) {
        final int widthOffset = getMeasuredWidth() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();

        Iterator<RowMeasurement> rowIterator = currentRows.iterator();
        RowMeasurement currentRow = rowIterator.next();
        for (View child : getLayoutChildren()) {
            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();
            if (x + childWidth > widthOffset) {
                x = getPaddingLeft();
                y += currentRow.height + verticalSpacing;
                if (rowIterator.hasNext()) {
                    currentRow = rowIterator.next();
                }
            }
            // Align the child vertically.
            int childY = y + (currentRow.height - childHeight) / 2;
            child.layout(x, childY, x + childWidth, childY + childHeight);
            x += childWidth + horizontalSpacing;
        }
    }

    protected List<View> getLayoutChildren() {
        List<View> children = new ArrayList<>();
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child.getVisibility() != View.GONE) {
                children.add(child);
            }
        }
        return children;
    }


    protected int getVerticalPadding() {
        return getPaddingTop() + getPaddingBottom();
    }

    protected int getHorizontalPadding() {
        return getPaddingLeft() + getPaddingRight();
    }

    private final class RowMeasurement {
        private final int maxWidth;
        private final int widthMode;
        private int width;
        private int height;

        public RowMeasurement(int maxWidth, int widthMode) {
            this.maxWidth = maxWidth;
            this.widthMode = widthMode;
        }

        public int getHeight() {
            return height;
        }

        public int getWidth() {
            return width;
        }

        public boolean wouldExceedMax(int childWidth) {
            return widthMode == MeasureSpec.UNSPECIFIED ? false : getNewWidth(childWidth) > maxWidth;
        }

        public void addChildDimensions(int childWidth, int childHeight) {
            width = getNewWidth(childWidth);
            height = Math.max(height, childHeight);
        }

        private int getNewWidth(int childWidth) {
            return width == 0 ? childWidth : width + horizontalSpacing + childWidth;
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p.width, p.height);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public int overflowTextViewId;
        public CharSequence fullText;

        public LayoutParams(int width, int height){
            super(width, height);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs,
                    R.styleable.TextWrapLayout_LayoutParams);

            try {
                overflowTextViewId = a.getResourceId(R.styleable.TextWrapLayout_LayoutParams_layout_wrapTextTo, -1);
            } finally {
                a.recycle();
            }
        }

    }



}