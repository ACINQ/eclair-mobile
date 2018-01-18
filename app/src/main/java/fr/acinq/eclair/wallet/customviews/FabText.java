package fr.acinq.eclair.wallet.customviews;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import fr.acinq.eclair.wallet.R;

public class FabText extends RelativeLayout {
  private View layout;
  private TextView labelTextView;
  private FloatingActionButton fab;

  public FabText(Context context) {
    super(context);
    init(null, 0);
  }

  public FabText(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs, 0);
  }

  public FabText(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs, defStyle);
  }

  private void init(AttributeSet attrs, int defStyle) {
    final TypedArray arr = getContext().obtainStyledAttributes(attrs, R.styleable.FabText, defStyle, 0);
    try {
      String service = Context.LAYOUT_INFLATER_SERVICE;
      LayoutInflater li = (LayoutInflater) getContext().getSystemService(service);
      layout = li.inflate(R.layout.custom_fab_text, this, true);
      int bgColor = arr.getColor(R.styleable.FabText_bgcolor, ContextCompat.getColor(getContext(), R.color.primary));
      String label = arr.getString(R.styleable.FabText_label);

      labelTextView = layout.findViewById(R.id.fabtext_label);
      labelTextView.setText(label);
      labelTextView.setBackgroundColor(bgColor);
      if ("".equals(label)) labelTextView.setVisibility(GONE);

      fab = layout.findViewById(R.id.fabtext_button);
      fab.setImageResource(arr.getResourceId(R.styleable.FabText_icon, R.mipmap.ic_plus));
      fab.setBackgroundTintList(ColorStateList.valueOf(bgColor));
    } finally {
      arr.recycle();
    }
  }

}
