package fr.acinq.eclair.wallet.activities;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.util.AsyncExecutor;

import java.net.InetSocketAddress;

import akka.dispatch.OnComplete;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.events.LNNewChannelFailureEvent;
import fr.acinq.eclair.wallet.events.LNNewChannelOpenedEvent;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.Validators;

public class OpenChannelActivity extends EclairActivity {

  public static final String EXTRA_NEW_HOST_URI = BuildConfig.APPLICATION_ID + "NEW_HOST_URI";
  private static final String TAG = "OpenChannelActivity";

  private TextView mCapacityHint;
  private EditText mCapacityValue;
  private TextView mCapacityUnit;
  private TextView mPubkeyTextView;
  private TextView mIPTextView;
  private TextView mPortTextView;
  private Button mOpenButton;
  private View mErrorView;
  private TextView mErrorValue;
  private String prefUnit = Constants.MILLI_BTC_CODE;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_open_channel);

    prefUnit = CoinUtils.getBtcPreferredUnit(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

    mOpenButton = findViewById(R.id.openchannel_do_open);
    mIPTextView = findViewById(R.id.openchannel_ip);
    mPortTextView = findViewById(R.id.openchannel_port);
    mPubkeyTextView = findViewById(R.id.openchannel_pubkey);
    mErrorView = findViewById(R.id.openchannel_error);
    mErrorValue = findViewById(R.id.openchannel_error_value);

    mCapacityHint = findViewById(R.id.openchannel_capacity_hint);
    mCapacityValue = findViewById(R.id.openchannel_capacity_value);
    mCapacityValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        // toggle hint depending on amount input
        mCapacityHint.setVisibility(s == null || s.length() == 0 ? View.VISIBLE : View.GONE);
        checkAmount(s.toString());
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    mCapacityUnit = findViewById(R.id.openchannel_capacity_unit);
    mCapacityUnit.setText(CoinUtils.getBitcoinUnitShortLabel(prefUnit));

    setNodeURI(getIntent().getStringExtra(EXTRA_NEW_HOST_URI));
  }

  /**
   * Checks if the String amount respects the following rules:
   * <ul>
   * <li>numeric</li>
   * <li>convertible to MilliSatoshi in the current user preferred unit</li>
   * <li>exceeds the minimal capacity amount (1mBTC)</li>
   * <li>does not exceed the maximal capacity amount (167 mBTC)</li>
   * <li>does not exceed the available onchain balance (confirmed + unconfirmed), accounting a minimal required leftover</li>
   * </ul>
   * <p>
   * Shows an error in the open channel form if one of the rules is not respected
   *
   * @param amount string amount
   * @return
   */
  private boolean checkAmount(final String amount) {
    try {
      if (amount == null || amount.length() == 0) {
        toggleError(R.string.openchannel_capacity_invalid);
        return false;
      }
      final MilliSatoshi amountMsat = CoinUtils.parseStringToMsat(amount, prefUnit);
      if (amountMsat.amount() < Validators.MIN_FUNDING_MSAT
        || amountMsat.amount() >= Validators.MAX_FUNDING_MSAT) {
        toggleError(R.string.openchannel_capacity_invalid);
        return false;
      } else if (package$.MODULE$.millisatoshi2satoshi(amountMsat).amount() + Validators.MIN_LEFTOVER_ONCHAIN_BALANCE_SAT > app.onChainBalance.get().amount()) {
        toggleError(R.string.openchannel_capacity_notenoughfunds);
        return false;
      } else {
        mErrorView.setVisibility(View.GONE);
        return true;
      }
    } catch (IllegalArgumentException ilex) {
      // the user's preferred unit may be unknown
      Log.w(TAG, "Could not convert amount, check preferred unit? " + ilex.getMessage());
      toggleError(R.string.error_generic);
      disableActions();
      finish(); // prevent any further issue by closing the activity.
      return false;
    } catch (Exception e) {
      Log.d(TAG, "Could not convert amount to number with cause " + e.getMessage());
      toggleError(R.string.openchannel_error_capacity_nan);
      return false;
    }
  }

  private void toggleError(final int errorLabelId) {
    mErrorValue.setText(errorLabelId);
    mErrorView.setVisibility(View.VISIBLE);
  }

  private void setNodeURI(String uri) {
    if (Validators.HOST_REGEX.matcher(uri).matches()) {
      String[] uriArray = uri.split("@", 2);
      if (uriArray.length == 2) {
        String pubkey = uriArray[0];
        String host = uriArray[1];
        String[] hostArray = host.split(":", 2);
        if (hostArray.length == 2) {
          String ip = hostArray[0];
          String port = hostArray[1];

          mPubkeyTextView.setText(pubkey);
          mIPTextView.setText(ip);
          mPortTextView.setText(port);
          mOpenButton.setVisibility(View.VISIBLE);
          return;
        }
      }
    }
    toggleError(R.string.openchannel_error_address);
    disableActions();
  }

  private void disableActions() {
    mOpenButton.setEnabled(false);
    mOpenButton.setOnClickListener(null);
    mCapacityValue.setEnabled(false);
    mOpenButton.setAlpha(0.3f);
  }

  public void cancelOpenChannel(View view) {
    goToHome();
  }

  private void goToHome() {
    finish();
  }

  public void confirmOpenChannel(View view) {
    if (!checkAmount(mCapacityValue.getText().toString())) {
      return;
    }
    disableActions();

    final String pubkeyString = mPubkeyTextView.getText().toString();
    final String ipString = mIPTextView.getText().toString();
    final String portString = mPortTextView.getText().toString();
    final Satoshi fundingSat = package$.MODULE$.millisatoshi2satoshi(CoinUtils.parseStringToMsat(mCapacityValue.getText().toString(), prefUnit));

    AsyncExecutor.create().execute(
      new AsyncExecutor.RunnableEx() {
        @Override
        public void run() throws Exception {

          final BinaryData pubkeyBinary = BinaryData.apply(pubkeyString);
          final Crypto.Point pubkeyPoint = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(pubkeyBinary)));
          final Crypto.PublicKey pubkey = new Crypto.PublicKey(pubkeyPoint, true);

          final InetSocketAddress address = new InetSocketAddress(ipString, Integer.parseInt(portString));
          OnComplete<Object> onComplete = new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable throwable, Object o) throws Throwable {
              if (throwable != null) {
                EventBus.getDefault().post(new LNNewChannelFailureEvent(throwable.getMessage()));
              } else {
                EventBus.getDefault().post(new LNNewChannelOpenedEvent(pubkeyString));
              }
            }
          };
          app.openChannel(30, onComplete, pubkey, address,
            new Switchboard.NewChannel(fundingSat, new MilliSatoshi(0), scala.Option.apply(null)));
        }
      });
    goToHome();
  }
}
