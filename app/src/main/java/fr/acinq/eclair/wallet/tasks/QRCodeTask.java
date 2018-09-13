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

package fr.acinq.eclair.wallet.tasks;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class QRCodeTask extends AsyncTask<String, Integer, Bitmap> {

  private final Logger log = LoggerFactory.getLogger(QRCodeTask.class);

  public interface AsyncQRCodeResponse {
    void processFinish(Bitmap output);
  }
  private AsyncQRCodeResponse delegate;

  private final QRCodeWriter writer = new QRCodeWriter();
  private final int width;
  private final int height;
  private final String source;

  public QRCodeTask(AsyncQRCodeResponse delegate, String source, int width, int height){
    this.delegate = delegate;
    this.width = width;
    this.height = height;
    this.source = "bitcoin:" + source;
  }

  @Override
  protected Bitmap doInBackground(String... params) {
    final Map<EncodeHintType, Object> hintsMap = new HashMap<>();
    hintsMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
    hintsMap.put(EncodeHintType.MARGIN, 2);
    final int qrWidth = 50;
    final int qrHeight= 50;
    try {
      BitMatrix matrix = writer.encode(source, BarcodeFormat.QR_CODE, qrWidth, qrHeight, hintsMap);
      final int[] pixels = new int[qrWidth * qrHeight];
      for (int j = 0; j < qrHeight; j++) {
        final int offset = j * qrWidth;
        for (int i = 0; i < qrWidth; i++) {
          pixels[offset + i] = matrix.get(i, j) ? Color.BLACK : 0xffffffff;
        }
      }
      return Bitmap.createScaledBitmap(Bitmap.createBitmap(pixels, qrWidth, qrHeight, Bitmap.Config.ARGB_8888), width, height, false);
    } catch (WriterException e) {
      log.warn("failed to generate QR code image for address {} with cause {}", source, e.getMessage());
      return null;
    }
  }

  protected void onPostExecute(Bitmap result) {
    delegate.processFinish(result);
  }
}
