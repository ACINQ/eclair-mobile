package fr.acinq.eclair.wallet.models;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.channel.ChannelException;
import fr.acinq.eclair.payment.Hop;
import fr.acinq.eclair.payment.LocalFailure;
import fr.acinq.eclair.payment.PaymentFailure;
import fr.acinq.eclair.payment.RemoteFailure;

/**
 * Contains a detailed error in a Lightning payment.
 * This object implements Parcelable so that it can be passed between activities.
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
      final String cause = rf.e().failureMessage() == null ? "Unknown cause" : rf.e().failureMessage().getClass().getSimpleName();
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
      final String cause = lf.t().getClass().getSimpleName();
      final String origin = lf.t() instanceof ChannelException ? ((ChannelException) lf.t()).channelId().toString() : null;
      return new LightningPaymentError(type, cause, origin, null, null);
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
