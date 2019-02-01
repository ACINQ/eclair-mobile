package fr.acinq.eclair.router;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import fr.acinq.bitcoin.Base58;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Block;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Crypto.PublicKey;
import fr.acinq.bitcoin.Crypto.PrivateKey;
import fr.acinq.eclair.NodeParams;
import fr.acinq.eclair.UInt64;
import fr.acinq.eclair.crypto.KeyManager;
import fr.acinq.eclair.crypto.LocalKeyManager;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.router.helpers.MockAuditDb;
import fr.acinq.eclair.router.helpers.MockChannelsDb;
import fr.acinq.eclair.router.helpers.MockNetworkDb;
import fr.acinq.eclair.router.helpers.MockPaymentsDb;
import fr.acinq.eclair.router.helpers.MockPeersDb;
import fr.acinq.eclair.router.helpers.MockPendingRelayDb;
import fr.acinq.eclair.wire.Color;
import scala.None$;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.collection.immutable.Set;
import scala.concurrent.Await;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import static scala.collection.JavaConverters.asScalaIteratorConverter;
import static scala.collection.JavaConverters.asScalaSetConverter;
import android.util.Log;

public class RouterTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create();
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void basicTest() {
    new JavaTestKit(system) {{

      NodeParams params = makeNodeParams();
      ActorRef watcher = system.actorOf(Props.create(NodeParams.MockActor.class));

      final Props routerProps = Props.create(Router.class, params, watcher, None$.MODULE$);
      final ActorRef router = system.actorOf(routerProps);

      new Within(duration("30 seconds")) {

        protected void run() {
          PrivateKey privKey1 = Crypto.PrivateKey$.MODULE$.fromBase58("cRumXueoZHjhGXrZWeFoEBkeDHu2m8dW5qtFBCqSAt4LDR2Hnd8Q", Base58.Prefix$.MODULE$.SecretKeyTestnet());
          PrivateKey privKey2 = Crypto.PrivateKey$.MODULE$.fromBase58("cVuzKWCszfvjkoJyUasvsrRdECriz8hSd1BDinRNzytwnXmX7m1g", Base58.Prefix$.MODULE$.SecretKeyTestnet());

          PublicKey source = privKey1.publicKey();
          PublicKey target = privKey2.publicKey();

          Seq<Seq<PaymentRequest.ExtraHop>> extraEdges = asScalaIteratorConverter(new ArrayList<Seq<PaymentRequest.ExtraHop>>().iterator()).asScala().toSeq();
          Set<Crypto.PublicKey> ignoredNodes = asScalaSetConverter(new TreeSet<Crypto.PublicKey>()).asScala().toSet();
          Set<ChannelDesc> ignoredEdges = asScalaSetConverter(new TreeSet<ChannelDesc>()).asScala().toSet();

          RouteParams params = new RouteParams(21000, 0.05, 6, 144);

          Timeout timeout = new Timeout(Duration.create(30, "seconds"));

          scala.concurrent.Future<Object> response = Patterns.ask(router, new RouteRequest(source, target, 500000, extraEdges, ignoredNodes, ignoredEdges, false, params), timeout);

          try {

            RouteResponse rr = (RouteResponse) Await.result(response, Duration.create(30, "seconds"));

            Assert.assertEquals(rr.hops().size(), 13);

          } catch (Exception e) {
            Assert.fail(e.getMessage());
          }
        }

      };

    }};
  }

  private NodeParams makeNodeParams(){
    KeyManager manager = new LocalKeyManager(BinaryData.apply("02999fa724ec3c244e4da52b4a91ad421dc96c9a810587849cd4b2469313519c73"), Block.LivenetGenesisBlock().hash());
    FiniteDuration someDuration = new FiniteDuration(10, TimeUnit.SECONDS);

    return NodeParams.apply(
      manager,
      "alias",
      new Color((byte) 0x01,(byte) 0x01,(byte) 0x01),
      asScalaIteratorConverter(new ArrayList<InetSocketAddress>().iterator()).asScala().toList(),
      BinaryData.empty(),
      BinaryData.empty(),
      NodeParams.emptyImmutableMap(),
      123L,
      UInt64.apply(123L),
      1,
      1,
      1,
      1,
      1,
      1,
      1,
      1,
      1,
      1D,
      1D,
      new MockChannelsDb(),
      new MockPeersDb(),
      new MockNetworkDb(),
      new MockPendingRelayDb(),
      new MockPaymentsDb(),
      new MockAuditDb(),
      someDuration,
      someDuration,
      someDuration,
      someDuration,
      true,
      1D,
      1D,
      true,
      BinaryData.empty(),
      Byte.MIN_VALUE,
      someDuration,
      null,
      someDuration,
      1,
      10D,
      123L
    );
  }

}
