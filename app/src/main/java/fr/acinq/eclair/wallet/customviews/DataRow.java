/*
 * Copyright 2018 ACINQ SAS
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

import android.content.Context;
import android.content.res.TypedArray;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import fr.acinq.eclair.wallet.R;

public class DataRow extends ConstraintLayout {

  public LinearLayout contentLayout;
  public Button actionButton;
  private TextView valueTextView;

  public DataRow(Context context) {
    super(context);
    init(null, 0);
  }

  public DataRow(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs, 0);
  }

  public DataRow(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs, defStyle);
  }

  private void init(AttributeSet attrs, int defStyle) {
    final TypedArray arr = getContext().obtainStyledAttributes(attrs, R.styleable.DataRow, defStyle, 0);
    try {
      final View root = LayoutInflater.from(getContext()).inflate(R.layout.custom_data_row, this);
      final int vPadding = getResources().getDimensionPixelSize(R.dimen.space_sm);
      final int hPadding = getResources().getDimensionPixelSize(R.dimen.space_md);
      root.setPadding(hPadding, vPadding, hPadding, vPadding);
      // label
      final TextView labelTextView = findViewById(R.id.data_label);
      if (arr.hasValue(R.styleable.DataRow_label)) {
        labelTextView.setText(arr.getString(R.styleable.DataRow_label));
      } else {
        labelTextView.setVisibility(GONE);
      }
      // value
      contentLayout = findViewById(R.id.data_content);
      valueTextView = findViewById(R.id.data_value);
      if (arr.hasValue(R.styleable.DataRow_value)) {
        valueTextView.setText(arr.getString(R.styleable.DataRow_value));
      } else {
        valueTextView.setVisibility(GONE);
      }
      // border
      final boolean hasBorder = arr.getBoolean(R.styleable.DataRow_has_border, false);
      final boolean isBottomRounded = arr.getBoolean(R.styleable.DataRow_is_bottom_rounded, false);
      if (hasBorder) {
        setBackground(getResources().getDrawable(R.drawable.white_with_bottom_border));
      } else if (isBottomRounded) {
        setBackground(getResources().getDrawable(R.drawable.rounded_corner_white_bottom_sm));
      } else {
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.almost_white));
      }
      // button
      final boolean hasAction = arr.getBoolean(R.styleable.DataRow_has_action, false);
      if (hasAction) {
        findViewById(R.id.separator).setVisibility(VISIBLE);
        actionButton = findViewById(R.id.data_action);
        actionButton.setVisibility(VISIBLE);
        actionButton.setText(arr.getString(R.styleable.DataRow_action_label));
        actionButton.setTextColor(arr.getColor(R.styleable.DataRow_action_text_color, ContextCompat.getColor(getContext(), R.color.grey_3)));
      }
    } finally {
      arr.recycle();
    }
  }

  @Override
  public void addView(View child, int index, ViewGroup.LayoutParams params) {
    if (contentLayout == null) {
      super.addView(child, index, params);
    } else {
      contentLayout.addView(child);
    }
  }

  public void setHtmlValue(final String value) {
    valueTextView.setVisibility(VISIBLE);
    valueTextView.setText(Html.fromHtml(value));
  }

  public void setValue(final String value) {
    valueTextView.setVisibility(VISIBLE);
    valueTextView.setText(value);
  }


  public void setActionLabel(final String label) {
    actionButton.setVisibility(VISIBLE);
    actionButton.setText(label);
  }
}
