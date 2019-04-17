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

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.databinding.BindingAdapter;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.CustomButtonBinding;

public class CustomButton extends ConstraintLayout {

  private CustomButtonBinding mBinding;

  public CustomButton(Context context) {
    this(context, null);
  }

  public CustomButton(Context context, AttributeSet attrs) {
    this(context, attrs, R.style.ClickableLayout);
  }

  public CustomButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, R.style.ClickableLayout);
    init(attrs, defStyle);
  }

  private void init(AttributeSet attrs, int defStyle) {
    final TypedArray arr = getContext().obtainStyledAttributes(attrs, R.styleable.CustomButton, defStyle, R.style.ClickableLayout);
    final TypedArray selectArr = getContext().obtainStyledAttributes(new int[] { android.R.attr.selectableItemBackground });
    try {
      mBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.custom_button, this, true);
      mBinding.label.setText(arr.getString(R.styleable.CustomButton_text));
      mBinding.label.setTextColor(arr.getColor(R.styleable.CustomButton_text_color, ContextCompat.getColor(getContext(), R.color.grey_4)));

      mBinding.image.setVisibility(GONE);
      if (arr.hasValue(R.styleable.CustomButton_image)) {
        final Drawable imageDrawable = arr.getDrawable(R.styleable.CustomButton_image);
        if (imageDrawable != null) {
          mBinding.image.setImageDrawable(imageDrawable);
          if (arr.hasValue(R.styleable.CustomButton_image_tint)) {
            mBinding.image.setImageTintList(ColorStateList.valueOf(arr.getColor(R.styleable.CustomButton_image_tint, ContextCompat.getColor(getContext(), R.color.grey_4))));
          }
          mBinding.image.setVisibility(VISIBLE);
        }
      }

      if (arr.hasValue(R.styleable.CustomButton_background)) {
        mBinding.getRoot().setBackground(arr.getDrawable(R.styleable.CustomButton_background));
      }
    } finally {
      arr.recycle();
      selectArr.recycle();
    }
  }
}
