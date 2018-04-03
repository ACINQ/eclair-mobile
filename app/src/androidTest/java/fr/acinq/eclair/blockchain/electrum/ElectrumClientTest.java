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

package fr.acinq.eclair.blockchain.electrum;

import android.util.Log;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetSocketAddress;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.bitcoin.package$;

public class ElectrumClientTest {

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
      final InetSocketAddress address = new InetSocketAddress("testnetnode.arihanc.com", 51001);
      final Props props = Props.create(ElectrumClient.class, address, system.dispatcher());
      final ActorRef subject = system.actorOf(props);

      new Within(duration("3 seconds")) {
        protected void run() {
          subject.tell(new ElectrumClient.AddStatusListener(getRef()), getRef());
          expectMsgClass(duration("1 second"), ElectrumClient.ElectrumReady.class);
        }
      };

      new Within(duration("3 seconds")) {
        protected void run() {
          subject.tell(new ElectrumClient.GetTransaction(BinaryData.apply("c5efb5cbd35a44ba956b18100be0a91c9c33af4c7f31be20e33741d95f04e202")), getRef());
          ElectrumClient.GetTransactionResponse response = expectMsgClass(duration("1 second"), ElectrumClient.GetTransactionResponse.class);
          Assert.assertEquals(response.tx().txid().toString(), "c5efb5cbd35a44ba956b18100be0a91c9c33af4c7f31be20e33741d95f04e202");
        }
      };

      new Within(duration("3 seconds")) {
        protected void run() {
          subject.tell(new ElectrumClient.GetMerkle(BinaryData.apply("c5efb5cbd35a44ba956b18100be0a91c9c33af4c7f31be20e33741d95f04e202"), 1210223L), getRef());
          ElectrumClient.GetMerkleResponse response = expectMsgClass(duration("1 second"), ElectrumClient.GetMerkleResponse.class);
          Assert.assertEquals(response.txid(), BinaryData.apply("c5efb5cbd35a44ba956b18100be0a91c9c33af4c7f31be20e33741d95f04e202"));
          Assert.assertEquals(response.block_height(), 1210223L);
          Assert.assertEquals(response.pos(), 28);
        }
      };

      new Within(duration("3 seconds")) {
        protected void run() {
          subject.tell(new ElectrumClient.HeaderSubscription(getRef()), getRef());
          ElectrumClient.HeaderSubscriptionResponse response = expectMsgClass(duration("1 second"), ElectrumClient.HeaderSubscriptionResponse.class);
          Log.i("received header", response.header().toString());
        }
      };

      new Within(duration("3 seconds")) {
        protected void run() {
          Transaction referenceTx = (Transaction) Transaction.read("0200000003947e307df3ab452d23f02b5a65f4ada1804ee733e168e6197b0bd6cc79932b6c010000006a473044022069346ec6526454a481690a3664609f9e8032c34553015cfa2e9b25ebb420a33002206998f21a2aa771ad92a0c1083f4181a3acdb0d42ca51d01be1309da2ffb9cecf012102b4568cc6ee751f6d39f4a908b1fcffdb878f5f784a26a48c0acb0acff9d88e3bfeffffff966d9d969cd5f95bfd53003a35fcc1a50f4fb51f211596e6472583fdc5d38470000000006b4830450221009c9757515009c5709b5b678d678185202b817ef9a69ffb954144615ab11762210220732216384da4bf79340e9c46d0effba6ba92982cca998adfc3f354cec7715f800121035f7c3e077108035026f4ebd5d6ca696ef088d4f34d45d94eab4c41202ec74f9bfefffffff8d5062f5b04455c6cfa7e3f250e5a4fb44308ba2b86baf77f9ad0d782f57071010000006a47304402207f9f7dd91fe537a26d5554105977e3949a5c8c4ef53a6a3bff6da2d36eff928f02202b9427bef487a1825fd0c3c6851d17d5f19e6d73dfee22bf06db591929a2044d012102b4568cc6ee751f6d39f4a908b1fcffdb878f5f784a26a48c0acb0acff9d88e3bfeffffff02809698000000000017a914c82753548fdf4be1c3c7b14872c90b5198e67eaa876e642500000000001976a914e2365ec29471b3e271388b22eadf0e7f54d307a788ac6f771200");
          BinaryData hash = package$.MODULE$.seq2binaryData(Crypto.sha256().apply(referenceTx.txOut().apply(0).publicKeyScript().data()).data().reverse());
          subject.tell(new ElectrumClient.ScriptHashSubscription(hash, getRef()), getRef());
          ElectrumClient.ScriptHashSubscriptionResponse response = expectMsgClass(duration("1 second"), ElectrumClient.ScriptHashSubscriptionResponse.class);
          System.out.println("received scripthash status " + response.status());
        }
      };
    }};
  }
}

