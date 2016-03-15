package com.voipgrid.vialer.dialer;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * Custom edit text needed to hide keyboard when pasting.
 */
public class DialerEditText extends EditText {

        public DialerEditText(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
            final InputMethodManager imm = ((InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE));
            if (imm != null && imm.isActive(this)) {
                imm.hideSoftInputFromWindow(getApplicationWindowToken(), 0);
            }
        }
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final boolean ret = super.onTouchEvent(event);
            // Must be done after super.onTouchEvent()
            final InputMethodManager imm = ((InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE));
            if (imm != null && imm.isActive(this)) {
                imm.hideSoftInputFromWindow(getApplicationWindowToken(), 0);
            }
            return ret;
        }
}