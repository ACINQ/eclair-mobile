package fr.acinq.eclair.swordfish;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.channel.ChannelCreated;
import fr.acinq.eclair.channel.ChannelRestored;
import fr.acinq.eclair.channel.ChannelSignatureReceived;
import fr.acinq.eclair.channel.ChannelStateChanged;
import fr.acinq.eclair.channel.NORMAL;
import fr.acinq.eclair.channel.OFFLINE;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.PaymentSent;
import fr.acinq.eclair.router.ChannelDiscovered;
import fr.acinq.eclair.router.ChannelLost;
import fr.acinq.eclair.router.NodeDiscovered;
import fr.acinq.eclair.router.NodeLost;
import fr.acinq.eclair.swordfish.events.BalanceUpdateEvent;
import fr.acinq.eclair.swordfish.events.ChannelUpdateEvent;
import fr.acinq.eclair.swordfish.events.NetworkAnnouncementEvent;
import fr.acinq.eclair.swordfish.events.SWPaymentEvent;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.wire.ChannelAnnouncement;
import fr.acinq.eclair.wire.NodeAnnouncement;

public class EclairEventService extends UntypedActor {

  private static final String TAG = "EclairEventService";
  public static Map<BinaryData, NodeAnnouncement> nodeAnnouncementMap = new ConcurrentHashMap<>();
  public static Map<Long, ChannelAnnouncement> channelAnnouncementMap = new ConcurrentHashMap<>();
  private static Map<ActorRef, ChannelDetails> channelDetailsMap = new ConcurrentHashMap<>();

  public static Map<ActorRef, ChannelDetails> getChannelsMap() {
    return channelDetailsMap;
  }

  public static BalanceUpdateEvent aggregateBalanceForEvent() {
    long availableTotal = 0;
    long pendingTotal = 0;
    long offlineTotal = 0;
    for (ChannelDetails d : channelDetailsMap.values()) {
      if (NORMAL.toString().equals(d.state)) {
        availableTotal += d.balanceSat;
      } else if (OFFLINE.toString().equals(d.state)) {
        offlineTotal += d.balanceSat;
      } else {
        pendingTotal += d.balanceSat;
      }
    }
    return new BalanceUpdateEvent(availableTotal, pendingTotal, offlineTotal);
  }

  public static long getBalanceOf(String channelId) {
    return channelDetailsMap.get(channelId).balanceSat;
  }

  public static long getCapacityOf(String channelId) {
    return channelDetailsMap.get(channelId).capacitySat;
  }

  private static ChannelDetails getChannelDetails(ActorRef ref) {
    return channelDetailsMap.containsKey(ref) ? channelDetailsMap.get(ref) : new ChannelDetails();
  }

