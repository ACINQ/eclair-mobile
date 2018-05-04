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

package fr.acinq.eclair.wallet;

import android.os.Build;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.channel.CLOSED$;
import fr.acinq.eclair.channel.CLOSING$;
import fr.acinq.eclair.channel.ChannelCreated;
import fr.acinq.eclair.channel.ChannelIdAssigned;
import fr.acinq.eclair.channel.ChannelRestored;
import fr.acinq.eclair.channel.ChannelSignatureReceived;
import fr.acinq.eclair.channel.ChannelSignatureSent;
import fr.acinq.eclair.channel.ChannelStateChanged;
import fr.acinq.eclair.channel.Commitments;
import fr.acinq.eclair.channel.DATA_CLOSING;
import fr.acinq.eclair.channel.HasCommitments;
import fr.acinq.eclair.channel.LocalCommit;
import fr.acinq.eclair.channel.OFFLINE$;
import fr.acinq.eclair.channel.RemoteCommit;
import fr.acinq.eclair.channel.WAIT_FOR_INIT_INTERNAL$;
import fr.acinq.eclair.channel.WaitingForRevocation;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.router.NORMAL$;
import fr.acinq.eclair.transactions.DirectedHtlc;
import fr.acinq.eclair.transactions.OUT$;
import fr.acinq.eclair.wallet.events.ChannelUpdateEvent;
import fr.acinq.eclair.wallet.events.LNBalanceUpdateEvent;
import fr.acinq.eclair.wallet.events.LNPaymentFailedEvent;
import fr.acinq.eclair.wallet.events.LNPaymentSuccessEvent;
import fr.acinq.eclair.wallet.events.NotificationEvent;
import fr.acinq.eclair.wallet.events.PaymentEvent;
import fr.acinq.eclair.wallet.models.LightningPaymentError;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import scala.collection.Iterator;
import scala.collection.Seq;
import scala.util.Either;

/**
 * This actor handles the messages sent by the Eclair node.
 */
public class EclairEventService extends UntypedActor {

  private DBHelper dbHelper;

  public EclairEventService(DBHelper dbHelper) {
    this.dbHelper = dbHelper;
  }

  private static final String TAG = "EclairEventService";
  private static Map<ActorRef, ChannelDetails> channelDetailsMap = new ConcurrentHashMap<>();

  public static Map<ActorRef, ChannelDetails> getChannelsMap() {
    return channelDetailsMap;
  }

  /**
   * Sends a event containing the new Lightning balance of the channels in the Eclair node.
   * The balance accounts for the state of its channel and thus can be of various type.
   */
  public static void postLNBalanceEvent() {
    long availableTotal = 0;
    long pendingTotal = 0;
    long offlineTotal = 0;
    long closingTotal = 0;
    long ignoredBalanceMsat = 0;
    for (ChannelDetails d : channelDetailsMap.values()) {
      if (NORMAL$.MODULE$.toString().equals(d.state)) {
        availableTotal += d.balanceMsat.amount();
      } else if (CLOSED$.MODULE$.toString().equals(d.state)) {
        // closed channel balance is ignored
      } else if (CLOSING$.MODULE$.toString().equals(d.state)) {
        closingTotal += d.balanceMsat.amount();
      } else if (OFFLINE$.MODULE$.toString().equals(d.state)) {
        offlineTotal += d.balanceMsat.amount();
      } else if (d.state == null || d.state.startsWith("ERR_")) {
        ignoredBalanceMsat += d.balanceMsat.amount();
      } else {
        pendingTotal += d.balanceMsat.amount();
      }
    }
    EventBus.getDefault().postSticky(new LNBalanceUpdateEvent(availableTotal, pendingTotal, offlineTotal, closingTotal, ignoredBalanceMsat));
  }

  private static ChannelDetails getChannelDetails(ActorRef ref) {
    return channelDetailsMap.containsKey(ref) ? channelDetailsMap.get(ref) : new ChannelDetails();
  }

