package fr.acinq.eclair.router.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import scala.Predef;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import static scala.collection.JavaConverters.*;
import scala.collection.Map;

public class Common {

  public static <T> Seq<T> emptyScalaSeq() {
    return asScalaIteratorConverter(new ArrayList<T>().iterator()).asScala().toSeq();
  }

  public static <T> Seq<T> emptyScalaList() {
    return asScalaIteratorConverter(new ArrayList<T>().iterator()).asScala().toList();
  }

  public static <T, U> Map<T, U> emptyScalaMap() {
    return mapAsScalaMapConverter(new java.util.HashMap<T, U>()).asScala();
  }

  final public static String rawEclairConf = "eclair {\n" +
    "\n" +
    "  chain = \"testnet\" // \"regtest\" for regtest, \"testnet\" for testnet, \"mainnet\" for mainnet\n" +
    "\n" +
    "  server {\n" +
    "    public-ips = [] // external ips, will be announced on the network\n" +
    "    binding-ip = \"0.0.0.0\"\n" +
    "    port = 9735\n" +
    "  }\n" +
    "\n" +
    "  api {\n" +
    "    enabled = false // disabled by default for security reasons\n" +
    "    binding-ip = \"127.0.0.1\"\n" +
    "    port = 8080\n" +
    "    password = \"\" // password for basic auth, must be non empty if json-rpc api is enabled\n" +
    "  }\n" +
    "\n" +
    "  watcher-type = \"bitcoind\" // other *experimental* values include \"electrum\"\n" +
    "\n" +
    "  bitcoind {\n" +
    "    host = \"localhost\"\n" +
    "    rpcport = 18332\n" +
    "    rpcuser = \"foo\"\n" +
    "    rpcpassword = \"bar\"\n" +
    "    zmqblock = \"tcp://127.0.0.1:29000\"\n" +
    "    zmqtx = \"tcp://127.0.0.1:29000\"\n" +
    "  }\n" +
    "\n" +
    "  default-feerates { // those are in satoshis per kilobyte\n" +
    "    delay-blocks {\n" +
    "      1 = 210000\n" +
    "      2 = 180000\n" +
    "      6 = 150000\n" +
    "      12 = 110000\n" +
    "      36 = 50000\n" +
    "      72 = 20000\n" +
    "    }\n" +
    "  }\n" +
    "  min-feerate = 2 // minimum feerate in satoshis per byte\n" +
    "  smooth-feerate-window = 3 // 1 = no smoothing\n" +
    "\n" +
    "  node-alias = \"eclair\"\n" +
    "  node-color = \"49daaa\"\n" +
    "\n" +
    "  global-features = \"\"\n" +
    "  local-features = \"8a\" // initial_routing_sync + option_data_loss_protect + option_channel_range_queries\n" +
    "  override-features = [ // optional per-node features\n" +
    "  #  {\n" +
    "  #    nodeid = \"02aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\n" +
    "  #    global-features = \"\",\n" +
    "  #    local-features = \"\"\n" +
    "  #  }\n" +
    "  ]\n" +
    "  channel-flags = 1 // announce channels\n" +
    "\n" +
    "  dust-limit-satoshis = 546\n" +
    "  max-htlc-value-in-flight-msat = 5000000000 // 50 mBTC\n" +
    "  htlc-minimum-msat = 1\n" +
    "  max-accepted-htlcs = 30\n" +
    "\n" +
    "  reserve-to-funding-ratio = 0.01 // recommended by BOLT #2\n" +
    "  max-reserve-to-funding-ratio = 0.05 // channel reserve can't be more than 5% of the funding amount (recommended: 1%)\n" +
    "\n" +
    "  to-remote-delay-blocks = 144 // number of blocks that the other node's to-self outputs must be delayed (144 ~ 1 day)\n" +
    "  max-to-local-delay-blocks = 2000 // maximum number of blocks that we are ready to accept for our own delayed outputs (2000 ~ 2 weeks)\n" +
    "  mindepth-blocks = 3\n" +
    "  expiry-delta-blocks = 144\n" +
    "\n" +
    "  fee-base-msat = 1000\n" +
    "  fee-proportional-millionths = 100 // fee charged per transferred satoshi in millionths of a satoshi (100 = 0.01%)\n" +
    "\n" +
    "  // maximum local vs remote feerate mismatch; 1.0 means 100%\n" +
    "  // actual check is abs((local feerate - remote fee rate) / (local fee rate + remote fee rate)/2) > fee rate mismatch\n" +
    "  max-feerate-mismatch = 1.5\n" +
    "\n" +
    "  // funder will send an UpdateFee message if the difference between current commitment fee and actual current network fee is greater\n" +
    "  // than this ratio.\n" +
    "  update-fee_min-diff-ratio = 0.1\n" +
    "\n" +
    "  revocation-timeout = 20 seconds // after sending a commit_sig, we will wait for at most that duration before disconnecting\n" +
    "\n" +
    "  channel-exclude-duration = 60 seconds // when a temporary channel failure is returned, we exclude the channel from our payment routes for this duration\n" +
    "  router-broadcast-interval = 60 seconds // see BOLT #7\n" +
    "\n" +
    "  router-init-timeout = 5 minutes\n" +
    "\n" +
    "  ping-interval = 30 seconds\n" +
    "  ping-timeout = 10 seconds // will disconnect if peer takes longer than that to respond\n" +
    "  ping-disconnect = true // disconnect if no answer to our pings\n" +
    "  auto-reconnect = true\n" +
    "\n" +
    "  payment-handler = \"local\"\n" +
    "  payment-request-expiry = 1 hour // default expiry for payment requests generated by this node\n" +
    "  max-pending-payment-requests = 10000000\n" +
    "  max-payment-fee = 0.03 // max total fee for outgoing payments, in percentage: sending a payment will not be attempted if the cheapest route found is more expensive than that\n" +
    "  min-funding-satoshis = 100000\n" +
    "\n" +
    "  autoprobe-count = 0 // number of parallel tasks that send test payments to detect invalid channels\n" +
    "}";

}