  @Override
  public void onReceive(final Object message) {
    Log.d(TAG, "######## Event: " + message);

    if (message instanceof ChannelCreated) {
      ChannelCreated cr = (ChannelCreated) message;
      ChannelDetails cd = getChannelDetails(cr.channel());
      cd.channelId = cr.temporaryChannelId().toString();
      cd.remoteNodeId = cr.remoteNodeId().toString();
      channelDetailsMap.put(cr.channel(), cd);
      EventBus.getDefault().post(new ChannelUpdateEvent());
    }
    // ---- events that update balance
    else if (message instanceof ChannelSignatureReceived) {
      ChannelSignatureReceived csr = (ChannelSignatureReceived) message;
      ChannelDetails cd = getChannelDetails(csr.channel());
      cd.channelId = csr.Commitments().channelId().toString();
      cd.balanceSat = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(csr.Commitments().localCommit().spec().toLocalMsat())).amount();
      cd.capacitySat = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(csr.Commitments().localCommit().spec().totalFunds())).amount();
      channelDetailsMap.put(csr.channel(), cd);
      EventBus.getDefault().post(new ChannelUpdateEvent());
      EventBus.getDefault().post(aggregateBalanceForEvent());
    } else if (message instanceof ChannelRestored) {
      ChannelRestored cr = (ChannelRestored) message;
      ChannelDetails cd = getChannelDetails(cr.channel());
      cd.channelId = cr.channelId().toString();
      cd.remoteNodeId = cr.remoteNodeId().toString();
      cd.balanceSat = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(cr.currentData().commitments().localCommit().spec().toLocalMsat())).amount();
      cd.capacitySat = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(cr.currentData().commitments().localCommit().spec().totalFunds())).amount();
      cd.remoteNodeId = cr.remoteNodeId().toString();
      channelDetailsMap.put(cr.channel(), cd);
      EventBus.getDefault().post(new ChannelUpdateEvent());
      EventBus.getDefault().post(aggregateBalanceForEvent());
    }
    // ---- channel has been terminated
    else if (message instanceof Terminated) {
      channelDetailsMap.remove(((Terminated) message).getActor());
      EventBus.getDefault().post(new ChannelUpdateEvent());
    }
    // ---- channel state changed
    else if (message instanceof ChannelStateChanged) {
      ChannelStateChanged cs = (ChannelStateChanged) message;
      ChannelDetails cd = getChannelDetails(cs.channel());
      cd.state = cs.currentState().toString();
      Log.d(TAG, "Channel " + cd.channelId + " changed state to " + cs.currentState());
      channelDetailsMap.put(cs.channel(), cd);
      EventBus.getDefault().post(new ChannelUpdateEvent());
      // also post balance event because a change in the  state of the channel matters to the balance
      EventBus.getDefault().post(aggregateBalanceForEvent());
    }
    // ---- events that update payments status
    else if (message instanceof PaymentSent) {
      PaymentSent paymentEvent = (PaymentSent) message;
      List<Payment> paymentList = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment WHERE payment_hash = ? LIMIT 1", paymentEvent.paymentHash().toString());
      if (paymentList.isEmpty()) {
        Log.d(TAG, "Received an unknown PaymentSent event. Ignoring");
      } else {
        Payment paymentInDB = paymentList.get(0);
        paymentInDB.amountPaid = Long.toString(paymentEvent.amount().amount());
        paymentInDB.feesPaid = Long.toString(paymentEvent.feesPaid().amount());
        paymentInDB.updated = new Date();
        paymentInDB.status = "PAID";
        paymentInDB.save();
        EventBus.getDefault().post(new SWPaymentEvent(PaymentRequest.read(paymentInDB.paymentRequest)));
      }
    }
    // ---- announcement events
    else if (message instanceof NodeDiscovered) {
      NodeAnnouncement na = ((NodeDiscovered) message).ann();
      Log.i(TAG, "Node Discovered: " + na.nodeId().toString());
      nodeAnnouncementMap.put(na.nodeId(), na);
      EventBus.getDefault().post(new NetworkAnnouncementEvent());
    } else if (message instanceof NodeLost) {
      BinaryData nodeId = ((NodeLost) message).nodeId();
      nodeAnnouncementMap.remove(nodeId);
      EventBus.getDefault().post(new NetworkAnnouncementEvent());
    } else if (message instanceof ChannelDiscovered) {
      ChannelAnnouncement ca = ((ChannelDiscovered) message).ann();
      Log.i(TAG, "Channel Discovered: " + ca.shortChannelId());
      channelAnnouncementMap.put(ca.shortChannelId(), ca);
      EventBus.getDefault().post(new NetworkAnnouncementEvent());
    } else if (message instanceof ChannelLost) {
      long channelId = ((ChannelLost) message).channelId();
      channelAnnouncementMap.remove(channelId);
      EventBus.getDefault().post(new NetworkAnnouncementEvent());
    }
  }

  public static class ChannelDetails {
    public long balanceSat = 0;
    public long capacitySat = 0;
    public String channelId;
    public String state;
    public String remoteNodeId;
  }
}
