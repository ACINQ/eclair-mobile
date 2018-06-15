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
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

public class LightningQRCodeTask extends AsyncTask<String, Integer, Bitmap> {

  public interface AsyncQRCodeResponse {
    void processLightningQRCodeFinish(Bitmap output);
  }
  private AsyncQRCodeResponse delegate;

  private static final String TAG = LightningQRCodeTask.class.getSimpleName();
  private final QRCodeWriter writer = new QRCodeWriter();
  private final int width;
  private final int height;
  private final String source;

  public LightningQRCodeTask(AsyncQRCodeResponse delegate, String source, int width, int height){
    this.delegate = delegate;
    this.width = width;
    this.height = height;
    this.source = "lightning:" + source;
  }

  @Override
  protected Bitmap doInBackground(String... params) {
    return QRCodeTask.generateBitmap(this.writer, this.source, this.width, this.height);
  }

  protected void onPostExecute(Bitmap result) {
    delegate.processLightningQRCodeFinish(result);
  }
}
