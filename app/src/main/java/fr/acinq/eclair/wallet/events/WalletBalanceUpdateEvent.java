package fr.acinq.eclair.wallet.events;

import fr.acinq.bitcoin.Satoshi;

public class WalletBalanceUpdateEvent {
  public final Satoshi walletBalance;

  public WalletBalanceUpdateEvent(Satoshi walletBalance) {
    this.walletBalance = walletBalance;
  }
}
