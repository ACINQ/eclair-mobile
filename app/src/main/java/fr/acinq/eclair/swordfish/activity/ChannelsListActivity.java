package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import java.util.List;

import fr.acinq.eclair.swordfish.ChannelsListTask;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.adapters.ChannelListItemAdapter;
import fr.acinq.eclair.swordfish.model.ChannelItem;

public class ChannelsListActivity extends AppCompatActivity implements ChannelsListTask.AsyncChannelsListResponse {

  @Override
  public void processFinish(List<ChannelItem> channels) {
    if (channels.size() == 0) {
      findViewById(R.id.channelslist__label_empty).setVisibility(View.VISIBLE);
      findViewById(R.id.channelslist__listview).setVisibility(View.GONE);
    } else {
      ChannelListItemAdapter adapter = new ChannelListItemAdapter(this, channels);
      ListView listView = (ListView) findViewById(R.id.channelslist__listview);
      listView.setAdapter(adapter);
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
        new ChannelsListTask(this, getApplicationContext()).execute();
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

    new ChannelsListTask(this, getApplicationContext()).execute();
  }

  public void channelslist__refresh(View view) {
    new ChannelsListTask(this, getApplicationContext()).execute();
  }

  public void channellist__goToFund(View view) {
    Intent intent = new Intent(this, FundActivity.class);
    startActivity(intent);
  }
}
