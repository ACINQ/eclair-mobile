package fr.acinq.eclair.wallet.customviews;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.util.TypedValue;

import fr.acinq.eclair.wallet.R;

public class TitleBar extends AppCompatTextView {

  public TitleBar(Context context) {
    super(context);
    init(null, 0);
  }

  public TitleBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs, 0);
  }

  public TitleBar(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs, defStyle);
  }

  private void init(AttributeSet attrs, int defStyle) {
    setTextColor(ContextCompat.getColor(getContext(), R.color.grey_2));
    setBackgroundColor(ContextCompat.getColor(getContext(), R.color.grey_0_light_x1));
    int smPadding = (int) getResources().getDimension(R.dimen.title_bar_padding_sm);
    int mdPadding = (int) getResources().getDimension(R.dimen.title_bar_padding_md);
    setPadding(smPadding, mdPadding, smPadding, smPadding);
    setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.title_bar_text_size));
    setAllCaps(true);
    setMaxLines(1);
  }
}
