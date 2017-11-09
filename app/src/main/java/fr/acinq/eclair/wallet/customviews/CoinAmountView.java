package fr.acinq.eclair.wallet.customviews;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.NumberFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import scala.math.BigDecimal;

public class CoinAmountView extends RelativeLayout {
  private String unit;
  private TextView amountTextView;
  private TextView unitTextView;
  private MilliSatoshi amountMsat = new MilliSatoshi(0);

  public CoinAmountView(Context context) {
    super(context);
    init(null, 0);
  }

  public CoinAmountView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs, 0);
  }

  public CoinAmountView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs, defStyle);
  }

  private void init(AttributeSet attrs, int defStyle) {

    final TypedArray arr = getContext().obtainStyledAttributes(attrs, R.styleable.CoinAmountView, defStyle, 0);
    try {
      String service = Context.LAYOUT_INFLATER_SERVICE;
      LayoutInflater li = (LayoutInflater) getContext().getSystemService(service);
      View layout = li.inflate(R.layout.custom_coin_amount_view, this, true);
      amountTextView = layout.findViewById(R.id.view_amount);
      unitTextView = layout.findViewById(R.id.view_unit);
      RelativeLayout relativeLayout = layout.findViewById(R.id.view_relative);

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

      int amount_size = arr.getDimensionPixelSize(R.styleable.CoinAmountView_amount_size, 0);
      int amount_color = arr.getColor(R.styleable.CoinAmountView_amount_color, ContextCompat.getColor(getContext(), R.color.colorGrey_2));
      amountTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, amount_size);
      amountTextView.setTextColor(amount_color);

      unit = arr.getString(R.styleable.CoinAmountView_unit);
      int unit_size = arr.getDimensionPixelSize(R.styleable.CoinAmountView_unit_size, 0);
      int unit_color = arr.getColor(R.styleable.CoinAmountView_unit_color, ContextCompat.getColor(getContext(), R.color.colorGrey_2));
      unitTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit_size);
      unitTextView.setTextColor(unit_color);
      unitTextView.setText(unit);
    } finally {
      arr.recycle();
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (this.unit) {
      case "BTC":
        setUnit("mBTC");
        break;
      case "mBTC":
        setUnit("BTC");
        break;
      default:
        setUnit("BTC");
    }
    return super.onTouchEvent(event);
  }

  public void setAmountMsat(MilliSatoshi amountMsat) {
    this.amountMsat = amountMsat;
    refreshView();
  }
  public MilliSatoshi getAmountMsat() {
    return this.amountMsat;
  }

  public void setUnit(String unit) {
    this.unit = unit;
    unitTextView.setText(this.unit);
    refreshView();
  }

  private void refreshView() {
    switch (this.unit) {
      case "BTC":
        BigDecimal amount_btc = package$.MODULE$.millisatoshi2btc(amountMsat).amount();
        amountTextView.setText(CoinUtils.getBTCFormat().format(amount_btc));
        break;
      case "mBTC":
        BigDecimal amount_mbtc = package$.MODULE$.millisatoshi2millibtc(amountMsat).amount();
        amountTextView.setText(CoinUtils.getMilliBTCFormat().format(amount_mbtc));
        break;
      case "SAT":
        long amount_sat = package$.MODULE$.millisatoshi2satoshi(amountMsat).amount();
        amountTextView.setText(Long.toString(amount_sat));
        break;
      default:
        amountTextView.setText(NumberFormat.getInstance().format(amountMsat.amount()));
    }
    invalidate();
    requestLayout();
  }
}
