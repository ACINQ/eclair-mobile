/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.customviews;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.*;
import android.widget.GridLayout;
import fr.acinq.eclair.wallet.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

/**
 * This is a virtual keyboard containing the [A-Z ] keys (i.e. 27 keys). The space character is allowed. It's basically
 * a grid of buttons. Pressing a button on this keyboard will dispatch an event that you can listen to by implementing
 * a {@link VirtualKeyboard.OnKeyPressedListener}.
 */
public class VirtualKeyboard extends GridLayout {

  private final Logger log = LoggerFactory.getLogger(VirtualKeyboard.class);

  public final static int KEY_DELETE = -1;
  private static final int REPEAT_INTERVAL = 50; // ~20 keys per second
  private static final int MSG_LONGPRESS = 4;
  private static final int MSG_REPEAT = 3;
  private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

  // field to handle key repetition (long press on delete for example)
  Handler mHandler;

  private OnKeyPressedListener mListener;

  /**
   * Listener for the virtual key press event. Implement this to react when a key is pressed.
   */
  public interface OnKeyPressedListener {

    /**
     * Called when a virtual key of the virtual keyboard is pressed.
     *
     * @param keyCode Unicode value of the character for the key.
     */
    void onEvent(final int keyCode);
  }

  public void setOnKeyPressedListener(final OnKeyPressedListener eventListener) {
    mListener = eventListener;
  }

  public VirtualKeyboard(Context context) {
    super(context);
    init();
  }

  public VirtualKeyboard(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public VirtualKeyboard(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  private void dispatchCharEvent(final int keyCode) {
    mHandler.removeMessages(MSG_REPEAT);
    if (mListener != null) {
      if (keyCode != KEY_DELETE) {
        setHapticFeedbackEnabled(true);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
      }
      mListener.onEvent(keyCode);
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private void init() {
    final View root = LayoutInflater.from(getContext()).inflate(R.layout.custom_virtual_keyboard, this);
    root.findViewById(R.id.key_a).setOnClickListener(v -> dispatchCharEvent('a'));
    root.findViewById(R.id.key_b).setOnClickListener(v -> dispatchCharEvent('b'));
    root.findViewById(R.id.key_c).setOnClickListener(v -> dispatchCharEvent('c'));
    root.findViewById(R.id.key_d).setOnClickListener(v -> dispatchCharEvent('d'));
    root.findViewById(R.id.key_e).setOnClickListener(v -> dispatchCharEvent('e'));
    root.findViewById(R.id.key_f).setOnClickListener(v -> dispatchCharEvent('f'));
    root.findViewById(R.id.key_g).setOnClickListener(v -> dispatchCharEvent('g'));
    root.findViewById(R.id.key_h).setOnClickListener(v -> dispatchCharEvent('h'));
    root.findViewById(R.id.key_i).setOnClickListener(v -> dispatchCharEvent('i'));
    root.findViewById(R.id.key_j).setOnClickListener(v -> dispatchCharEvent('j'));
    root.findViewById(R.id.key_k).setOnClickListener(v -> dispatchCharEvent('k'));
    root.findViewById(R.id.key_l).setOnClickListener(v -> dispatchCharEvent('l'));
    root.findViewById(R.id.key_m).setOnClickListener(v -> dispatchCharEvent('m'));
    root.findViewById(R.id.key_n).setOnClickListener(v -> dispatchCharEvent('n'));
    root.findViewById(R.id.key_o).setOnClickListener(v -> dispatchCharEvent('o'));
    root.findViewById(R.id.key_p).setOnClickListener(v -> dispatchCharEvent('p'));
    root.findViewById(R.id.key_q).setOnClickListener(v -> dispatchCharEvent('q'));
    root.findViewById(R.id.key_r).setOnClickListener(v -> dispatchCharEvent('r'));
    root.findViewById(R.id.key_s).setOnClickListener(v -> dispatchCharEvent('s'));
    root.findViewById(R.id.key_t).setOnClickListener(v -> dispatchCharEvent('t'));
    root.findViewById(R.id.key_u).setOnClickListener(v -> dispatchCharEvent('u'));
    root.findViewById(R.id.key_v).setOnClickListener(v -> dispatchCharEvent('v'));
    root.findViewById(R.id.key_w).setOnClickListener(v -> dispatchCharEvent('w'));
    root.findViewById(R.id.key_x).setOnClickListener(v -> dispatchCharEvent('x'));
    root.findViewById(R.id.key_y).setOnClickListener(v -> dispatchCharEvent('y'));
    root.findViewById(R.id.key_z).setOnClickListener(v -> dispatchCharEvent('z'));
    root.findViewById(R.id.key_space).setOnClickListener(v -> dispatchCharEvent(' '));

    root.findViewById(R.id.key_delete).setOnTouchListener((v, event) -> {
      final int action = event.getAction();
      switch (action) {
        case MotionEvent.ACTION_DOWN:
          dispatchCharEvent(KEY_DELETE);
          mHandler.removeMessages(MSG_LONGPRESS);
          mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_LONGPRESS, KEY_DELETE), LONGPRESS_TIMEOUT);
          break;
        case MotionEvent.ACTION_UP:
          if (mHandler != null) {
            mHandler.removeMessages(MSG_REPEAT);
            mHandler.removeMessages(MSG_LONGPRESS);
          }
          break;
        case MotionEvent.ACTION_CANCEL:
          if (mHandler != null) {
            mHandler.removeMessages(MSG_REPEAT);
            mHandler.removeMessages(MSG_LONGPRESS);
          }
          break;
      }
      return false;
    });
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (mHandler == null) {
      mHandler = new RepeatKeyHandler(this);
    }
  }

  static class RepeatKeyHandler extends Handler {
    private final Logger log = LoggerFactory.getLogger(RepeatKeyHandler.class);

    private final WeakReference<VirtualKeyboard> mKeyboard;

    RepeatKeyHandler(VirtualKeyboard keyboard) {
      this.mKeyboard = new WeakReference<>(keyboard);
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_LONGPRESS:
          sendMessage(obtainMessage(MSG_REPEAT, msg.obj));
          break;
        case MSG_REPEAT:
          final VirtualKeyboard keyboard = mKeyboard.get();
          if (keyboard != null) {
            final int keyCode = (int) msg.obj;
            keyboard.dispatchCharEvent(keyCode);
            sendMessageDelayed(obtainMessage(MSG_REPEAT, keyCode), REPEAT_INTERVAL);
          }
          break;
      }
    }
  }
}
