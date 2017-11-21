package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import fr.acinq.eclair.wallet.R;

public class PaymentFailureActivity extends EclairActivity {

  public static final String EXTRA_PAYMENTFAILURE_AMOUNT = "fr.acinq.eclair.wallet.EXTRA_PAYMENTFAILURE_AMOUNT";
  public static final String EXTRA_PAYMENTFAILURE_DESC = "fr.acinq.eclair.wallet.EXTRA_PAYMENTFAILURE_DESC";
  public static final String EXTRA_PAYMENTFAILURE_CAUSE = "fr.acinq.eclair.wallet.EXTRA_PAYMENTFAILURE_CAUSE";
  public static final String EXTRA_PAYMENTFAILURE_DETAILED_CAUSE = "fr.acinq.eclair.wallet.EXTRA_PAYMENTFAILURE_DETAILED_CAUSE";
  private static final String TAG = "PaymentFailureActivity";

  private Handler dismissHandler;
  private Runnable dismissRunnable;
  private String mSimpleCause;
  private String mDetailedCause;
  private TextView mDetailedCauseView;
  private TextView mToggleDetailsButton;
  private TextView mSimpleCauseView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment_failure);

    Intent intent = getIntent();
    mDetailedCause = intent.getStringExtra(EXTRA_PAYMENTFAILURE_DETAILED_CAUSE);
    mSimpleCause = intent.getStringExtra(EXTRA_PAYMENTFAILURE_CAUSE);

    mSimpleCauseView = findViewById(R.id.paymentfailure_simplecause);
    mToggleDetailsButton = findViewById(R.id.paymentfailure_button_toggle_details);
    mDetailedCauseView = findViewById(R.id.paymentfailure_detailed_cause);
    if (mDetailedCause != null && mDetailedCause.length() > 0) {
      mToggleDetailsButton.setVisibility(View.VISIBLE);
      mDetailedCauseView.setText(Html.fromHtml(mDetailedCause));
    }

    if (mSimpleCause != null && mSimpleCause.length() > 0) {
      mSimpleCauseView.setText(mSimpleCause);
      mSimpleCauseView.setVisibility(View.VISIBLE);
    }

    ImageView mSadImage = findViewById(R.id.paymentfailure_sad);
    mSadImage.setAlpha(0f);
    mSadImage.setScaleX(0.6f);
    mSadImage.setScaleY(0.6f);
    mSadImage.animate().alpha(1).scaleX(1).scaleY(1)
      .setInterpolator(new AnticipateOvershootInterpolator()).setStartDelay(80).setDuration(500).start();

    dismissHandler = new Handler();
    dismissRunnable = new Runnable() {
      public void run() {
        finish();
      }
    };
    dismissHandler.postDelayed(dismissRunnable, 4000);
  }

  public void failure_showDetails(View view) {
    dismissHandler.removeCallbacks(dismissRunnable);
    if (mDetailedCause != null && mDetailedCause.length() > 0) {
      mDetailedCauseView.setVisibility(View.VISIBLE);
      mToggleDetailsButton.setVisibility(View.GONE);
    }
  }

  public void failure_dismiss(View view) {
    dismissHandler.removeCallbacks(dismissRunnable);
    finish();
  }
}
