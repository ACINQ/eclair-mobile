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

package fr.acinq.eclair.backup;

import fr.acinq.bitcoin.ByteVector32;
import fr.acinq.eclair.wallet.activities.RestoreChannelsBackupActivity;
import fr.acinq.eclair.wallet.models.BackupTypes;
import fr.acinq.eclair.wallet.utils.EclairException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import scala.Option;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class RestoreBackupTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

  private final ByteVector32 c1 = ByteVector32.fromValidHex("584fa22f85bcf9d7db432189d3e983c9d6bf0ade7bc4cfee767cb4edfa066c0e");
  private final ByteVector32 c2 = ByteVector32.fromValidHex("49dcb176cf3ab627946c2e83d95b2073aa4b3f0b19f157175b6985cf2ae662ec");
  private final ByteVector32 c3 = ByteVector32.fromValidHex("99302a3a667202bdfe7802125fbc98c37ec5da4ee8b211b25af68b96ad236bf8");

  private final ByteVector32 c4 = ByteVector32.fromValidHex("1ce3222bb453873f159d58a0147f385198cfc9129fa9fd70891dce3193750d0f");
  private final ByteVector32 c5 = ByteVector32.fromValidHex("4523deb6a034c2dd1b28b5d2b7878d103963bf7987a7ecdca23846bfef0f2599");
  private final ByteVector32 c6 = ByteVector32.fromValidHex("a1949eedce10f91a9cdddc011791eca3a51f1fe50bf4569b10e847b35f5ab5fc");
  private final ByteVector32 c7 = ByteVector32.fromValidHex("864ba856055494e60ab42e049b5c8ba5c92eafaceb88120f411b77c231de34c4");
  private final ByteVector32 c8 = ByteVector32.fromValidHex("18b88c3618e84a0bb43427dcd47628c5eb5c2caf611913dd7c38d6ad8c29ec51");

  private Map<ByteVector32, Long> getIndexMap_1() {
    final Map<ByteVector32, Long> map = new HashMap<>();
    map.put(c1, 45L);
    map.put(c2, 3L);
    map.put(c3, 11L);
    return map;
  }

  private Map<ByteVector32, Long> getIndexMap_2() {
    final Map<ByteVector32, Long> map = new HashMap<>();
    map.put(c4, 2L);
    map.put(c5, 15L);
    map.put(c6, 10L);
    map.put(c7, 6L);
    map.put(c8, 24L);
    return map;
  }

  private Map<ByteVector32, Long> getIndexMap_2_updated() {
    final Map<ByteVector32, Long> map = getIndexMap_2();
    map.put(c7, 9L);
    return map;
  }

  private RestoreChannelsBackupActivity.BackupScanOk getOld(final BackupTypes t) throws IOException, ParseException {
    return new RestoreChannelsBackupActivity.BackupScanOk(t, getIndexMap_1(), sdf.parse("2019-07-01 01:02:03"), temp.newFile());
  }

  private RestoreChannelsBackupActivity.BackupScanOk getRecent(final BackupTypes t) throws IOException, ParseException {
    return new RestoreChannelsBackupActivity.BackupScanOk(t, getIndexMap_2(), sdf.parse("2019-07-04 06:59:59"), temp.newFile());
  }

  private RestoreChannelsBackupActivity.BackupScanOk getRecentUpdated(final BackupTypes t) throws IOException, ParseException {
    return new RestoreChannelsBackupActivity.BackupScanOk(t, getIndexMap_2_updated(), sdf.parse("2019-07-04 06:59:59"), temp.newFile());
  }

  private RestoreChannelsBackupActivity.BackupScanOk getOldUpdated(final BackupTypes t) throws IOException, ParseException {
    return new RestoreChannelsBackupActivity.BackupScanOk(t, getIndexMap_2_updated(), sdf.parse("2019-07-01 01:02:03"), temp.newFile());
  }

  @Test
  public void test_sortBackup() throws IOException, ParseException {
    final List<Map.Entry<BackupTypes, Option<RestoreChannelsBackupActivity.BackupScanResult>>> backups = new ArrayList<>();
    backups.add(new HashMap.SimpleEntry<>(BackupTypes.GDRIVE, Option.apply(new RestoreChannelsBackupActivity.BackupScanFailure("error"))));
    backups.add(new HashMap.SimpleEntry<>(BackupTypes.GDRIVE, Option.apply(new RestoreChannelsBackupActivity.BackupScanOk(BackupTypes.GDRIVE, getIndexMap_2(), sdf.parse("2016-01-18 00:10:03"), temp.newFile()))));
    backups.add(new HashMap.SimpleEntry<>(BackupTypes.GDRIVE, Option.apply(new RestoreChannelsBackupActivity.BackupScanOk(BackupTypes.GDRIVE, getIndexMap_2_updated(), sdf.parse("2019-07-04 06:59:59"), temp.newFile()))));
    backups.add(new HashMap.SimpleEntry<>(BackupTypes.LOCAL, Option.apply(new RestoreChannelsBackupActivity.BackupScanOk(BackupTypes.LOCAL, getIndexMap_2(), sdf.parse("2019-07-04 07:00:00"), temp.newFile()))));
    backups.add(new HashMap.SimpleEntry<>(BackupTypes.GDRIVE, Option.apply(new RestoreChannelsBackupActivity.BackupScanFailure("error"))));
    backups.add(new HashMap.SimpleEntry<>(BackupTypes.GDRIVE, Option.apply(new RestoreChannelsBackupActivity.BackupScanOk(BackupTypes.GDRIVE, getIndexMap_2(), sdf.parse("2019-02-15 11:02:51"), temp.newFile()))));
    final List<Map.Entry<BackupTypes, Option<RestoreChannelsBackupActivity.BackupScanResult>>> res = RestoreChannelsBackupActivity.sortBackupDateDesc(backups);
    Assert.assertSame(res.get(0).getKey(), BackupTypes.LOCAL);
    Assert.assertTrue(res.get(res.size() - 1).getValue().get() instanceof RestoreChannelsBackupActivity.BackupScanFailure);
  }

  @Test
  public void test_findBest_newest_timestamp() throws IOException, ParseException {
    final Map<BackupTypes, Option<RestoreChannelsBackupActivity.BackupScanResult>> backups = new HashMap<>();
    backups.put(BackupTypes.GDRIVE, Option.apply(getOld(BackupTypes.GDRIVE)));
    backups.put(BackupTypes.LOCAL, Option.apply(getRecent(BackupTypes.LOCAL)));
    final RestoreChannelsBackupActivity.BackupScanOk best = RestoreChannelsBackupActivity.findBestBackup(backups);
    Assert.assertNotNull(best);
    Assert.assertSame(best.type, BackupTypes.LOCAL);
  }

