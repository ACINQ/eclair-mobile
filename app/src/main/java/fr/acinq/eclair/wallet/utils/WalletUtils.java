package fr.acinq.eclair.wallet.utils;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

import java.util.Arrays;
import java.util.List;

public class WalletUtils {
  public final static String EXPLORER_TRANSACTION_URI = "https://api.blockcypher.com/v1/btc/main/txs/";
  public final static List<String> LN_NODES = Arrays.asList(
    "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@endurance.acinq.co:9735"
  );

  public final static View.OnClickListener getOpenTxListener(final String txId) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (txId != null && !txId.trim().isEmpty()) {
          Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(WalletUtils.EXPLORER_TRANSACTION_URI + txId));
          v.getContext().startActivity(browserIntent);
        }
      }
    };
  }
}
