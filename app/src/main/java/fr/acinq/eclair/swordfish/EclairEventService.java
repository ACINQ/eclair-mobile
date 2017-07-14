package fr.acinq.eclair.swordfish;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.channel.CLOSING;
import fr.acinq.eclair.channel.ChannelCreated;
import fr.acinq.eclair.channel.ChannelIdAssigned;
import fr.acinq.eclair.channel.ChannelRestored;
import fr.acinq.eclair.channel.ChannelSignatureReceived;
import fr.acinq.eclair.channel.ChannelStateChanged;
import fr.acinq.eclair.channel.DATA_CLOSING;
import fr.acinq.eclair.channel.HasCommitments;
import fr.acinq.eclair.channel.NORMAL;
import fr.acinq.eclair.channel.OFFLINE;
import fr.acinq.eclair.payment.PaymentSent;
import fr.acinq.eclair.router.ChannelDiscovered;
import fr.acinq.eclair.router.ChannelLost;
import fr.acinq.eclair.router.NodeDiscovered;
import fr.acinq.eclair.router.NodeLost;
import fr.acinq.eclair.swordfish.events.ChannelUpdateEvent;
import fr.acinq.eclair.swordfish.events.LNBalanceUpdateEvent;
import fr.acinq.eclair.swordfish.events.LNPaymentEvent;
import fr.acinq.eclair.swordfish.events.NetworkAnnouncementEvent;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.model.PaymentTypes;
import fr.acinq.eclair.wire.ChannelAnnouncement;
import fr.acinq.eclair.wire.NodeAnnouncement;

public class EclairEventService extends UntypedActor {

  private static final String TAG = "EclairEventService";
  public static Map<Crypto.PublicKey, NodeAnnouncement> nodeAnnouncementMap = new ConcurrentHashMap<>();
  public static Map<Long, ChannelAnnouncement> channelAnnouncementMap = new ConcurrentHashMap<>();
  private static Map<ActorRef, ChannelDetails> channelDetailsMap = new ConcurrentHashMap<>();

  public static Map<ActorRef, ChannelDetails> getChannelsMap() {
    return channelDetailsMap;
  }

  public static void postLNBalanceEvent() {
    long availableTotal = 0;
    long pendingTotal = 0;
    long offlineTotal = 0;
    long closingTotal = 0;
    for (ChannelDetails d : channelDetailsMap.values()) {
      if (NORMAL.toString().equals(d.state)) {
        availableTotal += d.balanceMsat.amount();
      } else if (CLOSING.toString().equals(d.state)) {
        closingTotal += d.balanceMsat.amount();
      } else if (OFFLINE.toString().equals(d.state)) {
        offlineTotal += d.balanceMsat.amount();
      } else {
        pendingTotal += d.balanceMsat.amount();
      }
    }
    EventBus.getDefault().postSticky(new LNBalanceUpdateEvent(availableTotal, pendingTotal, offlineTotal, closingTotal));
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
      cd.balanceMsat = new MilliSatoshi(csr.Commitments().localCommit().spec().toLocalMsat());
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
      cd.state = cs.currentState().toString();
      if (cs.currentData() instanceof DATA_CLOSING) {
        DATA_CLOSING d = (DATA_CLOSING) cs.currentData();
        // cooperative closing if publish is only mutual
        cd.isCooperativeClosing = d.mutualClosePublished().isDefined() && !d.localCommitPublished().isDefined()
          && !d.remoteCommitPublished().isDefined() && d.revokedCommitPublished().isEmpty();
      }
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
      Payment paymentInDB = Payment.getPayment(paymentEvent.paymentHash().toString(), PaymentTypes.LN);
      if (paymentInDB == null) {
        Log.d(TAG, "Received an unknown PaymentSent event. Ignoring");
      } else {
        paymentInDB.amountPaidMsat = paymentEvent.amount().amount();
        paymentInDB.feesPaidMsat = paymentEvent.feesPaid().amount();
        paymentInDB.updated = new Date();
        paymentInDB.status = "PAID";
        paymentInDB.save();
        EventBus.getDefault().post(new LNPaymentEvent(paymentInDB));
      }
    }
    // ---- announcement events
    else if (message instanceof NodeDiscovered) {
      NodeAnnouncement na = ((NodeDiscovered) message).ann();
      nodeAnnouncementMap.put(na.nodeId(), na);
      EventBus.getDefault().post(new NetworkAnnouncementEvent());
    } else if (message instanceof NodeLost) {
      BinaryData nodeId = ((NodeLost) message).nodeId();
      nodeAnnouncementMap.remove(nodeId);
      EventBus.getDefault().post(new NetworkAnnouncementEvent());
    } else if (message instanceof ChannelDiscovered) {
      ChannelAnnouncement ca = ((ChannelDiscovered) message).ann();
      channelAnnouncementMap.put(ca.shortChannelId(), ca);
      EventBus.getDefault().post(new NetworkAnnouncementEvent());
    } else if (message instanceof ChannelLost) {
      long channelId = ((ChannelLost) message).channelId();
      channelAnnouncementMap.remove(channelId);
      EventBus.getDefault().post(new NetworkAnnouncementEvent());
    }
  }

  public static class ChannelDetails {
    public MilliSatoshi balanceMsat = new MilliSatoshi(0);
    public MilliSatoshi capacityMsat = new MilliSatoshi(0);
    public String channelId;
    public String state;
    public Boolean isCooperativeClosing;
    public String remoteNodeId;
    public String transactionId;
  }
}
