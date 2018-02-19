package fr.acinq.eclair.wallet;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.channel.CLOSED;
import fr.acinq.eclair.channel.CLOSED$;
import fr.acinq.eclair.channel.CLOSING;
import fr.acinq.eclair.channel.CLOSING$;
import fr.acinq.eclair.channel.ChannelCreated;
import fr.acinq.eclair.channel.ChannelIdAssigned;
import fr.acinq.eclair.channel.ChannelRestored;
import fr.acinq.eclair.channel.ChannelSignatureReceived;
import fr.acinq.eclair.channel.ChannelStateChanged;
import fr.acinq.eclair.channel.DATA_CLOSING;
import fr.acinq.eclair.channel.HasCommitments;
import fr.acinq.eclair.channel.NORMAL;
import fr.acinq.eclair.channel.OFFLINE;
import fr.acinq.eclair.channel.OFFLINE$;
import fr.acinq.eclair.channel.WAIT_FOR_INIT_INTERNAL;
import fr.acinq.eclair.channel.WAIT_FOR_INIT_INTERNAL$;
import fr.acinq.eclair.payment.PaymentSent;
import fr.acinq.eclair.router.NORMAL$;
import fr.acinq.eclair.transactions.DirectedHtlc;
import fr.acinq.eclair.transactions.OUT$;
import fr.acinq.eclair.wallet.events.ChannelUpdateEvent;
import fr.acinq.eclair.wallet.events.LNBalanceUpdateEvent;
import fr.acinq.eclair.wallet.events.LNPaymentEvent;
import fr.acinq.eclair.wallet.events.NotificationEvent;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import scala.collection.Iterator;

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

  public static MilliSatoshi getBalanceMsatOf(String channelId) {
    return channelDetailsMap.get(channelId).balanceMsat;
  }

  public static MilliSatoshi getCapacityMsatOf(String channelId) {
    return channelDetailsMap.get(channelId).capacityMsat;
  }

  private static ChannelDetails getChannelDetails(ActorRef ref) {
    return channelDetailsMap.containsKey(ref) ? channelDetailsMap.get(ref) : new ChannelDetails();
  }

  @Override
  public void onReceive(final Object message) {
    Log.d(TAG, "######## Event: " + message);
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
      cd.balanceMsat = new MilliSatoshi(cr.currentData().commitments().localCommit().spec().toLocalMsat());
      cd.capacityMsat = new MilliSatoshi(cr.currentData().commitments().localCommit().spec().totalFunds());
      cd.transactionId = cr.currentData().commitments().commitInput().outPoint().txid().toString();
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
    // ---- balance update
    else if (message instanceof ChannelSignatureReceived && channelDetailsMap.containsKey(((ChannelSignatureReceived) message).channel())) {
      ChannelSignatureReceived csr = (ChannelSignatureReceived) message;
      ChannelDetails cd = channelDetailsMap.get(csr.channel());
      long outHtlcsAmount = 0L;
      Iterator<DirectedHtlc> htlcsIterator = csr.Commitments().localCommit().spec().htlcs().iterator();
      while (htlcsIterator.hasNext()) {
        DirectedHtlc h = htlcsIterator.next();
        if (h.direction() instanceof OUT$) {
          outHtlcsAmount += h.add().amountMsat();
        }
      }
      cd.balanceMsat = new MilliSatoshi(csr.Commitments().localCommit().spec().toLocalMsat() + outHtlcsAmount);
      cd.capacityMsat = new MilliSatoshi(csr.Commitments().localCommit().spec().totalFunds());
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
        if (cd.state != null && !CLOSED$.MODULE$.toString().equals(cs.currentState().toString()) && !WAIT_FOR_INIT_INTERNAL$.MODULE$.toString().equals(cd.state)) {
          Log.d(TAG, "########## CLOSING => from " + cd.state + " to " + cs.currentState().toString());
          String notifTitle = "Closing channel with " + cd.remoteNodeId.substring(0, 7) + "...";
          final String notifMessage = "Your final balance: " + CoinUtils.formatAmountMilliBtc(new MilliSatoshi(d.commitments().localCommit().spec().toLocalMsat())) + " mBTC";
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
        cd.transactionId = ((HasCommitments) cs.currentData()).commitments().commitInput().outPoint().txid().toString();
      }
      channelDetailsMap.put(cs.channel(), cd);
      Log.d(TAG, "Channel " + cd.channelId + " changed state to " + cs.currentState());
      EventBus.getDefault().post(new ChannelUpdateEvent());
      postLNBalanceEvent();
    }
    // ---- events that update payments status
    else if (message instanceof PaymentSent) {
      PaymentSent paymentEvent = (PaymentSent) message;
      Payment paymentInDB = dbHelper.getPayment(paymentEvent.paymentHash().toString(), PaymentType.BTC_LN);
      if (paymentInDB == null) {
        Log.d(TAG, "Received an unknown PaymentSent event. Ignoring");
      } else {
        dbHelper.updatePaymentPaid(paymentInDB, paymentEvent.amount().amount() + paymentEvent.feesPaid().amount(),
          paymentEvent.feesPaid().amount(), paymentEvent.paymentPreimage().toString());
        EventBus.getDefault().post(new LNPaymentEvent(paymentInDB));
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
