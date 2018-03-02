package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.util.Map;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.channel.CLOSED$;
import fr.acinq.eclair.channel.CLOSING$;
import fr.acinq.eclair.channel.CMD_CLOSE;
import fr.acinq.eclair.channel.OFFLINE$;
import fr.acinq.eclair.router.NORMAL$;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.LocalChannelItemHolder;
import fr.acinq.eclair.wallet.databinding.ActivityChannelDetailsBinding;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class ChannelDetailsActivity extends EclairActivity {

  private static final String TAG = "ChannelDetailsActivity";

  private AlertDialog mCloseDialog;
  private ActivityChannelDetailsBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_channel_details);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_channel_details);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);
  }

  @Override
  protected void onStart() {
    super.onStart();
    Intent intent = getIntent();
    String channelId = intent.getStringExtra(LocalChannelItemHolder.EXTRA_CHANNEL_ID);
    try {
      final Map.Entry<ActorRef, EclairEventService.ChannelDetails> channel = getChannel(channelId);

      if (channel.getValue() != null && channel.getKey() != null && channel.getValue() != null) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
        final String state = channel.getValue().state;

        mBinding.balance.setAmountMsat(channel.getValue().balanceMsat);
        mBinding.state.setText(state);

        if (NORMAL$.MODULE$.toString().equals(state)) {
          mBinding.state.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.green));
        } else if (OFFLINE$.MODULE$.toString().equals(state) || state.startsWith("ERR_")) {
          mBinding.state.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red_faded));
        } else {
          mBinding.state.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.orange));
        }

        if (CLOSING$.MODULE$.toString().equals(state)) {
          mBinding.closingTypeView.setVisibility(View.VISIBLE);
          if (channel.getValue().isCooperativeClosing) {
            mBinding.closingType.setText(getString(R.string.channeldetails_closingtype_mutual));
          } else if (channel.getValue().isLocalClosing) {
            mBinding.closingType.setText(getString(R.string.channeldetails_closingtype_local));
          } else if (channel.getValue().isRemoteClosing) {
            mBinding.closingType.setText(getString(R.string.channeldetails_closingtype_remote));
          } else {
            mBinding.closingType.setText(getString(R.string.channeldetails_closingtype_other));
          }
        }

        if (!CLOSING$.MODULE$.toString().equals(channel.getValue().state) && !CLOSED$.MODULE$.toString().equals(channel.getValue().state)) {
          mBinding.close.setVisibility(View.VISIBLE);
          mBinding.close.setOnClickListener(v -> mCloseDialog.show());
        }

        mBinding.nodeid.setValue(channel.getValue().remoteNodeId);
        mBinding.capacity.setValue(CoinUtils.formatAmountInUnit(channel.getValue().capacityMsat, prefUnit, true));
        mBinding.channelId.setValue(channel.getValue().channelId);
        mBinding.reserve.setValue(CoinUtils.formatAmountInUnit(new Satoshi(channel.getValue().channelReserveSat), prefUnit, true));
        mBinding.countHtlcsInflight.setValue(String.valueOf(channel.getValue().htlcsInFlightCount));
        mBinding.minimumHtlcAmount.setValue(CoinUtils.formatAmountInUnit(new MilliSatoshi(channel.getValue().minimumHtlcAmountMsat), prefUnit, true));
        mBinding.transactionId.setValue(channel.getValue().transactionId);
        mBinding.explorerButton.setOnClickListener(WalletUtils.getOpenTxListener(channel.getValue().transactionId));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final String message = getResources().getString(R.string.close_channel_message)
          + (!NORMAL$.MODULE$.toString().equals(channel.getValue().state)
          ? "\n\nWith a " + channel.getValue().state + " state, the closing will be uncooperative!"
          : "");
        builder.setMessage(message);
        builder.setPositiveButton(R.string.btn_ok, (dialog, id) -> {
          scala.Option<BinaryData> none = scala.Option.apply(null);
          channel.getKey().tell(CMD_CLOSE.apply(none), channel.getKey());
          finish();
        });
        builder.setNegativeButton(R.string.btn_cancel, (dialog, id) -> mCloseDialog.dismiss());
        mCloseDialog = builder.create();
      }
    } catch (Exception e) {
      Log.w(TAG, "could not read channel details with cause=" + e.getMessage());
      goToHome();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkInit();
  }

  private void goToHome() {
    Intent homeIntent = new Intent(this, HomeActivity.class);
    startActivity(homeIntent);
  }

  private Map.Entry<ActorRef, EclairEventService.ChannelDetails> getChannel(String channelId) {
    for (Map.Entry<ActorRef, EclairEventService.ChannelDetails> e : EclairEventService.getChannelsMap().entrySet()) {
      if (e.getValue().channelId.equals(channelId)) {
        return e;
      }
    }
    return null;
  }
}
