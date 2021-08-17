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
package fr.acinq.eclair.crypto

import fr.acinq.eclair.wallet.utils.WalletUtils.writeSeedFile
import fr.acinq.eclair.wallet.utils.WalletUtils.readSeedAndDecrypt
import kotlin.Throws
import com.tozny.crypto.android.AesCbcWithIntegrity
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.wallet.utils.WalletUtils
import fr.acinq.eclair.wallet.utils.EncryptedSeed
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import scala.collection.JavaConverters
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.security.GeneralSecurityException

class SeedEncryptionTest {
  private val password = "123456"

  @get:Rule
  public var temp = TemporaryFolder()

  private fun createWords(length: Int) = JavaConverters.seqAsJavaListConverter(
    MnemonicCode.toMnemonics(`package$`.`MODULE$`.randomBytes(length), MnemonicCode.englishWordlist())
  ).asJava()

  @Test
  @Throws(GeneralSecurityException::class, UnsupportedEncodingException::class)
  fun basicTest() {
    val salt = AesCbcWithIntegrity.generateSalt()
    val plaintext = "this is not encrypted".toByteArray(charset("UTF-8"))
    val keys = AesCbcWithIntegrity.generateKeyFromPassword(password, salt)
    val civ = AesCbcWithIntegrity.encrypt(plaintext, keys)
    val decrypted = AesCbcWithIntegrity.decrypt(civ, keys)
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(plaintext, decrypted))
  }

  @Test
  @Throws(IOException::class, GeneralSecurityException::class, IllegalAccessException::class)
  fun writeAndReadSeed_v1() {
    val words = createWords(16)
    val seed = WalletUtils.encodeMnemonics(EncryptedSeed.SEED_FILE_VERSION_1, words, "")
    val datadir = temp.newFolder("datadir_temp")
    writeSeedFile(datadir, seed, password, EncryptedSeed.SEED_FILE_VERSION_1)

    val (encryptedSeed, blob) = readSeedAndDecrypt(datadir, password)
    Assert.assertEquals(1, encryptedSeed.version)
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(seed, blob))
  }

  @Test(expected = GeneralSecurityException::class)
  @Throws(IOException::class, GeneralSecurityException::class, IllegalAccessException::class)
  fun failReadSeedWrongPassword_v1() {
    val words = createWords(16)
    val seed = WalletUtils.encodeMnemonics(EncryptedSeed.SEED_FILE_VERSION_1, words, "")
    val datadir = temp.newFolder("datadir_temp")
    writeSeedFile(datadir, seed, password, EncryptedSeed.SEED_FILE_VERSION_1)
    readSeedAndDecrypt(datadir, "999999")
  }

  private fun checkV2Mnemonics(datadir: File, words: List<String>, passphrase: String?) {
    val hex = WalletUtils.encodeMnemonics(EncryptedSeed.SEED_FILE_VERSION_2, words, passphrase)

    val (encryptedSeed, decrypted) = readSeedAndDecrypt(datadir, password)
    Assert.assertEquals(2, encryptedSeed.version)
    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(hex, decrypted))

    val (extractedWords, extractedPassphrase) = WalletUtils.decodeV2MnemonicsBlob(decrypted)
    Assert.assertTrue(extractedWords.size == 12 || extractedWords.size == 24)
    Assert.assertEquals(words, extractedWords)
    Assert.assertEquals(passphrase, extractedPassphrase)
  }

  @Test
  @Throws(IOException::class, GeneralSecurityException::class, IllegalAccessException::class)
  fun writeAndReadSeed_v2_nopassphrase() {
    val words = createWords(16)
    val passphrase = ""
    val datadir = temp.newFolder("datadir_temp")
    writeSeedFile(datadir, WalletUtils.encodeMnemonics(EncryptedSeed.SEED_FILE_VERSION_2, words, passphrase), password, EncryptedSeed.SEED_FILE_VERSION_2)

    checkV2Mnemonics(datadir, words, passphrase)

    val words24 = "utility access ship also soda axis unveil tag garden end when oblige sketch fit rack word swap teach bind purchase twin pair demand cube".split(" ")
    writeSeedFile(datadir, WalletUtils.encodeMnemonics(EncryptedSeed.SEED_FILE_VERSION_2, words24, passphrase), password, EncryptedSeed.SEED_FILE_VERSION_2)

    checkV2Mnemonics(datadir, words24, passphrase)
  }

  @Test
  @Throws(IOException::class, GeneralSecurityException::class, IllegalAccessException::class)
  fun writeAndReadSeed_v2_blankpassphrase() {
    val words = "vendor patch glow alone swear alley early dutch bacon such supply rescue".split(" ")
    val passphrase = "           " + '\t'
    val datadir = temp.newFolder("datadir_temp")
    writeSeedFile(datadir, WalletUtils.encodeMnemonics(EncryptedSeed.SEED_FILE_VERSION_2, words, passphrase), password, EncryptedSeed.SEED_FILE_VERSION_2)

    checkV2Mnemonics(datadir, words, passphrase)
  }

  @Test
  @Throws(IOException::class, GeneralSecurityException::class, IllegalAccessException::class)
  fun writeAndReadSeed_v2_passphrase() {
    val words = createWords(32)
    val passphrase = "my secret PassPhrase 12 ͡°!@#$%^&*\"()éûöçñفر_][';.,"
    val datadir = temp.newFolder("datadir_temp")
    writeSeedFile(datadir, WalletUtils.encodeMnemonics(EncryptedSeed.SEED_FILE_VERSION_2, words, passphrase), password, EncryptedSeed.SEED_FILE_VERSION_2)

    checkV2Mnemonics(datadir, words, passphrase)
  }
}
