/*
 * Copyright (c) 2016 Menny Even-Danan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anysoftkeyboard.keyboards.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MotionEventCompat;
import com.anysoftkeyboard.addons.AddOn;
import com.anysoftkeyboard.base.utils.CompatUtils;
import com.anysoftkeyboard.base.utils.Logger;
import com.anysoftkeyboard.devicespecific.PressVibrator;
import com.anysoftkeyboard.keyboards.AnyPopupKeyboard;
import com.anysoftkeyboard.keyboards.Keyboard;
import com.anysoftkeyboard.overlay.OverlayData;
import com.anysoftkeyboard.prefs.AnimationsLevel;
import com.anysoftkeyboard.theme.KeyboardTheme;
import com.menny.android.anysoftkeyboard.R;

/**
 * Supports popup keyboard when {@link com.anysoftkeyboard.keyboards.AnyKeyboard.AnyKey} says it has
 * that, and user long-press that key.
 */
public class AnyKeyboardViewWithMiniKeyboard extends SizeSensitiveAnyKeyboardView {

  public interface OnPopupShownListener {
    void onPopupKeyboardShowingChanged(boolean showing);
  }

  private AnyKeyboardViewBase mMiniKeyboard = null;
  private int mMiniKeyboardOriginX;
  private int mMiniKeyboardOriginY;
  private long mMiniKeyboardPopupTime;

  final PopupWindow mMiniKeyboardPopup;

  protected final MiniKeyboardActionListener mChildKeyboardActionListener =
      new MiniKeyboardActionListener(() -> mKeyboardActionListener, this::dismissPopupKeyboard);

  @Nullable private OnPopupShownListener mPopupShownListener;

  public AnyKeyboardViewWithMiniKeyboard(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AnyKeyboardViewWithMiniKeyboard(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    mMiniKeyboardPopup = new PopupWindow(context.getApplicationContext());
    CompatUtils.setPopupUnattachedToDecor(mMiniKeyboardPopup);
    mMiniKeyboardPopup.setBackgroundDrawable(null);
    mDisposables.add(
        mAnimationLevelSubject.subscribe(
            value ->
                mMiniKeyboardPopup.setAnimationStyle(
                    value == AnimationsLevel.None ? 0 : R.style.MiniKeyboardAnimation)));
  }

  public void setOnPopupShownListener(@Nullable OnPopupShownListener listener) {
    mPopupShownListener = listener;
  }

  protected final AnyKeyboardViewBase getMiniKeyboard() {
    return mMiniKeyboard;
  }

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent me) {
    if (getMiniKeyboard() != null && mMiniKeyboardPopup.isShowing()) {
      final int miniKeyboardX = (int) me.getX();
      final int miniKeyboardY = (int) me.getY();
      final int action = MotionEventCompat.getActionMasked(me);

      MotionEvent translated =
          generateMiniKeyboardMotionEvent(action, miniKeyboardX, miniKeyboardY, me.getEventTime());
      getMiniKeyboard().onTouchEvent(translated);
      translated.recycle();
      return true;
    }

    return super.onTouchEvent(me);
  }

  @Override
  public boolean isShifted() {
    if (mMiniKeyboardPopup.isShowing()) return mMiniKeyboard.isShifted();

    return super.isShifted();
  }

