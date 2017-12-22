package fr.acinq.eclair.wallet.activities;

import android.content.SharedPreferences;
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
import fr.acinq.eclair.channel.Channel;
import fr.acinq.eclair.io.Peer;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.events.LNNewChannelFailureEvent;
import fr.acinq.eclair.wallet.events.LNNewChannelOpenedEvent;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.Validators;

public class OpenChannelActivity extends EclairActivity {

  public static final String EXTRA_NEW_HOST_URI = BuildConfig.APPLICATION_ID + "NEW_HOST_URI";
  private static final String TAG = "OpenChannelActivity";

  private TextView mCapacityHint;
  private EditText mCapacityValue;
  private TextView mCapacityUnit;
  private TextView mCapacityFiat;
  private TextView mPubkeyTextView;
  private TextView mIPTextView;
  private TextView mPortTextView;
  private Button mOpenButton;
  private View mErrorView;
  private TextView mErrorValue;

  private String preferredFiatCurrency = Constants.FIAT_USD;
  private String preferredBitcoinUnit = Constants.MILLI_BTC_CODE;
  final MilliSatoshi minFunding = new MilliSatoshi(100000000); // 1 mBTC
  final MilliSatoshi maxFunding = package$.MODULE$.satoshi2millisatoshi(new Satoshi(Channel.MAX_FUNDING_SATOSHIS()));

  private PinDialog pinDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_open_channel);

    final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    preferredFiatCurrency = CoinUtils.getPreferredFiat(sharedPrefs);
    preferredBitcoinUnit = CoinUtils.getBtcPreferredUnit(sharedPrefs);

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
        try {
          checkAmount(s.toString());
        } catch (Exception e) {
          Log.d(TAG, "Could not convert amount to number with cause " + e.getMessage());
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    mCapacityUnit = findViewById(R.id.openchannel_capacity_unit);
    mCapacityUnit.setText(CoinUtils.getBitcoinUnitShortLabel(preferredBitcoinUnit));
    mCapacityFiat = findViewById(R.id.openchannel_capacity_fiat);

    setNodeURI(getIntent().getStringExtra(EXTRA_NEW_HOST_URI));
    mCapacityValue.requestFocus();
  }

  public void focusAmount(final View view) {
    mCapacityValue.requestFocus();
  }

  @Override
  protected void onPause() {
    // dismiss the pin dialog if it exists to prevent leak.
    if (pinDialog != null) {
      pinDialog.dismiss();
    }
    super.onPause();
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
   * Show an error in the open channel form if one of the rules is not respected.
   *
   * @param amount string amount
   *
   * @return true if amount is valid, false otherwise
   */
  private boolean checkAmount(final String amount) throws IllegalArgumentException, NullPointerException {
    final MilliSatoshi amountMsat = CoinUtils.parseStringToMsat(amount, preferredBitcoinUnit);
    mCapacityFiat.setText(CoinUtils.convertMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
    if (amountMsat.amount() < minFunding.amount() || amountMsat.amount() >= maxFunding.amount()) {
      toggleError(getString(R.string.openchannel_capacity_invalid, CoinUtils.formatAmountInUnit(minFunding, preferredBitcoinUnit),
        CoinUtils.formatAmountInUnitWithUnit(maxFunding, preferredBitcoinUnit)));
      return false;
    } else if (package$.MODULE$.millisatoshi2satoshi(amountMsat).amount() + Validators.MIN_LEFTOVER_ONCHAIN_BALANCE_SAT > app.onChainBalance.get().amount()) {
      toggleError(getString(R.string.openchannel_capacity_notenoughfunds));
      return false;
    } else {
      mErrorView.setVisibility(View.GONE);
      return true;
    }
  }

  private void toggleError(final String errorLabel) {
    mErrorValue.setText(errorLabel);
    mErrorView.setVisibility(View.VISIBLE);
  }

  private void setNodeURI(final String uri) {
    if (uri != null && Validators.HOST_REGEX.matcher(uri).matches()) {
      String[] uriArray = uri.split("@", 2);
      if (uriArray.length == 2) {
        final String pubkey = uriArray[0];
        String[] hostArray = uriArray[1].split(":", 2);
        if (hostArray.length == 1) {
          final String ip = hostArray[0];
          final String port = "9735"; // if the port is not set in the URI, default to 9735
          setURIFields(pubkey, ip, port);
          return;
        } else if (hostArray.length == 2) {
          final String ip = hostArray[0];
          final String port = hostArray[1];
          setURIFields(pubkey, ip, port);
          return;
        }
      }
    }
    toggleError(getString(R.string.openchannel_error_address));
    disableForm();
  }

  private void setURIFields(final String pubkey, final String ip, final String port) {
    mPubkeyTextView.setText(pubkey);
    mIPTextView.setText(ip);
    mPortTextView.setText(port);
    mOpenButton.setVisibility(View.VISIBLE);
  }

  private void disableForm() {
    mOpenButton.setEnabled(false);
    mCapacityValue.setEnabled(false);
    mOpenButton.setAlpha(0.3f);
  }
  private void enableForm() {
    mOpenButton.setEnabled(true);
    mCapacityValue.setEnabled(true);
    mOpenButton.setAlpha(1f);
  }

  public void cancelOpenChannel(View view) {
    goToHome();
  }

  private void goToHome() {
    finish();
  }

  public void confirmOpenChannel(View view) {
    try {
      if (!checkAmount(mCapacityValue.getText().toString())) {
        return;
      }
    } catch (Exception e) {
      Log.d(TAG, "Could not convert amount to number with cause " + e.getMessage());
      toggleError(getString(R.string.openchannel_error_capacity_nan));
      return;
    }
    disableForm();
    if (isPinRequired()) {
      pinDialog = new PinDialog(OpenChannelActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
        @Override
        public void onPinConfirm(final PinDialog dialog, final String pinValue) {
          if (isPinCorrect(pinValue, dialog)) {
            doOpenChannel();
          } else {
            toggleError(getString(R.string.payment_error_incorrect_pin));
            enableForm();
          }
        }
        @Override
        public void onPinCancel(PinDialog dialog) {
          enableForm();
        }
      });
      pinDialog.show();
    } else {
      doOpenChannel();
    }
  }

  private void doOpenChannel() {

    final String pubkeyString = mPubkeyTextView.getText().toString();
    final String ipString = mIPTextView.getText().toString();
    final String portString = mPortTextView.getText().toString();
    final Satoshi fundingSat = package$.MODULE$.millisatoshi2satoshi(CoinUtils.parseStringToMsat(mCapacityValue.getText().toString(), preferredBitcoinUnit));

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
            new Peer.OpenChannel(pubkey, fundingSat, new MilliSatoshi(0), scala.Option.apply(null)));
        }
      });
    goToHome();
  }
}