//  @Test
//  public void test_findBest_prefer_timestamp_over_commitment() throws IOException, ParseException {
//    final Map<BackupTypes, Option<RestoreChannelsBackupActivity.BackupScanResult>> backups = new HashMap<>();
//    backups.put(BackupTypes.LOCAL, Option.apply(getRecent(BackupTypes.LOCAL)));
//    backups.put(BackupTypes.GDRIVE, Option.apply(getOldUpdated(BackupTypes.GDRIVE)));
//    final RestoreChannelsBackupActivity.BackupScanOk best = RestoreChannelsBackupActivity.findBestBackup(backups);
//    Assert.assertNotNull(best);
//    Assert.assertSame(best.type, BackupTypes.LOCAL);
//  }

  @Test
  public void test_findBest_newer_commitments() throws IOException, ParseException {
    final Map<BackupTypes, Option<RestoreChannelsBackupActivity.BackupScanResult>> backups = new HashMap<>();
    backups.put(BackupTypes.LOCAL, Option.apply(getRecent(BackupTypes.LOCAL)));
    backups.put(BackupTypes.GDRIVE, Option.apply(getRecentUpdated(BackupTypes.GDRIVE)));
    final RestoreChannelsBackupActivity.BackupScanOk best = RestoreChannelsBackupActivity.findBestBackup(backups);
    Assert.assertNotNull(best);
    Assert.assertSame(best.type, BackupTypes.GDRIVE);
    Assert.assertEquals(Objects.requireNonNull(best.localCommitIndexMap.get(c7)).longValue(), 9L);
  }

  @Test(expected = EclairException.UnreadableBackupException.class)
  public void test_findBest_throw_if_failure() throws IOException, ParseException {
    final Map<BackupTypes, Option<RestoreChannelsBackupActivity.BackupScanResult>> backups = new HashMap<>();
    backups.put(BackupTypes.LOCAL, Option.apply(new RestoreChannelsBackupActivity.BackupScanFailure("foo error in local")));
    backups.put(BackupTypes.GDRIVE, Option.apply(getRecent(BackupTypes.GDRIVE)));
    RestoreChannelsBackupActivity.findBestBackup(backups);
  }

  @Test
  public void test_findBest_null_if_null() {
    final Map<BackupTypes, Option<RestoreChannelsBackupActivity.BackupScanResult>> backups = new HashMap<>();
    backups.put(BackupTypes.GDRIVE, Option.empty());
    backups.put(BackupTypes.LOCAL, Option.empty());
    Assert.assertNull(RestoreChannelsBackupActivity.findBestBackup(backups)); // means that no backup was found
  }

  @Test
  public void test_findBest_ok_if_has_null() throws IOException, ParseException {
    final Map<BackupTypes, Option<RestoreChannelsBackupActivity.BackupScanResult>> backups = new HashMap<>();
    backups.put(BackupTypes.LOCAL, Option.apply(getOld(BackupTypes.LOCAL)));
    backups.put(BackupTypes.GDRIVE, Option.empty());
    Assert.assertSame(Objects.requireNonNull(RestoreChannelsBackupActivity.findBestBackup(backups)).type, BackupTypes.LOCAL);
  }
}