  @Override
  public void onReceive(final Object message) {
    if (message instanceof ChannelCreated) {
      ChannelCreated cc = (ChannelCreated) message;
      ChannelDetails cd = getChannelDetails(cc.channel());
      cd.channelId = cc.temporaryChannelId().toString();
      cd.remoteNodeId = cc.remoteNodeId().toString();
      channelDetailsMap.put(cc.channel(), cd);
      context().watch(cc.channel());
      EventBus.getDefault().post(new ChannelUpdateEvent());
    } else if (message instanceof ChannelRestored) {
      ChannelRestored cr = (ChannelRestored) message;
      ChannelDetails cd = getChannelDetails(cr.channel());
      cd.channelId = cr.channelId().toString();
      cd.remoteNodeId = cr.remoteNodeId().toString();
      channelDetailsMap.put(cr.channel(), cd);
      context().watch(cr.channel());
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- channel id assigned
    else if (message instanceof ChannelIdAssigned && channelDetailsMap.containsKey(((ChannelIdAssigned) message).channel())) {
      ChannelIdAssigned cia = (ChannelIdAssigned) message;
      ChannelDetails cd = channelDetailsMap.get(cia.channel());
      cd.channelId = cia.channelId().toString();
      EventBus.getDefault().post(new ChannelUpdateEvent());
    }
    // ---- we sent a channel sig => update corresponding payment to PENDING in app's DB
    else if (message instanceof ChannelSignatureSent) {
      ChannelSignatureSent sigSent = (ChannelSignatureSent) message;
      Either<WaitingForRevocation, Crypto.Point> nextCommitInfo = sigSent.commitments().remoteNextCommitInfo();
      if (nextCommitInfo.isLeft()) {
        RemoteCommit commit = nextCommitInfo.left().get().nextRemoteCommit();
        Iterator<DirectedHtlc> htlcsIterator = commit.spec().htlcs().iterator();
        while (htlcsIterator.hasNext()) {
          DirectedHtlc h = htlcsIterator.next();
          String htlcPaymentHash = h.add().paymentHash().toString();
          Payment p = dbHelper.getPayment(htlcPaymentHash, PaymentType.BTC_LN);
          if (p != null) {
            // regular case: we know this payment hash
            if (p.getStatus() == PaymentStatus.INIT) {
              dbHelper.updatePaymentPending(p);
              EventBus.getDefault().post(new PaymentEvent());
            }
          } else {
            // rare case: an htlc is sent without the app knowing its payment hash
            // this can happen if the app could not save the payment into its own payments DB
            // we don't know much about this htlc, except that it was sent and will affects the channel's balance
            p = new Payment();
            p.setType(PaymentType.BTC_LN);
            p.setDirection(PaymentDirection.SENT);
            p.setReference(htlcPaymentHash);
            p.setAmountPaidMsat(h.add().amountMsat());
            p.setRecipient("unknown recipient");
            p.setPaymentRequest("unknown invoice");
            p.setStatus(PaymentStatus.PENDING);
            p.setUpdated(new Date());
            dbHelper.insertOrUpdatePayment(p);
            EventBus.getDefault().post(new PaymentEvent());
          }
        }
      } else {
        // do nothing
      }
    }
    // ---- balance update, only for the channels we know
    else if (message instanceof ChannelSignatureReceived && channelDetailsMap.containsKey(((ChannelSignatureReceived) message).channel())) {
      ChannelSignatureReceived csr = (ChannelSignatureReceived) message;
      ChannelDetails cd = channelDetailsMap.get(csr.channel());
      LocalCommit localCommit = csr.commitments().localCommit();
      long outHtlcsAmount = 0L;
      Iterator<DirectedHtlc> htlcsIterator = localCommit.spec().htlcs().iterator();
      while (htlcsIterator.hasNext()) {
        DirectedHtlc h = htlcsIterator.next();
        if (h.direction() instanceof OUT$) {
          outHtlcsAmount += h.add().amountMsat();
        }
      }
      cd.channelReserveSat = csr.commitments().localParams().channelReserveSatoshis();
      cd.minimumHtlcAmountMsat = csr.commitments().localParams().htlcMinimumMsat();
      cd.htlcsInFlightCount = htlcsIterator.size();
      cd.balanceMsat = new MilliSatoshi(localCommit.spec().toLocalMsat() + outHtlcsAmount);
      cd.capacityMsat = new MilliSatoshi(localCommit.spec().totalFunds());
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- channel has been terminated
    else if (message instanceof Terminated) {
      channelDetailsMap.remove(((Terminated) message).getActor());
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- channel state changed
    else if (message instanceof ChannelStateChanged) {
      ChannelStateChanged cs = (ChannelStateChanged) message;
      ChannelDetails cd = getChannelDetails(cs.channel());

      if (cs.currentData() instanceof DATA_CLOSING) {
        DATA_CLOSING d = (DATA_CLOSING) cs.currentData();
        // cooperative closing if publish is only mutual
        cd.isCooperativeClosing = !d.mutualClosePublished().isEmpty() && d.localCommitPublished().isEmpty()
          && d.remoteCommitPublished().isEmpty() && d.revokedCommitPublished().isEmpty();
        cd.isRemoteClosing = d.mutualClosePublished().isEmpty() && d.localCommitPublished().isEmpty()
          && d.remoteCommitPublished().isDefined() && d.revokedCommitPublished().isEmpty();
        // local close are delayed by 144 blocks
        cd.isLocalClosing = d.mutualClosePublished().isEmpty() && d.localCommitPublished().isDefined()
          && d.remoteCommitPublished().isEmpty() && d.revokedCommitPublished().isEmpty();

        // Don't show the notification if state goes from straight from WAIT_FOR_INIT_INTERNAL to CLOSING
        // Otherwise the notification would show up each time the wallet is started and the channel is
        // still closing, even though the user has already been alerted the last time he used the app.
        // Same thing for CLOSING -> CLOSED
        if (Build.VERSION.SDK_INT < 26 && cd.state != null && !CLOSED$.MODULE$.toString().equals(cs.currentState().toString()) && !WAIT_FOR_INIT_INTERNAL$.MODULE$.toString().equals(cd.state)) {
          String notifTitle = "Closing channel with " + cd.remoteNodeId.substring(0, 7) + "...";
          MilliSatoshi balanceLeft = new MilliSatoshi(d.commitments().localCommit().spec().toLocalMsat());
          final String notifMessage = "Your final balance: " + CoinUtils.formatAmountInUnit(balanceLeft, CoinUtils.getUnitFromString("btc"), true);
          final String notifBigMessage = notifMessage +
            "\n" + (cd.isLocalClosing
              ? "You unilaterally closed this channel. You will receive your funds in " + d.commitments().localParams().toSelfDelay() + " blocks."
              : "You should see an incoming onchain transaction.");
          EventBus.getDefault().post(new NotificationEvent(
            NotificationEvent.NOTIF_CHANNEL_CLOSED_ID, cd.channelId, notifTitle, notifMessage, notifBigMessage));
        }
      }
      cd.state = cs.currentState().toString();
      if (cs.currentData() instanceof HasCommitments) {
        Commitments commitments = ((HasCommitments) cs.currentData()).commitments();
        cd.toSelfDelayBlocks = commitments.remoteParams().toSelfDelay();
        cd.htlcsInFlightCount = commitments.localCommit().spec().htlcs().iterator().size();
        cd.channelReserveSat = commitments.localParams().channelReserveSatoshis();
        cd.minimumHtlcAmountMsat = commitments.localParams().htlcMinimumMsat();
        cd.transactionId = commitments.commitInput().outPoint().txid().toString();
        cd.balanceMsat = new MilliSatoshi(commitments.localCommit().spec().toLocalMsat());
        cd.capacityMsat = new MilliSatoshi(commitments.localCommit().spec().totalFunds());
      }
      channelDetailsMap.put(cs.channel(), cd);
      Log.d(TAG, "Channel " + cd.channelId + " changed state to " + cs.currentState());
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- events that update payments status
    else if (message instanceof PaymentLifecycle.PaymentFailed) {
      PaymentLifecycle.PaymentFailed event = (PaymentLifecycle.PaymentFailed) message;
      final Payment paymentInDB = dbHelper.getPayment(event.paymentHash().toString(), PaymentType.BTC_LN);
      if (paymentInDB != null) {
        dbHelper.updatePaymentFailed(paymentInDB);
        // extract failure cause to generate a pretty error message
        final ArrayList<LightningPaymentError> errorList = new ArrayList<>();
        final Seq<PaymentLifecycle.PaymentFailure> failures = PaymentLifecycle.transformForUser(event.failures());
        if (failures.size() > 0) {
          for (int i = 0; i < failures.size(); i++) {
            errorList.add(LightningPaymentError.generateDetailedErrorCause(failures.apply(i)));
          }
        }
        EventBus.getDefault().post(new LNPaymentFailedEvent(paymentInDB.getReference(), paymentInDB.getDescription(), false, null, errorList));
        EventBus.getDefault().post(new PaymentEvent());
      } else {
        Log.d(TAG, "received and ignored an unknown PaymentFailed event with hash=" + event.paymentHash().toString());
      }
    }
    else if (message instanceof PaymentLifecycle.PaymentSucceeded) {
      PaymentLifecycle.PaymentSucceeded event = (PaymentLifecycle.PaymentSucceeded) message;
      Payment paymentInDB = dbHelper.getPayment(event.paymentHash().toString(), PaymentType.BTC_LN);
      if (paymentInDB != null) {
        dbHelper.updatePaymentPaid(paymentInDB, event.amountMsat(), event.amountMsat() - paymentInDB.getAmountSentMsat(), event.paymentPreimage().toString());
        EventBus.getDefault().post(new LNPaymentSuccessEvent(paymentInDB));
        EventBus.getDefault().post(new PaymentEvent());
      } else {
        Log.d(TAG, "received and ignored an unknown PaymentSucceeded event with hash=" + event.paymentHash().toString());
      }
    }
  }

  public static class ChannelDetails {
    public MilliSatoshi balanceMsat = new MilliSatoshi(0);
    public MilliSatoshi capacityMsat = new MilliSatoshi(0);
    public String channelId;
    public String state;
    public Boolean isCooperativeClosing;
    public Boolean isRemoteClosing;
    public Boolean isLocalClosing;
    public String remoteNodeId;
    public String transactionId;
    public long channelReserveSat = 0;
    public long minimumHtlcAmountMsat = 0;
    public int toSelfDelayBlocks = 0;
    public int htlcsInFlightCount = 0;
  }

  public static boolean hasActiveChannels () {
    for (ChannelDetails d : channelDetailsMap.values()) {
      if (NORMAL$.MODULE$.toString().equals(d.state)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the wallet has at least 1 NORMAL channel with enough balance. An arbitrary 5k satoshis = 0.05mBTC reserve is required for now.
   * TODO: pull the real reserve from the eclair configuration
   *
   * @param requiredBalanceMsat
   * @return
   */
  public static boolean hasActiveChannelsWithBalance (long requiredBalanceMsat) {
    for (ChannelDetails d : channelDetailsMap.values()) {
      if (NORMAL$.MODULE$.toString().equals(d.state) && d.balanceMsat.amount() > requiredBalanceMsat + 5000000) {
        return true;
      }
    }
    return false;
  }
}
