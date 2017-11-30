package fr.acinq.eclair.wallet.utils;

import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public class WalletUtils {
  public final static List<String> LN_NODES = Arrays.asList(
    "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@endurance.acinq.co:9735"
  );

  public final static View.OnClickListener getOpenTxListener(final String txId) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        final String uri = PreferenceManager.getDefaultSharedPreferences(v.getContext())
          .getString(Constants.SETTING_ONCHAIN_EXPLORER, "https://api.blockcypher.com/v1/btc/test3/txs/");
        try {
          Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri + txId));
          v.getContext().startActivity(browserIntent);
        } catch (Throwable t) {
          Log.e(WalletUtils.class.getSimpleName(), "Could not open explorer with uri=" + uri + txId);
          Toast.makeText(v.getContext(), "Could not open explorer", Toast.LENGTH_SHORT).show();
        }
      }
    };
  }
}
