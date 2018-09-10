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

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.channel.CLOSED$;
import fr.acinq.eclair.channel.CLOSING$;
import fr.acinq.eclair.channel.Channel;
import fr.acinq.eclair.channel.ChannelCreated;
import fr.acinq.eclair.channel.ChannelFailed;
import fr.acinq.eclair.channel.ChannelIdAssigned;
import fr.acinq.eclair.channel.ChannelPersisted;
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
import fr.acinq.eclair.channel.ShortChannelIdAssigned;
import fr.acinq.eclair.channel.WAIT_FOR_INIT_INTERNAL$;
import fr.acinq.eclair.channel.WaitingForRevocation;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.router.NORMAL$;
import fr.acinq.eclair.router.SyncProgress;
import fr.acinq.eclair.transactions.DirectedHtlc;
import fr.acinq.eclair.transactions.OUT$;
import fr.acinq.eclair.wallet.events.ChannelUpdateEvent;
import fr.acinq.eclair.wallet.events.ClosingChannelNotificationEvent;
import fr.acinq.eclair.wallet.events.LNBalanceUpdateEvent;
import fr.acinq.eclair.wallet.events.LNPaymentFailedEvent;
import fr.acinq.eclair.wallet.events.LNPaymentSuccessEvent;
import fr.acinq.eclair.wallet.events.PaymentEvent;
import fr.acinq.eclair.wallet.models.ClosingType;
import fr.acinq.eclair.wallet.models.LightningPaymentError;
import fr.acinq.eclair.wallet.models.LocalChannel;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.collection.Iterator;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.util.Either;

/**
 * This actor handles the messages sent by the Eclair node.
 */
public class EclairEventService extends UntypedActor {

  private DBHelper dbHelper;
  private OneTimeWorkRequest channelsBackupWork;

  public EclairEventService(final DBHelper dbHelper, final String seedHash, final BinaryData backupKey) {
    this.dbHelper = dbHelper;
    this.channelsBackupWork = WalletUtils.generateBackupRequest(seedHash, backupKey);
  }

  private static final String TAG = "EclairEventService";
  private static Map<ActorRef, LocalChannel> activeChannelsMap = new ConcurrentHashMap<>();

  public static Map<ActorRef, LocalChannel> getChannelsMap() {
    return activeChannelsMap;
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
    for (LocalChannel c : activeChannelsMap.values()) {
      if (NORMAL$.MODULE$.toString().equals(c.state)) {
        availableTotal += c.getBalanceMsat();
      } else if (CLOSED$.MODULE$.toString().equals(c.state)) {
        // closed channel balance is ignored
      } else if (CLOSING$.MODULE$.toString().equals(c.state)) {
        closingTotal += c.getBalanceMsat();
      } else if (OFFLINE$.MODULE$.toString().equals(c.state)) {
        offlineTotal += c.getBalanceMsat();
      } else if (c.state == null || c.state.startsWith("ERR_")) {
        ignoredBalanceMsat += c.getBalanceMsat();
      } else {
        pendingTotal += c.getBalanceMsat();
      }
    }
    EventBus.getDefault().postSticky(new LNBalanceUpdateEvent(availableTotal, pendingTotal, offlineTotal, closingTotal, ignoredBalanceMsat));
  }

  private static LocalChannel getChannel(ActorRef ref) {
    return activeChannelsMap.containsKey(ref) ? activeChannelsMap.get(ref) : new LocalChannel();
  }

