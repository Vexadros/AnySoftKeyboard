package com.anysoftkeyboard.ime;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import com.anysoftkeyboard.keyboards.AnyKeyboard;
import com.anysoftkeyboard.keyboards.KeyboardDimens;
import com.anysoftkeyboard.keyboards.views.ThemeableChild;
import java.util.List;

public interface InputViewBinder extends InputViewActionsProvider, ThemeableChild {

  /**
   * Returns the {@link KeyboardDimens} describing the UI for the current keyboard view and theme.
   */
  @NonNull
  KeyboardDimens getThemedKeyboardDimens();

  /** Called when the View is no longer needed, and can release any resources it has. */
  void onViewNotRequired();

  /**
   * Sets the state of the control key of the keyboard, if any.
   *
   * @param active whether or not to enable the state of the control key
   * @return true if the control key state changed, false if there was no change
   */
  boolean setControl(boolean active);

  /**
   * Sets the state of the shift key of the keyboard, if any.
   *
   * @param active whether or not to enable the state of the shift key
   * @return true if the shift key state changed, false if there was no change
   */
  boolean setShifted(boolean active);

  /**
   * Returns the state of the shift key of the UI, if any.
   *
   * @return true if the shift is in a pressed state, false otherwise. If there is no shift key on
   *     the keyboard or there is no keyboard attached, it returns false.
   */
  boolean isShifted();

  /**
   * Sets the CAPS-LOCK state of the keyboard.
   *
   * @return true if there was a change in the state.
   */
  boolean setShiftLocked(boolean locked);

  /**
   * Called when the user requests input-view reset
   *
   * @return returns true if something was closed (say, a child-view). Else, false - which means
   *     this event was not consumed by the input-view.
   */
  boolean resetInputView();

  /**
   * Attaches a keyboard to this view. The keyboard can be switched at any time and the view will
   * re-layout itself to accommodate the keyboard.
   *
   * @param currentKeyboard current keyboard to be shown.
   * @param nextAlphabetKeyboard next alphabet keyboard's name.
   * @param nextSymbolsKeyboard next symbols keyboard's name.
   */
  void setKeyboard(
      AnyKeyboard currentKeyboard,
      CharSequence nextAlphabetKeyboard,
      CharSequence nextSymbolsKeyboard);

  /**
   * Sets the current input-connection's imeOptions
   *
   * @param imeOptions a set of {@link android.view.inputmethod.EditorInfo} flags.
   */
  void setKeyboardActionType(int imeOptions);

  /** Is this View currently shown. */
  boolean isShown();

  void setWatermark(@NonNull List<Drawable> watermarks);
}
