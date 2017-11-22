package fr.acinq.eclair.wallet.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.util.Map;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.channel.CLOSED;
import fr.acinq.eclair.channel.CLOSING;
import fr.acinq.eclair.channel.CMD_CLOSE;
import fr.acinq.eclair.channel.NORMAL;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.LocalChannelItemHolder;
import fr.acinq.eclair.wallet.customviews.DataRow;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class ChannelDetailsActivity extends EclairActivity {

  private static final String TAG = "ChannelDetailsActivity";

  private AlertDialog mCloseDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_channel_details);

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
        final String prefUnit = CoinUtils.getBtcPreferredUnit(prefs);

        DataRow idRow = findViewById(R.id.channeldetails_id);
        idRow.setValue(channel.getValue().channelId);

        DataRow balanceRow = findViewById(R.id.channeldetails_balance);
        balanceRow.setValue(CoinUtils.formatAmountInUnitWithUnit(channel.getValue().balanceMsat, prefUnit));

        DataRow capacityRow = findViewById(R.id.channeldetails_capacity);
        capacityRow.setValue(CoinUtils.formatAmountInUnitWithUnit(channel.getValue().capacityMsat, prefUnit));

        DataRow nodeIdRow = findViewById(R.id.channeldetails_nodeid);
        nodeIdRow.setValue(channel.getValue().remoteNodeId);

        DataRow stateRow = findViewById(R.id.channeldetails_state);
        stateRow.setValue(channel.getValue().state);

        if (CLOSING.toString().equals(channel.getValue().state)) {
          DataRow closingTypeRow = findViewById(R.id.channeldetails_state_closing_type);
          if (channel.getValue().isCooperativeClosing) {
            closingTypeRow.setValue(getString(R.string.channeldetails_closingtype_mutual));
          } else if (channel.getValue().isLocalClosing) {
            closingTypeRow.setValue(getString(R.string.channeldetails_closingtype_local));
          } else if (channel.getValue().isRemoteClosing) {
            closingTypeRow.setValue(getString(R.string.channeldetails_closingtype_remote));
          } else {
            closingTypeRow.setValue(getString(R.string.channeldetails_closingtype_other));
          }
          findViewById(R.id.channeldetails_state_closing_type_separator).setVisibility(View.VISIBLE);
          closingTypeRow.setVisibility(View.VISIBLE);
        }

        DataRow transactionIdRow = findViewById(R.id.channeldetails_transactionid);
        transactionIdRow.setValue(channel.getValue().transactionId);
        View openInExplorer = findViewById(R.id.open_in_explorer);
        openInExplorer.setOnClickListener(WalletUtils.getOpenTxListener(channel.getValue().transactionId));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final String message = getResources().getString(R.string.close_channel_message)
          + (!NORMAL.toString().equals(channel.getValue().state)
          ? "\n\nWith a " + channel.getValue().state + " state, the closing will be uncooperative!"
          : "");
        builder.setMessage(message);
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
            scala.Option<BinaryData> none = scala.Option.apply(null);
            channel.getKey().tell(CMD_CLOSE.apply(none), channel.getKey());
            finish();
          }
        });
        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
            mCloseDialog.dismiss();
          }
        });
        mCloseDialog = builder.create();

        View closeButton = findViewById(R.id.channeldetails_close);
        if (!CLOSING.toString().equals(channel.getValue().state) && !CLOSED.toString().equals(channel.getValue().state)) {
          closeButton.setVisibility(View.VISIBLE);
          closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              mCloseDialog.show();
            }
          });
        }
      }

    } catch (Exception e) {
      Log.e(TAG, "Internal error", e);
      goToHome();
    }
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
