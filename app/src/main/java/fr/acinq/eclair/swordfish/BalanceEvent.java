package fr.acinq.eclair.swordfish;

import fr.acinq.bitcoin.Satoshi;

public class BalanceEvent {
  public final String channelId;
  public final Satoshi balance;

  public BalanceEvent(String channelId, Satoshi balance) {
    this.channelId = channelId;
    this.balance = balance;
  }
}
