package org.labrad.registry

import org.labrad.{RegistryServerProxy, TlsMode}
import org.labrad.annotations._
import org.labrad.data._
import org.labrad.manager.ManagerUtils
import org.labrad.registry._
import org.labrad.types._
import org.labrad.util.Await
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.Tag
import org.scalatest.concurrent.Waiters
import org.scalatest.time.SpanSugar._
import scala.collection._

class RemoteStoreTest extends AnyFunSuite with Matchers with Waiters {

  import ManagerUtils._

  def withManagers()(func: (ManagerInfo, ManagerInfo, ManagerInfo) => Unit): Unit = {
    withManager() { root =>
      val store1 = new RemoteStore(root.host, root.port, root.credential, tls = TlsMode.OFF)
      withManager(registryStore = Some(store1)) { leaf1 =>
        val store2 = new RemoteStore(root.host, root.port, root.credential, tls = TlsMode.OFF)
        withManager(registryStore = Some(store2)) { leaf2 =>
        }
      }
    }
  }

  test("remote registry gets message when key is created") {
    withManagers() { (root, leaf1, leaf2) =>
      withClient(leaf1) { c1 =>
        withClient(leaf2) { c2 =>

          val r1 = new RegistryServerProxy(c1)
          val r2 = new RegistryServerProxy(c2)

          val w = new Waiter

          val msgId = 1234

          c2.addMessageListener {
            case Message(src, ctx, `msgId`, data) =>
              w { assert(data == Cluster(Str("a"), Bool(false), Bool(true))) }
              w.dismiss
          }

          Await(r1.mkDir("test"))
          Await(r1.cd("test"))

          Await(r2.notifyOnChange(msgId, true))
          Await(r2.cd("test"))

          Await(r1.set("a", Str("test")))
          w.await(timeout(10.seconds))
        }
      }
    }
  }

  test("remote registry gets message when key is changed") {
    withManagers() { (root, leaf1, leaf2) =>
      withClient(leaf1) { c1 =>
        withClient(leaf2) { c2 =>

          val r1 = new RegistryServerProxy(c1)
          val r2 = new RegistryServerProxy(c2)

          val w = new Waiter

          val msgId = 1234

          c2.addMessageListener {
            case Message(src, ctx, `msgId`, data) =>
              w { assert(data == Cluster(Str("a"), Bool(false), Bool(true))) }
              w.dismiss
          }

          Await(r1.mkDir("test"))
          Await(r1.cd("test"))
          Await(r1.set("a", Str("first")))

          Await(r2.notifyOnChange(msgId, true))
          Await(r2.cd("test"))

          Await(r1.set("a", Str("second")))
          w.await(timeout(10.seconds))
        }
      }
    }
  }

  test("remote registry gets message when key is deleted") {
    withManagers() { (root, leaf1, leaf2) =>
      withClient(leaf1) { c1 =>
        withClient(leaf2) { c2 =>

          val r1 = new RegistryServerProxy(c1)
          val r2 = new RegistryServerProxy(c2)

          val w = new Waiter

          val msgId = 1234

          c2.addMessageListener {
            case Message(src, ctx, `msgId`, data) =>
              w { assert(data == Cluster(Str("a"), Bool(false), Bool(false))) }
              w.dismiss
          }

          Await(r1.mkDir("test"))
          Await(r1.cd("test"))
          Await(r1.set("a", Str("first")))

          Await(r2.notifyOnChange(msgId, true))
          Await(r2.cd("test"))

          Await(r1.del("a"))
          w.await(timeout(10.seconds))
        }
      }
    }
  }
}
