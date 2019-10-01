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

package fr.acinq.eclair.wallet.utils

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Base64
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.SatUnit
import fr.acinq.eclair.`CoinUtils$`

object Prefs {
  // -- authentication with PIN/biometrics
  private const val PREFS_ENCRYPTED_PIN: String = "PREFS_ENCRYPTED_PIN"
  private const val PREFS_ENCRYPTED_PIN_IV: String = "PREFS_ENCRYPTED_PIN_IV"
  private const val PREFS_USE_BIOMETRICS: String = "PREFS_USE_BIOMETRICS"

  // -- ==================================
  // -- authentication with PIN/biometrics
  // -- ==================================

  @JvmStatic
  fun useBiometrics(context: Context): Boolean {
    return context.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).getBoolean(PREFS_USE_BIOMETRICS, false)
  }

  @JvmStatic
  fun useBiometrics(context: Context, useBiometrics: Boolean) {
    context.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).edit().putBoolean(PREFS_USE_BIOMETRICS, useBiometrics).apply()
  }

  fun getEncryptedPIN(context: Context): ByteArray? {
    return context.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).getString(PREFS_ENCRYPTED_PIN, null)?.let { Base64.decode(it, Base64.DEFAULT) }
  }

  fun saveEncryptedPIN(context: Context, encryptedPIN: ByteArray) {
    context.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).edit().putString(PREFS_ENCRYPTED_PIN, Base64.encodeToString(encryptedPIN, Base64.DEFAULT)).apply()
  }

  fun getEncryptedPINIV(context: Context): ByteArray? {
    return context.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).getString(PREFS_ENCRYPTED_PIN_IV, null)?.let { Base64.decode(it, Base64.DEFAULT) }
  }

  fun saveEncryptedPINIV(context: Context, iv: ByteArray) {
    context.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).edit().putString(PREFS_ENCRYPTED_PIN_IV, Base64.encodeToString(iv, Base64.DEFAULT)).apply()
  }
}
