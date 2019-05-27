/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.models;

import android.net.Uri;
import android.support.annotation.NonNull;
import com.google.common.base.Strings;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.package$;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EclairException;
import fr.acinq.eclair.wallet.utils.WalletUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class BitcoinURI {
  private final static List<String> BITCOIN_PREFIXES = Arrays.asList("bitcoin://", "bitcoin:");

  @NonNull
  public final String address;
  @Nullable
  public final String message;
  @Nullable
  public final String label;
  @Nullable
  public final PaymentRequest lightning;
  @Nullable
  public final Satoshi amount;

  public BitcoinURI(final String input) throws EclairException.BitcoinURIParseException {

    if (input == null) {
      throw new EclairException.BitcoinURIParseException("input is null");
    }

    final Uri uri = Uri.parse(stripScheme(input));

    // -- should have no scheme (bitcoin: and bitcoin:// are stripped)
    if (uri.getScheme() != null) {
      throw new EclairException.BitcoinURIParseException("scheme is not valid");
    }

    // -- read and validate address
    final String path = uri.getPath();
    if (Strings.isNullOrEmpty(path)) {
      throw new EclairException.BitcoinURIParseException("address is missing");
    }
    try {
      package$.MODULE$.addressToPublicKeyScript(path, WalletUtils.getChainHash());
    } catch (IllegalArgumentException e) {
      throw new EclairException.BitcoinURIParseException(e.getLocalizedMessage(), e);
    }
    this.address = path;

    // -- read label/message field parameter
    this.label = uri.getQueryParameter("label");
    this.message = uri.getQueryParameter("message");

    // -- read and validate lightning payment request field
    final String lightningParam = uri.getQueryParameter("lightning");
    if (Strings.isNullOrEmpty(lightningParam)) {
      this.lightning = null;
    } else {
      this.lightning = PaymentRequest.read(lightningParam);
    }

    // -- read and validate amount field parameter. Amount is in BTC in the URI, and is converted to Satoshi,
    final String amountParam = uri.getQueryParameter("amount");
    if (Strings.isNullOrEmpty(amountParam)) {
      this.amount = null;
    } else {
      this.amount = CoinUtils.convertStringAmountToSat(amountParam, Constants.BTC_CODE);
    }

    // -- check required (yet unused) parameters
    // see https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki
    for (final String p : uri.getQueryParameterNames()) {
      if (p.startsWith("req-")) {
        throw new EclairException.BitcoinURIParseException("unhandled required param: " + p);
      }
    }
  }

  private String stripScheme(@Nonnull final String uri) {
    for (String prefix : BITCOIN_PREFIXES) {
      if (uri.toLowerCase().startsWith(prefix)) {
        return uri.substring(prefix.length());
      }
    }
    return uri;
  }

  @NonNull
  @Override
  public String toString() {
    return new StringBuilder()
      .append(getClass().getSimpleName()).append("[ ")
      .append("address: ").append(this.address).append(", ")
      .append("amount: ").append(this.amount).append(", ")
      .append("label: ").append(this.label).append(", ")
      .append("message: ").append(this.message).append(", ")
      .append("lightning: ").append(this.lightning).append(" ]").toString();
  }
}
