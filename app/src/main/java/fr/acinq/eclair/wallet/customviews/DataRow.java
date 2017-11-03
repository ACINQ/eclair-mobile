package fr.acinq.eclair.wallet.customviews;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import fr.acinq.eclair.wallet.R;

public class DataRow extends LinearLayout {

  private TextView labelTextView;
  private TextView descTextView;
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
      String service = Context.LAYOUT_INFLATER_SERVICE;
      LayoutInflater li = (LayoutInflater) getContext().getSystemService(service);
      LinearLayout layout = (LinearLayout) li.inflate(R.layout.custom_data_row, this, true);
      labelTextView = layout.findViewById(R.id.view_label);
      labelTextView.setText(arr.getString(R.styleable.DataRow_label));
      descTextView = layout.findViewById(R.id.view_desc);
      descTextView.setText(arr.getString(R.styleable.DataRow_desc));
      valueTextView = layout.findViewById(R.id.view_value);
      valueTextView.setText(arr.getString(R.styleable.DataRow_value));
    } finally {
      arr.recycle();
    }
  }

  public void setValue(String value) {
    valueTextView.setText(value);
  }
}