  private void setupMiniKeyboardContainer(
      @NonNull AddOn keyboardAddOn, @NonNull Keyboard.Key popupKey, boolean isSticky) {
    final AnyPopupKeyboard keyboard = createPopupKeyboardForKey(keyboardAddOn, popupKey);

    if (isSticky) {
      // using the vertical correction this keyboard has, since the input should behave
      // just as the mParent keyboard
      mMiniKeyboard.setKeyboard(keyboard, mOriginalVerticalCorrection);
    } else {
      // not passing vertical correction, so the popup keyboard will use its own correction
      mMiniKeyboard.setKeyboard(keyboard, mNextAlphabetKeyboardName, mNextSymbolsKeyboardName);
    }

    mMiniKeyboard.measure(
        MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
  }

  @NonNull
  protected AnyPopupKeyboard createPopupKeyboardForKey(
      @NonNull AddOn keyboardAddOn, @NonNull Keyboard.Key popupKey) {
    if (popupKey.popupCharacters != null) {
      // in this case, we must use ASK's context to inflate views and XMLs
      return new AnyPopupKeyboard(
          mDefaultAddOn,
          getContext().getApplicationContext(),
          popupKey.popupCharacters,
          mMiniKeyboard.getThemedKeyboardDimens(),
          "");
    } else {
      Logger.d(
          "CRUSHER",
          "popupKey.externalResourcePopupLayout is %s. keyboard is %s, local is %s",
          popupKey.externalResourcePopupLayout,
          keyboardAddOn.getPackageContext().getPackageName(),
          getContext().getApplicationContext().getPackageName());

      return new AnyPopupKeyboard(
          popupKey.externalResourcePopupLayout ? keyboardAddOn : mDefaultAddOn,
          getContext().getApplicationContext(),
          popupKey.popupResId,
          mMiniKeyboard.getThemedKeyboardDimens(),
          "",
          null,
          null);
    }
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    // Overlay a dark rectangle to dim the keyboard
    if (mMiniKeyboardPopup.isShowing()) {
      mPaint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
      canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
    }
  }

  @SuppressLint("InflateParams")
  public void ensureMiniKeyboardInitialized() {
    if (mMiniKeyboard != null) return;

    LayoutInflater inflater =
        (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    mMiniKeyboard = (AnyKeyboardViewBase) inflater.inflate(R.layout.popup_keyboard_layout, null);

    mMiniKeyboard.setKeyboardTheme(getLastSetKeyboardTheme());
    mMiniKeyboard.setOnKeyboardActionListener(mChildKeyboardActionListener);
    mMiniKeyboard.setThemeOverlay(mThemeOverlay);
  }

  protected void setPopupKeyboardWithView(
      int x, int y, int originX, int originY, View contentView) {
    mMiniKeyboardOriginX = originX;
    mMiniKeyboardOriginY = originY;

    mMiniKeyboardPopup.setContentView(contentView);
    CompatUtils.setPopupUnattachedToDecor(mMiniKeyboardPopup);
    mMiniKeyboardPopup.setWidth(contentView.getMeasuredWidth());
    mMiniKeyboardPopup.setHeight(contentView.getMeasuredHeight());
    mMiniKeyboardPopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y);

    invalidateAllKeys();
  }

  private MotionEvent generateMiniKeyboardMotionEvent(int action, int x, int y, long eventTime) {
    return MotionEvent.obtain(
        mMiniKeyboardPopupTime,
        eventTime,
        action,
        x - mMiniKeyboardOriginX,
        y - mMiniKeyboardOriginY,
        0);
  }

  @Override
  protected boolean onLongPress(
      AddOn keyboardAddOn, Keyboard.Key key, boolean isSticky, @NonNull PointerTracker tracker) {
    if (super.onLongPress(keyboardAddOn, key, isSticky, tracker)) return true;
    if (key.popupResId == 0) return false;

    // don't vibrate when selecting the first popup keyboard key
    PressVibrator.suppressNextVibration();
    showMiniKeyboardForPopupKey(keyboardAddOn, key, isSticky);
    return true;
  }

  @Override
  public void setThemeOverlay(@NonNull OverlayData overlayData) {
    super.setThemeOverlay(overlayData);
    if (mMiniKeyboard != null) {
      mMiniKeyboard.setThemeOverlay(mThemeOverlay);
    }
  }

  @Override
  public void setKeyboardTheme(@NonNull KeyboardTheme theme) {
    super.setKeyboardTheme(theme);
    // will be recreated with the new theme.
    mMiniKeyboard = null;
  }

  protected void showMiniKeyboardForPopupKey(
      @NonNull AddOn keyboardAddOn, @NonNull Keyboard.Key popupKey, boolean isSticky) {
    final int[] windowOffset = new int[2];
    getLocationInWindow(windowOffset);

    ensureMiniKeyboardInitialized();

    setupMiniKeyboardContainer(keyboardAddOn, popupKey, isSticky);

    Point miniKeyboardPosition =
        PopupKeyboardPositionCalculator.calculatePositionForPopupKeyboard(
            popupKey, this, mMiniKeyboard, mPreviewPopupTheme, windowOffset);

    final int x = miniKeyboardPosition.x;
    final int y = miniKeyboardPosition.y;

    final int originX = x - windowOffset[0];
    final int originY = y + mMiniKeyboard.getPaddingTop() - windowOffset[1];

    // NOTE:I'm checking the main keyboard shift state directly!
    // Not anything else.
    mMiniKeyboard.setShifted(getKeyboard() != null && getKeyboard().isShifted());

    setPopupKeyboardWithView(x, y, originX, originY, mMiniKeyboard);

    setPopupStickinessValues(
        isSticky, !isSticky, Keyboard.Key.getCenterX(popupKey), Keyboard.Key.getCenterY(popupKey));

    dismissAllKeyPreviews();

    if (mPopupShownListener != null) mPopupShownListener.onPopupKeyboardShowingChanged(true);
  }

  protected void setPopupStickinessValues(
      boolean isSticky, boolean requiresSlideInMotionEvent, int touchX, int touchY) {
    mChildKeyboardActionListener.setInOneShot(!isSticky);
    if (requiresSlideInMotionEvent) {
      // Inject down event on the key to mini keyboard.
      long eventTime = SystemClock.uptimeMillis();
      mMiniKeyboardPopupTime = eventTime;
      MotionEvent downEvent =
          generateMiniKeyboardMotionEvent(MotionEvent.ACTION_DOWN, touchX, touchY, eventTime);
      mMiniKeyboard.onTouchEvent(downEvent);
      downEvent.recycle();
    }
  }

  public boolean dismissPopupKeyboard() {
    if (mMiniKeyboardPopup.isShowing()) {
      if (mMiniKeyboard != null) mMiniKeyboard.resetInputView();
      mMiniKeyboardPopup.dismiss();
      mMiniKeyboardOriginX = 0;
      mMiniKeyboardOriginY = 0;
      mPointerQueue.cancelAllPointers();
      invalidateAllKeys();
      if (mPopupShownListener != null) {
        mPopupShownListener.onPopupKeyboardShowingChanged(false);
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void disableTouchesTillFingersAreUp() {
    super.disableTouchesTillFingersAreUp();
    dismissPopupKeyboard();
  }

  @Override
  public boolean resetInputView() {
    return dismissPopupKeyboard() || super.resetInputView();
  }

  @Override
  public void onViewNotRequired() {
    super.onViewNotRequired();
    CompatUtils.unbindDrawable(mPreviewPopupTheme.getPreviewKeyBackground());
    if (mMiniKeyboard != null) mMiniKeyboard.onViewNotRequired();
    mMiniKeyboard = null;
  }
}
