package fr.acinq.eclair.wallet.events;

import fr.acinq.bitcoin.Satoshi;

public class WalletBalanceUpdateEvent {
  public final Satoshi balance;

  public WalletBalanceUpdateEvent(Satoshi balance) {
    this.balance = balance;
  }
}
