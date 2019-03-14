/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.activities;

import akka.actor.ActorRef;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.View;
import android.widget.Toast;
import com.google.common.base.Strings;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.Features;
import fr.acinq.eclair.Globals;
import fr.acinq.eclair.channel.*;
import fr.acinq.eclair.router.NORMAL$;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.adapters.LocalChannelItemHolder;
import fr.acinq.eclair.wallet.databinding.ActivityChannelDetailsBinding;
import fr.acinq.eclair.wallet.fragments.CloseChannelDialog;
import fr.acinq.eclair.wallet.models.ClosingType;
import fr.acinq.eclair.wallet.models.LocalChannel;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChannelDetailsActivity extends EclairActivity {

  private final Logger log = LoggerFactory.getLogger(ChannelDetailsActivity.class);

  public static final Set<String> STATE_MUTUAL_CLOSE = new HashSet<>(Arrays.asList(WAIT_FOR_INIT_INTERNAL$.MODULE$.toString(), WAIT_FOR_OPEN_CHANNEL$.MODULE$.toString(), WAIT_FOR_ACCEPT_CHANNEL$.MODULE$.toString(), WAIT_FOR_FUNDING_INTERNAL$.MODULE$.toString(), WAIT_FOR_FUNDING_CREATED$.MODULE$.toString(), WAIT_FOR_FUNDING_SIGNED$.MODULE$.toString(), NORMAL$.MODULE$.toString()));
  public static final Set<String> STATE_FORCE_CLOSE = new HashSet<>(Arrays.asList(WAIT_FOR_FUNDING_CONFIRMED$.MODULE$.toString(), WAIT_FOR_FUNDING_LOCKED$.MODULE$.toString(), NORMAL$.MODULE$.toString(), SHUTDOWN$.MODULE$.toString(), NEGOTIATING$.MODULE$.toString(), OFFLINE$.MODULE$.toString(), SYNCING$.MODULE$.toString()));

  private CloseChannelDialog mCloseChannelDialog;
  private ActivityChannelDetailsBinding mBinding;
  private String mChannelId;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_channel_details);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);
    Intent intent = getIntent();
    mChannelId = intent.getStringExtra(LocalChannelItemHolder.EXTRA_CHANNEL_ID);
  }

  @Override
  protected void onPause() {
    // dismiss the pin dialog if it exists to prevent leak.
    if (mCloseChannelDialog != null) {
      mCloseChannelDialog.dismiss();
    }
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (checkInit(ChannelDetailsActivity.class.getSimpleName(), mChannelId)) {
      refreshChannel();
    }
  }

  @Override
  protected void onNewIntent(final Intent intent) {
    super.onNewIntent(intent);
    mChannelId = intent.getStringExtra(LocalChannelItemHolder.EXTRA_CHANNEL_ID);
  }

  private void refreshChannel() {
    try {
      final Map.Entry<ActorRef, LocalChannel> activeChannel = NodeSupervisor.getChannelFromId(mChannelId);
      if (activeChannel != null && activeChannel.getValue() != null) {
        setupView(activeChannel.getValue(), activeChannel.getKey());
      } else {
        log.debug("could not find active channel with id={}", mChannelId);
        final LocalChannel channelDB = app.getDBHelper().getLocalChannel(mChannelId);
        if (channelDB != null) {
          setupView(channelDB, null);
        } else {
          log.debug("could not find channel with id={} in database", mChannelId);
          Toast.makeText(this, getString(R.string.channeldetails_unknown), Toast.LENGTH_LONG).show();
          finish();
        }
      }
    } catch (Exception e) {
      log.error("could not read channel details with cause={}", e.getMessage());
      finish();
    }
  }

  private void setupView(final LocalChannel channel, @Nullable final ActorRef channelRef) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
    final String fiatUnit = WalletUtils.getPreferredFiat(prefs);
    mBinding.setIsActive(channel.getIsActive());

    if (channel.getIsActive()) {
      mBinding.balance.setValue(getString(R.string.channeldetails_balance_value,
        CoinUtils.formatAmountInUnit(new MilliSatoshi(channel.getBalanceMsat()), prefUnit, true),
        WalletUtils.formatMsatToFiatWithUnit(channel.getBalanceMsat(), fiatUnit))
      );
      mBinding.capacity.setValue(getString(R.string.channeldetails_balance_value,
        CoinUtils.formatAmountInUnit(new MilliSatoshi(channel.getCapacityMsat()), prefUnit, true),
        WalletUtils.formatMsatToFiatWithUnit(channel.getCapacityMsat(), fiatUnit))
      );
      final double progress = channel.getCapacityMsat() != 0 ? (double) channel.getBalanceMsat() / channel.getCapacityMsat() * 100 : 0;
      mBinding.balanceProgress.setProgress(100 - (int) progress);
      mBinding.maxReceivable.setText(CoinUtils.formatAmountInUnit(new MilliSatoshi(channel.getReceivableMsat()), prefUnit, true));
      mBinding.maxReceivableFiat.setText(getString(R.string.amount_to_fiat, WalletUtils.formatMsatToFiatWithUnit(channel.getReceivableMsat(), fiatUnit)));
      mBinding.maxSendable.setText(CoinUtils.formatAmountInUnit(new MilliSatoshi(channel.getSendableMsat()), prefUnit, true));
      mBinding.maxSendableFiat.setText(getString(R.string.amount_to_fiat, WalletUtils.formatMsatToFiatWithUnit(channel.getSendableMsat(), fiatUnit)));

      mBinding.state.setText(channel.state);

      if (NORMAL$.MODULE$.toString().equals(channel.state)) {
        mBinding.state.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.green));
      } else if (OFFLINE$.MODULE$.toString().equals(channel.state) || channel.state.startsWith("ERR_")) {
        mBinding.state.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red_faded));
      } else {
        mBinding.state.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.orange));
      }

      if (CLOSING$.MODULE$.toString().equals(channel.state)) {
        mBinding.closingType.setVisibility(View.VISIBLE);
        mBinding.closingTypeTitle.setVisibility(View.VISIBLE);
        if (ClosingType.MUTUAL.equals(channel.getClosingType())) {
          mBinding.closingType.setText(getString(R.string.channeldetails_closingtype_mutual));
        } else if (ClosingType.LOCAL.equals(channel.getClosingType())) {
          mBinding.closingType.setText(getString(R.string.channeldetails_closingtype_local));
        } else if (ClosingType.REMOTE.equals(channel.getClosingType())) {
          mBinding.closingType.setText(getString(R.string.channeldetails_closingtype_remote));
        } else {
          mBinding.closingType.setText(getString(R.string.channeldetails_closingtype_other));
        }
      }

      // show reconnect buttons if offline
      if (OFFLINE$.MODULE$.toString().equals(channel.state)) {
        mBinding.updateNodeAddressSeparator.setVisibility(View.VISIBLE);
        mBinding.updateNodeAddressButton.setVisibility(View.VISIBLE);
        mBinding.updateNodeAddressButton.setOnClickListener((v) -> {
          final Intent intent = new Intent(getBaseContext(), OpenConnectionActivity.class);
          intent.putExtra(OpenConnectionActivity.EXTRA_CONN_NODE_ID, channel.getPeerNodeId());
          startActivity(intent);
        });
      }

      mCloseChannelDialog = new CloseChannelDialog(ChannelDetailsActivity.this, dialog -> finish(), channelRef,
        app.getElectrumState() == null ? null : app.getElectrumState().onchainAddress, STATE_MUTUAL_CLOSE.contains(channel.state), STATE_FORCE_CLOSE.contains(channel.state));
      mBinding.closeButton.setOnClickListener(v -> mCloseChannelDialog.show());
      mBinding.closeButton.setVisibility(STATE_MUTUAL_CLOSE.contains(channel.state) || STATE_FORCE_CLOSE.contains(channel.state)
        ? View.VISIBLE : View.GONE);

      mBinding.channelId.actionButton.setOnClickListener(v -> openRawDataWindow());
    } else {
      // ---- channel is inactive
      mBinding.channelId.actionButton.setVisibility(View.GONE);
      final String closedBalance = CoinUtils.formatAmountInUnit(new MilliSatoshi(channel.getBalanceMsat()), prefUnit, true);
      mBinding.balanceClosed.setValue(closedBalance);
      mBinding.balanceClosed.setVisibility(View.VISIBLE);
      mBinding.terminatedDisclaimer.setText(getString(R.string.channeldetails_terminated_disclaimer));
      mBinding.openedOn.setText(Html.fromHtml(getString(R.string.channeldetails_opened_on,
        DateFormat.getDateTimeInstance().format(channel.getCreated()))));
      mBinding.closedOn.setText(Html.fromHtml(getString(R.string.channeldetails_closed_on,
        DateFormat.getDateTimeInstance().format(channel.getUpdated()))));
    }

    if (CLOSING$.MODULE$.toString().equals(channel.state) || !channel.getIsActive()) {
      if (!Strings.isNullOrEmpty(channel.getClosingErrorMessage())) {
        mBinding.closingCause.setValue(channel.getClosingErrorMessage());
      }
      mBinding.closingCause.setVisibility(View.VISIBLE);
      mBinding.closingSection.setVisibility(View.VISIBLE);
    }

    if (CLOSING$.MODULE$.toString().equals(channel.state)) {
      if (channel.getRefundAtBlock() > 0) {
        mBinding.closingRefundBlock.setValue(getString(R.string.channeldetails_refund_block_value,
          NumberFormat.getInstance().format(channel.getRefundAtBlock()),
          NumberFormat.getInstance().format(Globals.blockCount().get())));
      }
      mBinding.closingRefundBlock.setVisibility(View.VISIBLE);
    }

    mBinding.nodeId.setValue(channel.getPeerNodeId());
    mBinding.channelId.setValue(channel.getChannelId());
    mBinding.shortChannelId.setValue(channel.getShortChannelId());
    if (channel.getLocalFeatures() != null) {
      final BinaryData localFeatures = BinaryData.apply(channel.getLocalFeatures());
      mBinding.setHasAdvancedRoutingSync(
        Features.hasFeature(localFeatures, Features.CHANNEL_RANGE_QUERIES_BIT_OPTIONAL())
          || Features.hasFeature(localFeatures, Features.CHANNEL_RANGE_QUERIES_BIT_MANDATORY()));
      mBinding.setHasDataLossProtection(Features.hasFeature(localFeatures, Features.OPTION_DATA_LOSS_PROTECT_OPTIONAL()));
    }
    mBinding.toSelfDelay.setValue(getString(R.string.channeldetails_delay_value, channel.getToSelfDelayBlocks()));
    mBinding.remoteToSelfDelay.setValue(getString(R.string.channeldetails_delay_value, channel.remoteToSelfDelayBlocks));
    mBinding.reserve.setValue(CoinUtils.formatAmountInUnit(new Satoshi(channel.getChannelReserveSat()), prefUnit, true));
    mBinding.countHtlcsInflight.setValue(String.valueOf(channel.htlcsInFlightCount));
    mBinding.minimumHtlcAmount.setValue(CoinUtils.formatAmountInUnit(new MilliSatoshi(channel.getMinimumHtlcAmountMsat()), prefUnit, true));
    if (Strings.isNullOrEmpty(channel.getFundingTxId())) {
      mBinding.transactionId.setVisibility(View.GONE);
    } else {
      mBinding.transactionId.setValue(channel.getFundingTxId());
      mBinding.transactionId.actionButton.setOnClickListener(WalletUtils.getOpenTxListener(channel.getFundingTxId()));
    }
  }

  private void openRawDataWindow() {
    Intent intent = new Intent(getApplicationContext(), ChannelRawDataActivity.class);
    intent.putExtra(LocalChannelItemHolder.EXTRA_CHANNEL_ID, this.mChannelId);
    startActivity(intent);
  }
}
