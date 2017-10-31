package fr.acinq.eclair.wallet.jobs;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This service fetches the current fiat/BTC exchange rate from various API.
 */
public class ExchangeRateJob extends JobService {
  public static final String TAG = "ExchangeRateJob";

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "Service created");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "Service destroyed");
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "Service started");
    return START_STICKY;
  }

  @Override
  public boolean onStartJob(final JobParameters params) {
    Log.d(TAG, "Service starts job");

    RequestQueue queue = Volley.newRequestQueue(this);
    JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.GET, "https://bitcoinfees.21.co/api/v1/fees/recommended", null,
      new Response.Listener<JSONObject>() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onResponse(JSONObject response) {
          try {
            Long fastFee = response.getLong("fastestFee");
            Long mediumFee = response.getLong("halfHourFee");
            Long slowFee = response.getLong("hourFee");
            Log.i(TAG, "response=" + response);
          } catch (JSONException e) {
            Log.e(TAG, "Could not read 21.co response", e);
          }
        }
      }, new Response.ErrorListener() {
      @Override
      public void onErrorResponse(VolleyError error) {
        Log.e(TAG, "Error when querying 21.co", error);
      }
    });
    queue.add(stringRequest);

    return false;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    Log.d(TAG, "Stop job with id=" + params.getJobId());
    return true;
  }
}
