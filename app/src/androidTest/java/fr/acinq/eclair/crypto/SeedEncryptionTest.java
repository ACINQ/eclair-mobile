/*
 * Copyright 2018 ACINQ SAS
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

package fr.acinq.eclair.crypto;

import com.tozny.crypto.android.AesCbcWithIntegrity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class SeedEncryptionTest {

  private final String password = "123456";

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void basicTest() throws GeneralSecurityException, UnsupportedEncodingException {
    byte[] salt = AesCbcWithIntegrity.generateSalt();
    byte[] plaintext = "this is not encrypted".getBytes("UTF-8");
    AesCbcWithIntegrity.SecretKeys keys = AesCbcWithIntegrity.generateKeyFromPassword(password, salt);
    AesCbcWithIntegrity.CipherTextIvMac civ = AesCbcWithIntegrity.encrypt(plaintext, keys);
    byte[] decrypted = AesCbcWithIntegrity.decrypt(civ, keys);
    assert (AesCbcWithIntegrity.constantTimeEq(plaintext, decrypted));
  }

  @Test
  public void writeAndReadSeed() throws IOException, GeneralSecurityException, IllegalAccessException {
    byte[] seed = new byte[ElectrumWallet.SEED_BYTES_LENGTH()];
    new SecureRandom().nextBytes(seed);
    final File datadir = temp.newFolder("datadir_temp");
    WalletUtils.writeSeedFile(datadir, seed, password);
    final byte[] decrypted = WalletUtils.readSeedFile(datadir, password);
    assert (AesCbcWithIntegrity.constantTimeEq(seed, decrypted));
  }

  @Test(expected = GeneralSecurityException.class)
  public void failReadSeedWrongPassword() throws IOException, GeneralSecurityException, IllegalAccessException {
    byte[] seed = new byte[ElectrumWallet.SEED_BYTES_LENGTH()];
    new SecureRandom().nextBytes(seed);
    final File datadir = temp.newFolder("datadir_temp");
    WalletUtils.writeSeedFile(datadir, seed, password);
    WalletUtils.readSeedFile(datadir, "999999");
  }

}

