package fr.acinq.eclair.wallet.events;

import fr.acinq.bitcoin.Satoshi;

public class WalletStateUpdateEvent {
  public final Satoshi balance;
  public final boolean isSync;

  public WalletStateUpdateEvent(Satoshi balance, boolean isSync) {
    this.balance = balance;
    this.isSync = isSync;
  }
}
