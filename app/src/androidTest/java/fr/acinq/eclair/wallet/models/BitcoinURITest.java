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

import android.util.Log;
import fr.acinq.eclair.BtcUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.utils.EclairException;
import org.junit.Assert;
import org.junit.Test;

public class BitcoinURITest {

  private final String no_scheme = "";
  private final String valid_scheme = "bitcoin:";
  private final String acceptable_scheme_ = "bitcoin://";
  private final String invalid_scheme = "bitcoinmagic://";

  private final String valid_address = "2MxGZTPhfGqcU4s8jn7FAM3AZCmAjaQTo1n";
  private final String invalid_address = "2MxGZTPhfGqcU4s8jn7FAM3AZCmAjaQTo1ncU4s8jn";
  private final String empty_address = "";

  private final String valid_amount = "21.98765432";
  private final String invalid_amount = "21.9876.5432";
  private final String negative_amount = "-0.123";
  private final String empty_amount = "";

  private final String valid_lightning = "lntb17u1pwws8k3pp5sy3rjx7td55uthljg5jz4mj6k8g0svkww0k8ccjr7cx99jh9gnxqdpzxysy2umswfjhxum0yppk76twypgxzmnwvycqp5kdvdfuhykxcnxrrqmhwzp4suxqdp43x7vvk69mnz95mupk53vnnqh5ujzy5hvak56v4yr878m6sdapcuzlxrm6x5jam50j7jl4a6zwcp9ztydh";
  private final String invalid_lightning = "lntb17u1pwws8k3pp5sy36twypgxzmnwvycqp5kdvdfuhykxcnxrrqmhwzp4suxqdp43x7vvk69mnz95mupk53vnnqh5ujzy5hvak56v4yr878m6sdapcuzlxrm6x5jam50j7jl4a6zwcp9ztydh";
  private final String empty_lightning = "";

  @Test
  public void basic_valid() throws EclairException.BitcoinURIParseException {
    Assert.assertEquals(valid_address, new BitcoinURI(no_scheme + valid_address).address);
    Assert.assertNull(new BitcoinURI(no_scheme + valid_address).amount);
    Assert.assertEquals(valid_address, new BitcoinURI(valid_scheme + valid_address).address);
    Assert.assertEquals(valid_address, new BitcoinURI(acceptable_scheme_ + valid_address).address);

    BitcoinURI uri = new BitcoinURI(valid_scheme + valid_address + "?amount=" + valid_amount);
    Assert.assertEquals(CoinUtils.convertStringAmountToSat(valid_amount, BtcUnit.code()).amount(), uri.amount.amount());

    uri = new BitcoinURI(valid_scheme + valid_address + "?amount=" + valid_amount + "&lightning=" + valid_lightning);
    Assert.assertEquals(valid_lightning, PaymentRequest.write(uri.lightning));

    uri = new BitcoinURI(valid_scheme + valid_address + "?label=foo%20BAR&message=Donation%20for%20project%20xyz");
    Assert.assertEquals("foo BAR", uri.label);
    Assert.assertEquals("Donation for project xyz", uri.message);
  }

  @Test(expected = EclairException.BitcoinURIParseException.class)
  public void no_address() throws EclairException.BitcoinURIParseException {
    new BitcoinURI(valid_scheme + empty_address);
  }

  @Test(expected = EclairException.BitcoinURIParseException.class)
  public void invalid_address() throws EclairException.BitcoinURIParseException {
    new BitcoinURI(valid_scheme + invalid_address);
  }

  @Test(expected = EclairException.BitcoinURIParseException.class)
  public void invalid_scheme() throws EclairException.BitcoinURIParseException {
    new BitcoinURI(invalid_scheme + valid_address);
  }

  @Test(expected = NumberFormatException.class)
  public void invalid_amount() throws EclairException.BitcoinURIParseException {
    new BitcoinURI(valid_scheme + valid_address + "?amount=" + invalid_amount);
  }

  @Test(expected = IllegalArgumentException.class)
  public void negative_amount() throws EclairException.BitcoinURIParseException {
    new BitcoinURI(valid_scheme + valid_address + "?amount=" + negative_amount);
  }

  @Test
  public void empty_amount() throws EclairException.BitcoinURIParseException {
    assert (null == new BitcoinURI(valid_scheme + valid_address + "?amount=" + empty_amount).amount);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalid_lightning() throws EclairException.BitcoinURIParseException {
    new BitcoinURI(valid_scheme + valid_address + "?lightning=" + invalid_lightning);
  }

  @Test
  public void empty_lightning() throws EclairException.BitcoinURIParseException {
    assert (null == new BitcoinURI(valid_scheme + valid_address + "?lightning=" + empty_lightning).lightning);
  }

  @Test(expected = EclairException.BitcoinURIParseException.class)
  public void required_param() throws EclairException.BitcoinURIParseException {
    new BitcoinURI(valid_scheme + valid_address + "?req-param=whatever");
  }
}
