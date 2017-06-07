package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.List;

import fr.acinq.eclair.swordfish.ChannelsListTask;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.adapters.ChannelListItemAdapter;
import fr.acinq.eclair.swordfish.fragment.OneInputDialog;
import fr.acinq.eclair.swordfish.model.ChannelItem;
import fr.acinq.eclair.swordfish.utils.Validators;

public class ChannelsListActivity extends AppCompatActivity implements ChannelsListTask.AsyncChannelsListResponse, OneInputDialog.OneInputDialogListener {

  public static final String EXTRA_NEWHOSTURI = "fr.acinq.eclair.swordfish.NEWHOSTURI";

  @Override
  public void onDialogPositiveClick(OneInputDialog dialog, String uri) {
    goToOpenChannelActivity(uri);
  }

  @Override
  public void processFinish(List<ChannelItem> channels) {
    if (channels.size() == 0) {
      findViewById(R.id.channelslist__label_pending).setVisibility(View.GONE);
      findViewById(R.id.channelslist__label_empty).setVisibility(View.VISIBLE);
      findViewById(R.id.channelslist__listview).setVisibility(View.GONE);
    } else {
      ChannelListItemAdapter adapter = new ChannelListItemAdapter(this, channels);
      ListView listView = (ListView) findViewById(R.id.channelslist__listview);
      listView.setAdapter(adapter);
      findViewById(R.id.channelslist__label_pending).setVisibility(View.GONE);
      findViewById(R.id.channelslist__label_empty).setVisibility(View.GONE);
      findViewById(R.id.channelslist__listview).setVisibility(View.VISIBLE);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_channelslist, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_channelslist_refresh:
        fetchingChannelsList();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_channels_list);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    fetchingChannelsList();
  }

  public void channellist__toggleButtons(View view) {
    FloatingActionButton fb = (FloatingActionButton) findViewById(R.id.channelslist__button_new);
    if (findViewById(R.id.channelslist__button_newchannel_manual).getVisibility() == View.GONE) {
      findViewById(R.id.channelslist__button_newchannel_manual).setVisibility(View.VISIBLE);
      findViewById(R.id.channelslist__button_newchannel_scan).setVisibility(View.VISIBLE);
      fb.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimaryDark));
      fb.setRotation(45.0f);
    } else {
      findViewById(R.id.channelslist__button_newchannel_manual).setVisibility(View.GONE);
      findViewById(R.id.channelslist__button_newchannel_scan).setVisibility(View.GONE);
      findViewById(R.id.channelslist__button_new).setBackgroundResource(R.drawable.ic_plus_white_24dp);
      fb.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
      fb.setRotation(0.0f);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d("Channel List Scanner", "Got a result with code " + requestCode + "/" + resultCode);
    IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    if (result != null /*&& requestCode == */ && resultCode == RESULT_OK) {
      if (result.getContents() == null) {
        Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
      } else {
        goToOpenChannelActivity(result.getContents());
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void goToOpenChannelActivity(String uri) {
    if (Validators.HOST_REGEX.matcher(uri).matches()) {
      Intent intent = new Intent(this, OpenChannelActivity.class);
      intent.putExtra(EXTRA_NEWHOSTURI, uri);
      startActivity(intent);
    } else {
      Toast.makeText(this, "Invalid URI", Toast.LENGTH_SHORT).show();
    }
  }

  public void channellist__showManualDialog(View view) {
    OneInputDialog dialog = new OneInputDialog();
    dialog.show(getFragmentManager(), "ChannelURIDialog");
  }

  public void channellist__showScanner(View view) {
    IntentIntegrator integrator = new IntentIntegrator(this);
    integrator.setOrientationLocked(false);
    integrator.setCaptureActivity(ScanActivity.class);
    integrator.setBeepEnabled(false);
    integrator.setPrompt("Scan a LN Node's URI QR Code");
    integrator.initiateScan();
  }

  private void fetchingChannelsList() {
    findViewById(R.id.channelslist__label_pending).setVisibility(View.VISIBLE);
    new ChannelsListTask(this, getApplicationContext()).execute();
  }
}
