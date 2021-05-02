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

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.common.io.Files
import fr.acinq.eclair.wallet.utils.EclairException.ExternalStorageUnavailableException
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.util.*


data class LocalBackupFile(val modifiedAt: Date, val data: ByteArray, val path: String)

object LocalBackupHelper {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun isExternalStorageWritable(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
  }

  fun hasLocalAccess(context: Context?): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        || (ContextCompat.checkSelfPermission(context!!, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && isExternalStorageWritable()
        && Environment.getExternalStorageDirectory().canWrite())
  }

  @Throws(ExternalStorageUnavailableException::class)
  fun getBackupFile(context: Context, backupFileName: String): LocalBackupFile? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      getBackupUriMediaStore(context, backupFileName)?.let { (modifiedAt, uri) ->
        getBackupFileFromUri(context, backupFileName, uri, modifiedAt)
      }
    } else {
      val file = getBackupFileLegacy(backupFileName)
      LocalBackupFile(Date(file.lastModified()), Files.toByteArray(file), file.absolutePath)
    }
  }

  @Throws(ExternalStorageUnavailableException::class)
  fun getBackupFileFromUri(context: Context, backupFileName: String, uri: Uri, modifiedAt: Long): LocalBackupFile? {
    return context.contentResolver.openInputStream(uri)?.use {
      it.readBytes()
    }?.let {
      LocalBackupFile(Date(modifiedAt * 1000), it, "${Environment.DIRECTORY_DOCUMENTS}/${Constants.ECLAIR_BACKUP_DIR}/$backupFileName")
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  @Throws(ExternalStorageUnavailableException::class)
  private fun getBackupUriMediaStore(context: Context, backupFileName: String): Pair<Long, Uri>? {
    val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_MODIFIED
    )
    val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
    val selectionArgs = arrayOf(backupFileName)
    val resolver = context.contentResolver
    return resolver.query(
        contentUri,
        projection,
        selection,
        selectionArgs,
        null, null
    )?.use { cursor ->
      val idColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
      val nameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
      val modifiedAtColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
      if (cursor.moveToNext()) {
        val fileId = cursor.getLong(idColumn)
        val fileName = cursor.getString(nameColumn)
        val modifiedAt = cursor.getLong(modifiedAtColumn)
        log.info("(mediastore) found backup file with name=$fileName modified_at=${Date(modifiedAt * 1000)}")
        modifiedAt to ContentUris.withAppendedId(contentUri, fileId)
      } else {
        log.info("(mediastore) no backup file found for name=$backupFileName")
        null
      }
    }
  }

  private fun getBackupFileLegacy(backupFileName: String): File {
    if (!isExternalStorageWritable()) {
      throw ExternalStorageUnavailableException()
    }

    // on legacy (android 9 or lower) local backups are stored in download/eclair-mobile-backups
    val storage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!storage.canWrite()) {
      throw ExternalStorageUnavailableException()
    }

    val publicDir = File(storage, Constants.ECLAIR_BACKUP_DIR)
    val backup = File(publicDir, backupFileName)

    if (!backup.exists()) {
      if (!publicDir.exists() && !publicDir.mkdirs()) {
        throw ExternalStorageUnavailableException()
      }
    }
    return backup
  }

  @Throws(ExternalStorageUnavailableException::class)
  fun saveBackupFile(
      context: Context,
      backupFileName: String,
      bytes: ByteArray,
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      log.info("(mediastore) saving channels backup to device")
      val resolver = context.contentResolver
      val uri = getBackupUriMediaStore(context, backupFileName)?.second ?: run {
        val values = ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
          put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
          put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/${Constants.ECLAIR_BACKUP_DIR}")
        }
        resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), values)
            ?: throw RuntimeException("failed to create media store record for backup=$backupFileName")
      }
      resolver.openOutputStream(uri)
          ?: throw RuntimeException("failed to open output stream from uri=$uri")
    } else {
      log.info("(legacy) saving channels backup to device")
      FileOutputStream(getBackupFileLegacy(backupFileName))
    }.use { outputStream ->
      outputStream.write(bytes)
      log.info("channels backup has been saved to device")
    }
  }
}