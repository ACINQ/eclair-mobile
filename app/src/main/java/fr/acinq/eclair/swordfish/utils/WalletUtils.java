package fr.acinq.eclair.swordfish.utils;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

public class WalletUtils {
  public final static String EXPLORER_TRANSACTION_URI = "https://tbtc.blockr.io/tx/info/";

  public final static View.OnClickListener getOpenTxListener(final String txId) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(WalletUtils.EXPLORER_TRANSACTION_URI + txId));
        v.getContext().startActivity(browserIntent);
      }
    };
  }
}
