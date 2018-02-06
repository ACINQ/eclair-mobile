package fr.acinq.eclair.wallet.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.channel.ChannelException;
import fr.acinq.eclair.payment.LocalFailure;
import fr.acinq.eclair.payment.PaymentFailure;
import fr.acinq.eclair.payment.RemoteFailure;
import fr.acinq.eclair.router.Hop;
import fr.acinq.eclair.router.RouteNotFound$;

/**
 * Wraps information about a failed lightning payment returned by eclair-core. Implements Parcelable
 * so that it can be passed between activities with intents.
 * <ul>
 * <li>The <b>type</b> field tells if this error is a remote or a local failure.
 * <li>The <b>cause</b> field contains a message detailing the reason of this failure.
 * <li>The <b>origin</b> field should be the id of the node from which this error comes from. May be null (for local failure).
 * <li>The <b>originChannelId</b> is the id of the channel which rejected the payment. May be null.
 * <li>The <b>hops</b> field is set only for the remote failures and describes the route used by the failed payment.
 */
public class LightningPaymentError implements Parcelable {

  public static final Creator<LightningPaymentError> CREATOR = new Creator<LightningPaymentError>() {
    @Override
    public LightningPaymentError createFromParcel(Parcel in) {
      return new LightningPaymentError(in);
    }

    @Override
    public LightningPaymentError[] newArray(int size) {
      return new LightningPaymentError[size];
    }
  };

  private String type;
  private String cause;
  private String origin;
  private String originChannelId;
  private List<String> hops;

  public LightningPaymentError(String type, String cause, String origin, String originChannelId, List<String> hops) {
    this.type = type;
    this.cause = cause;
    this.origin = origin;
    this.originChannelId = originChannelId;
    this.hops = hops == null ? new ArrayList<String>() : hops;
  }

  public LightningPaymentError(Parcel in) {
    type = in.readString();
    cause = in.readString();
    origin = in.readString();
    originChannelId = in.readString();
    List<String> h = new ArrayList<>();
    in.readList(h, List.class.getClassLoader());
  }

  /**
   * Parses a {@link PaymentFailure} sent by eclair core and generates a {@link LightningPaymentError}.
   * According to the failure type, the resulting error may contain a list of the nodes in the failed route.
   * The type of the error is always set, as well as the cause, be it unknown.
   *
   * @param failure failure in the payment route
   * @return
   */
  public static LightningPaymentError generateDetailedErrorCause(final PaymentFailure failure) {
    if (failure instanceof RemoteFailure) {
      final RemoteFailure rf = (RemoteFailure) failure;
      final String type = rf.getClass().getSimpleName();
      final String cause = rf.e().failureMessage().toString();
      final String origin = rf.e().originNode().toString();
      String originChannelId = null;
      final List<String> hopsNodesPK = new ArrayList<>();
      if (rf.route().size() > 0) {
        final scala.collection.immutable.List<Hop> hops = rf.route().toList();
        for (int hi = 0; hi < hops.size(); hi++) {
          Hop h = hops.apply(hi);
          if (hi == 0) {
            hopsNodesPK.add(h.nodeId().toString());
          }
          if (origin.equals(h.nodeId().toString())) {
            originChannelId = Long.toHexString(h.lastUpdate().shortChannelId());
          }
          hopsNodesPK.add(h.nextNodeId().toString());
        }
      }
      return new LightningPaymentError(type, cause, origin, originChannelId, hopsNodesPK);
    } else if (failure instanceof LocalFailure) {
      final LocalFailure lf = (LocalFailure) failure;
      final String type = lf.getClass().getSimpleName();
      String cause;
      String originChannelId = null;
      Throwable t = lf.t();
      if (t instanceof RouteNotFound$) {
        cause = "The wallet could not find a path to the payee.";
      } else if (t instanceof ChannelException){
        cause = t.getMessage();
        originChannelId = ((ChannelException) t).channelId().toString();
      } else {
        cause = t.getClass().getSimpleName();
      }
      return new LightningPaymentError(type, cause, null, originChannelId, null);
    } else {
      return new LightningPaymentError("Unknown Error", "Unknown Cause", null, null, null);
    }
  }

  public String getType() {
    return type;
  }

  public String getCause() {
    return cause;
  }

  public String getOrigin() {
    return origin;
  }

  public String getOriginChannelId() {
    return originChannelId;
  }

  public List<String> getHops() {
    return hops;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel parcel, int flags) {
    parcel.writeString(type);
    parcel.writeString(cause);
    parcel.writeString(origin);
    parcel.writeString(originChannelId);
    parcel.writeList(hops);
  }
}
