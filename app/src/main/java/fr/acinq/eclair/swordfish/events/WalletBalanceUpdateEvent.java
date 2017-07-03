package fr.acinq.eclair.swordfish.events;

import fr.acinq.bitcoin.Satoshi;

public class WalletBalanceUpdateEvent {
  public final Satoshi walletBalance;

  public WalletBalanceUpdateEvent(Satoshi walletBalance) {
    this.walletBalance = walletBalance;
  }
}