  @Override
  public void onReceive(final Object message) {
    if (message instanceof ChannelCreated) {
      final ChannelCreated event = (ChannelCreated) message;
      final LocalChannel c = getChannel(event.channel());
      c.setChannelId(event.temporaryChannelId().toString());
      c.setPeerNodeId(event.remoteNodeId().toString());
      activeChannelsMap.put(event.channel(), c);
      context().watch(event.channel());
      EventBus.getDefault().post(new ChannelUpdateEvent());
    } else if (message instanceof ChannelRestored) {
      final ChannelRestored event = (ChannelRestored) message;
      final LocalChannel c = getChannel(event.channel());
      c.setChannelId(event.channelId().toString());
      c.setPeerNodeId(event.remoteNodeId().toString());
      activeChannelsMap.put(event.channel(), c);
      context().watch(event.channel());
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- channel id assigned
    else if (message instanceof ChannelIdAssigned && activeChannelsMap.containsKey(((ChannelIdAssigned) message).channel())) {
      final ChannelIdAssigned event = (ChannelIdAssigned) message;
      final LocalChannel c = getChannel(event.channel());
      c.setChannelId(event.channelId().toString());
      dbHelper.saveLocalChannel(c);
    } else if (message instanceof ShortChannelIdAssigned && activeChannelsMap.containsKey(((ShortChannelIdAssigned) message).channel())) {
      final ShortChannelIdAssigned event = (ShortChannelIdAssigned) message;
      final LocalChannel c = getChannel(event.channel());
      c.setShortChannelId(event.shortChannelId().toString());
    }
    // ---- we sent a channel sig => update corresponding payment to PENDING in app's DB
    else if (message instanceof ChannelSignatureSent) {
      final ChannelSignatureSent sigSent = (ChannelSignatureSent) message;
      final Either<WaitingForRevocation, Crypto.Point> nextCommitInfo = sigSent.commitments().remoteNextCommitInfo();
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
      }
    }
    // ---- balance update, only for the channels we know
    else if (message instanceof ChannelSignatureReceived && activeChannelsMap.containsKey(((ChannelSignatureReceived) message).channel())) {
      final ChannelSignatureReceived event = (ChannelSignatureReceived) message;
      final LocalChannel c = getChannel(event.channel());
      final LocalCommit localCommit = event.commitments().localCommit();
      final Iterator<DirectedHtlc> htlcsIterator = localCommit.spec().htlcs().iterator();

      long outHtlcsAmount = 0L;
      int htlcsCount = 0;
      while (htlcsIterator.hasNext()) {
        DirectedHtlc h = htlcsIterator.next();
        if (h.direction() instanceof OUT$) {
          htlcsCount++;
          outHtlcsAmount += h.add().amountMsat();
        }
      }

      c.setChannelReserveSat(event.commitments().localParams().channelReserveSatoshis());
      c.setMinimumHtlcAmountMsat(event.commitments().localParams().htlcMinimumMsat());
      c.htlcsInFlightCount = htlcsCount;
      c.setBalanceMsat(localCommit.spec().toLocalMsat() + outHtlcsAmount);
      c.setCapacityMsat(localCommit.spec().totalFunds());
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- channel must be saved
    else if (message instanceof ChannelPersisted) {

      WorkManager.getInstance()
        .beginUniqueWork("ChannelsBackup", ExistingWorkPolicy.REPLACE, channelsBackupWork)
        .enqueue();

//      backupScheduler.tell(BackupScheduler.DO_BACKUP, null);
    }
    // ---- network map syncing
    else if (message instanceof SyncProgress) {
      EventBus.getDefault().post(message);
    }
    // ---- channel has been terminated
    else if (message instanceof Terminated) {
      final Terminated event = (Terminated) message;
      final LocalChannel c = activeChannelsMap.get(event.getActor());
      if (c != null) dbHelper.channelTerminated(c.getChannelId());
      activeChannelsMap.remove(event.getActor());
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- channel is in error
    else if (message instanceof ChannelFailed) {
      final ChannelFailed event = (ChannelFailed) message;
      final LocalChannel c = getChannel(event.channel());
      if (event.error() instanceof Channel.LocalError) {
        final Channel.LocalError localError = (Channel.LocalError) event.error();
        if (localError.t() != null) {
          c.setClosingErrorMessage(localError.t().getMessage());
          dbHelper.saveLocalChannel(c);
        }
      } else if (event.error() instanceof Channel.RemoteError) {
        final Channel.RemoteError remoteError = (Channel.RemoteError) event.error();
        if (fr.acinq.eclair.package$.MODULE$.isAsciiPrintable(remoteError.e().data())) {
          c.setClosingErrorMessage(WalletUtils.toAscii(remoteError.e().data()));
        } else {
          c.setClosingErrorMessage(remoteError.e().data().toString());
        }
        dbHelper.saveLocalChannel(c);
      }
    }
    // ---- channel state changed
    else if (message instanceof ChannelStateChanged) {
      final ChannelStateChanged event = (ChannelStateChanged) message;
      final LocalChannel c = getChannel(event.channel());

      if (event.currentData() instanceof DATA_CLOSING) {
        final DATA_CLOSING d = (DATA_CLOSING) event.currentData();
        if (!d.mutualClosePublished().isEmpty() && d.localCommitPublished().isEmpty()
          && d.remoteCommitPublished().isEmpty() && d.revokedCommitPublished().isEmpty()) {
          c.setClosingType(ClosingType.MUTUAL);
          c.mainClosingTxs = JavaConverters.seqAsJavaListConverter(d.mutualClosePublished()).asJava();
        } else if (d.mutualClosePublished().isEmpty() && d.localCommitPublished().isEmpty()
          && d.remoteCommitPublished().isDefined() && d.revokedCommitPublished().isEmpty()) {
          c.setClosingType(ClosingType.REMOTE);
          c.mainClosingTxs = Collections.singletonList(d.remoteCommitPublished().get().commitTx());
        } else if (d.mutualClosePublished().isEmpty() && d.localCommitPublished().isDefined()
          && d.remoteCommitPublished().isEmpty() && d.revokedCommitPublished().isEmpty()) {
          c.setClosingType(ClosingType.LOCAL);
          c.mainClosingTxs = Collections.singletonList(d.localCommitPublished().get().commitTx());
          c.mainDelayedClosingTx = d.localCommitPublished().get().claimMainDelayedOutputTx();
        } else {
          c.setClosingType(ClosingType.OTHER);
        }

        // Don't show the notification if state goes from straight from WAIT_FOR_INIT_INTERNAL to CLOSING
        // Otherwise the notification would show up each time the wallet is started and the channel is
        // still closing, even though the user has already been alerted the last time he used the app.
        // Same thing for CLOSING -> CLOSED
        if (!CLOSED$.MODULE$.toString().equals(event.currentState().toString())
          && !WAIT_FOR_INIT_INTERNAL$.MODULE$.toString().equals(event.previousState().toString())) {
          final MilliSatoshi balanceLeft = new MilliSatoshi(d.commitments().localCommit().spec().toLocalMsat());
          EventBus.getDefault().post(new ClosingChannelNotificationEvent(
            c.getChannelId(), c.getPeerNodeId(), ClosingType.LOCAL.equals(c.getClosingType()), balanceLeft, c.getToSelfDelayBlocks()));
        }
      }
      c.state = event.currentState().toString();
      if (event.currentData() instanceof HasCommitments) {
        final Commitments commitments = ((HasCommitments) event.currentData()).commitments();
        c.setLocalFeatures(commitments.remoteParams().localFeatures().toString());
        c.setToSelfDelayBlocks(commitments.remoteParams().toSelfDelay());
        c.htlcsInFlightCount = commitments.localCommit().spec().htlcs().iterator().size();
        c.setChannelReserveSat(commitments.localParams().channelReserveSatoshis());
        c.setMinimumHtlcAmountMsat(commitments.localParams().htlcMinimumMsat());
        c.setFundingTxId(commitments.commitInput().outPoint().txid().toString());
        c.setBalanceMsat(commitments.localCommit().spec().toLocalMsat());
        c.setCapacityMsat(commitments.localCommit().spec().totalFunds());
      }
      activeChannelsMap.put(event.channel(), c);
      dbHelper.saveLocalChannel(c);
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- events that update payments status
    else if (message instanceof PaymentLifecycle.PaymentFailed) {
      final PaymentLifecycle.PaymentFailed event = (PaymentLifecycle.PaymentFailed) message;
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
    } else if (message instanceof PaymentLifecycle.PaymentSucceeded) {
      final PaymentLifecycle.PaymentSucceeded event = (PaymentLifecycle.PaymentSucceeded) message;
      final Payment paymentInDB = dbHelper.getPayment(event.paymentHash().toString(), PaymentType.BTC_LN);
      if (paymentInDB != null) {
        dbHelper.updatePaymentPaid(paymentInDB, event.amountMsat(), event.amountMsat() - paymentInDB.getAmountSentMsat(), event.paymentPreimage().toString());
        EventBus.getDefault().post(new LNPaymentSuccessEvent(paymentInDB));
        EventBus.getDefault().post(new PaymentEvent());
      } else {
        Log.d(TAG, "received and ignored an unknown PaymentSucceeded event with hash=" + event.paymentHash().toString());
      }
    }
  }

  public static boolean hasActiveChannels() {
    for (LocalChannel c : activeChannelsMap.values()) {
      if (NORMAL$.MODULE$.toString().equals(c.state)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the wallet has at least 1 NORMAL channel with enough balance (including channel reserve).
   */
  public static boolean hasNormalChannelsWithBalance(final long requiredBalanceMsat) {
    for (LocalChannel d : activeChannelsMap.values()) {
      if (NORMAL$.MODULE$.toString().equals(d.state)
        & d.getBalanceMsat() > requiredBalanceMsat + package$.MODULE$.satoshi2millisatoshi(new Satoshi(d.getChannelReserveSat())).amount()) {
        return true;
      }
    }
    return false;
  }

  public static Map.Entry<ActorRef, LocalChannel> getChannelFromId(String channelId) {
    for (Map.Entry<ActorRef, LocalChannel> c : EclairEventService.getChannelsMap().entrySet()) {
      if (c.getValue().getChannelId().equals(channelId)) {
        return c;
      }
    }
    return null;
  }
}
