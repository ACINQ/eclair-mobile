package fr.acinq.eclair.wallet.events;

public class NotificationEvent {
  public final static int NOTIF_CHANNEL_CLOSED_ID = 1;

  public final int id;
  public final String tag;
  public final String title;
  public final String message;
  public final String bigMessage;

  public NotificationEvent(int id, String tag, String title, String message, String bigMessage) {
    this.id = id;
    this.tag = tag;
    this.title = title;
    this.message = message;
    this.bigMessage = bigMessage;
  }
}
