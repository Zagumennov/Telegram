package org.telegram.ui.Profile;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class EvenlyWidthLayout extends ViewGroup {

    private int dividerWidth = 0;
    private int paddingLeft = 0;
    private int paddingRight = 0;
    private int paddingBottom = 0;
    private int childHeight = 0;

    public EvenlyWidthLayout(Context context) {
        super(context);
    }

    public EvenlyWidthLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EvenlyWidthLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void setPaddingLeft(int paddingLeft) {
        this.paddingLeft = paddingLeft;
        requestLayout();
    }
    public void setPaddingRight(int paddingRight) {
        this.paddingRight = paddingRight;
        requestLayout();
    }

    public void setPaddingBottom(int paddingBottom) {
        this.paddingBottom = paddingBottom;
        requestLayout();
    }

    public void setChildHeight(int childHeight) {
        this.childHeight = childHeight;
        requestLayout();
    }

    public void setDividerSize(int size) {
        this.dividerWidth = size;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int totalWidth = widthSize - paddingLeft - paddingRight;
        int childCount = getChildCount();
        int availableWidth = totalWidth - (childCount > 0 ? (childCount - 1) * dividerWidth : 0);

        int childWidth = childCount > 0 ? availableWidth / childCount : 0;
        int maxChildHeight = 0;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
                int childHeightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.AT_MOST);
                child.measure(childWidthSpec, childHeightSpec);
            }
        }

        int finalWidth = widthMode == MeasureSpec.EXACTLY ? widthSize : paddingLeft + paddingRight + childCount * childWidth + (childCount - 1) * dividerWidth;
        int finalHeight = heightMode == MeasureSpec.EXACTLY ? heightSize : childHeight + paddingBottom;

        setMeasuredDimension(finalWidth, finalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childCount = getChildCount();
        if (childCount == 0) return;

        int childWidth = getChildAt(0).getMeasuredWidth();
        int childHeight = getChildAt(0).getMeasuredHeight();

        int currentLeft = paddingLeft;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                child.layout(
                        currentLeft,
                        0,
                        currentLeft + childWidth,
                        childHeight
                );
                currentLeft += childWidth + dividerWidth;
            }
        }
    }
}