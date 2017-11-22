package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.LightningErrorListAdapter;
import fr.acinq.eclair.wallet.models.LightningPaymentError;

public class PaymentFailureActivity extends EclairActivity {

  public static final String EXTRA_PAYMENT_SIMPLE_ONLY = BuildConfig.APPLICATION_ID + "EXTRA_SIMPLE_ONLY";
  public static final String EXTRA_PAYMENT_SIMPLE_MESSAGE = BuildConfig.APPLICATION_ID + "EXTRA_SIMPLE_MESSAGE";
  public static final String EXTRA_PAYMENT_ERRORS = BuildConfig.APPLICATION_ID + "EXTRA_PAYMENT_ERRORS";
  private static final String TAG = "PaymentFailureActivity";

  private Handler dismissHandler;
  private Runnable dismissRunnable;
  private TextView mMessageView;
  private Button mToggleDetailed;
  private RecyclerView mErrorsView;

  private LightningErrorListAdapter mLightningErrorsAdapter;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment_failure);

    // close activity after a few seconds
    dismissHandler = new Handler();
    dismissRunnable = new Runnable() {
      public void run() {
        finish();
      }
    };
    dismissHandler.postDelayed(dismissRunnable, 4000);

    final Intent intent = getIntent();
    final boolean displaySimpleMessageOnly = intent.getBooleanExtra(EXTRA_PAYMENT_SIMPLE_ONLY, true);
    final String simpleMessage = intent.getStringExtra(EXTRA_PAYMENT_SIMPLE_MESSAGE);
    mMessageView = findViewById(R.id.paymentfailure_simple);
    if (displaySimpleMessageOnly) {
      mMessageView.setText(simpleMessage);
    } else {
      final List<LightningPaymentError> errors = intent.getParcelableArrayListExtra(EXTRA_PAYMENT_ERRORS);
      mMessageView.setText(getString(R.string.paymentfailure_error_size, errors.size()));

      mLightningErrorsAdapter = new LightningErrorListAdapter(errors);
      mErrorsView = findViewById(R.id.paymentfailure_errors);
      mErrorsView.setHasFixedSize(true);
      mErrorsView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
      mErrorsView.setAdapter(mLightningErrorsAdapter);

      mToggleDetailed = findViewById(R.id.paymentfailure_toggle);
      mToggleDetailed.setVisibility(View.VISIBLE);
      mToggleDetailed.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          dismissHandler.removeCallbacks(dismissRunnable); // give time to read errors
          mToggleDetailed.setVisibility(View.GONE);
          mErrorsView.setVisibility(View.VISIBLE);
        }
      });
    }

    // animation
    final ImageView mSadImage = findViewById(R.id.paymentfailure_sad);
    mSadImage.setAlpha(0f);
    mSadImage.setScaleX(0.6f);
    mSadImage.setScaleY(0.6f);
    mSadImage.animate().alpha(1).scaleX(1).scaleY(1)
      .setInterpolator(new AnticipateOvershootInterpolator()).setStartDelay(80).setDuration(500).start();
  }

  public void failure_dismiss(final View view) {
    dismissHandler.removeCallbacks(dismissRunnable);
    finish();
  }
}
