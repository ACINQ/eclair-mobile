package fr.acinq.eclair.swordfish.tasks;

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

public class QRCodeTask extends AsyncTask<String, Integer, Bitmap> {

  public interface AsyncQRCodeResponse {
    void processFinish(Bitmap output);
  }
  private AsyncQRCodeResponse delegate;

  private static final String TAG = "QRCodeTask";
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
    hintsMap.put(EncodeHintType.CHARACTER_SET, "utf-8");
    hintsMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
    try {
      BitMatrix matrix = writer.encode(source, BarcodeFormat.QR_CODE, width, height, hintsMap);
      final int[] pixels = new int[width * height];
      for (int j = 0; j < height; j++) {
        final int offset = j * width;
        for (int i = 0; i < width; i++) {
          pixels[offset + i] = matrix.get(i, j) ? Color.BLACK : 0x00ffffff;
        }
      }
      return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    } catch (WriterException e) {
      Log.e(TAG, "Failed to generate QR code for source " + source, e);
      return null;
    }
  }

  protected void onPostExecute(Bitmap result) {
    delegate.processFinish(result);
  }
}
