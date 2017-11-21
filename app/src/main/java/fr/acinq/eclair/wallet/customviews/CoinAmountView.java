package fr.acinq.eclair.wallet.customviews;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.utils.CoinUtils;

public class CoinAmountView extends RelativeLayout {
  private static final String TAG = "CoinAmtView";
  private final SharedPreferences prefs;
  private TextView amountTextView;
  private TextView unitTextView;
  private ImageView imageView;
  private MilliSatoshi amountMsat = new MilliSatoshi(0);
  private String prefBtcUnit;
  private String prefFiatCurrency;

  public CoinAmountView(final Context context) {
    super(context);
    this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    init(null, 0);
  }

  public CoinAmountView(final Context context, final AttributeSet attrs) {
    super(context, attrs);
    this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    init(attrs, 0);
  }

  public CoinAmountView(final Context context, final AttributeSet attrs, final int defStyle) {
    super(context, attrs, defStyle);
    init(attrs, defStyle);
    this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
  }

  public void refreshUnits() {
    this.prefBtcUnit = CoinUtils.getBtcPreferredUnit(prefs);
    this.prefFiatCurrency = CoinUtils.getPreferredFiat(prefs);
    final boolean displayAmountAsFiat = CoinUtils.shouldDisplayInFiat(prefs);
    if (displayAmountAsFiat) {
      amountTextView.setText(CoinUtils.convertMsatToFiat(amountMsat.amount(), prefFiatCurrency));
      unitTextView.setText(prefFiatCurrency.toUpperCase());
    } else {
      amountTextView.setText(CoinUtils.formatAmountInUnit(amountMsat, prefBtcUnit));
      unitTextView.setText(CoinUtils.getShortLabel(prefBtcUnit));
    }
    refreshView();
  }

  private void init(final AttributeSet attrs, final int defStyle) {
    final TypedArray arr = getContext().obtainStyledAttributes(attrs, R.styleable.CoinAmountView, defStyle, 0);
    try {
      final String service = Context.LAYOUT_INFLATER_SERVICE;
      final LayoutInflater li = (LayoutInflater) getContext().getSystemService(service);
      final View layout = li.inflate(R.layout.custom_coin_amount_view, this, true);
      amountTextView = layout.findViewById(R.id.view_amount);
      unitTextView = layout.findViewById(R.id.view_unit);
      imageView = layout.findViewById(R.id.view_image);
      final RelativeLayout relativeLayout = layout.findViewById(R.id.view_relative);

      switch (arr.getInt(R.styleable.CoinAmountView_alignment, 0)) {
        case 1:
          relativeLayout.setGravity(Gravity.CENTER);
          break;
        case 2:
          relativeLayout.setGravity(Gravity.RIGHT);
          break;
        default:
          relativeLayout.setGravity(Gravity.LEFT);
      }

      final int imageResId = arr.getResourceId(R.styleable.CoinAmountView_image_src, 0);
      if (imageResId != 0) {
        final int imageSize = arr.getDimensionPixelSize(R.styleable.CoinAmountView_image_size, 0);
        imageView.setImageResource(imageResId);
        imageView.setVisibility(VISIBLE);
        imageView.getLayoutParams().height = imageSize;
        imageView.getLayoutParams().width = imageSize;
      }

      final int amountSize = arr.getDimensionPixelSize(R.styleable.CoinAmountView_amount_size, 0);
      final int amountColor = arr.getColor(R.styleable.CoinAmountView_amount_color, ContextCompat.getColor(getContext(), R.color.colorGrey_2));
      final boolean isAmountBold = arr.getBoolean(R.styleable.CoinAmountView_amount_bold, false);
      if (isAmountBold) {
        amountTextView.setTypeface(null, Typeface.BOLD);
      }
      amountTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, amountSize);
      amountTextView.setTextColor(amountColor);

      final int unitSize = arr.getDimensionPixelSize(R.styleable.CoinAmountView_unit_size, 0);
      final int unitColor = arr.getColor(R.styleable.CoinAmountView_unit_color, ContextCompat.getColor(getContext(), R.color.colorGrey_2));
      final boolean isUnitBold = arr.getBoolean(R.styleable.CoinAmountView_unit_bold, false);
      if (isUnitBold) {
        unitTextView.setTypeface(null, Typeface.BOLD);
      }
      unitTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, unitSize);
      unitTextView.setTextColor(unitColor);

      refreshUnits();
    } finally {
      arr.recycle();
    }
  }

  public MilliSatoshi getAmountMsat() {
    return this.amountMsat;
  }

  public void setAmountMsat(final MilliSatoshi amountMsat) {
    this.amountMsat = amountMsat;
    refreshUnits();
  }

  private void refreshView() {
    invalidate();
    requestLayout();
  }
}
