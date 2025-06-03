package org.labrad

import java.io.File
import java.nio.ByteOrder
import java.util.{Date, Random}
import org.labrad.data._
import org.labrad.manager.ManagerUtils
import org.labrad.types._
import org.labrad.util.{Logging, Util}
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.Await
import scala.concurrent.duration._

class ClientTest extends AnyFunSuite {

  def testWithClient(name: String)(func: Client => Unit) = test(name) {
    ManagerUtils.withManager() { m =>
      ManagerUtils.withClient(m) { c =>
        func(c)
      }
    }
  }

  testWithClient("retrieve list of servers from manager") { c =>
    val data = Await.result(c.send("Manager", "Servers" -> Data.NONE), 10.seconds)(0)
    val servers = data.get[Seq[(Long, String)]]
    assert(servers.contains((1, "Manager")))
    assert(servers.contains((2, "Registry")))
  }

  testWithClient("echo random data from manager") { c =>
    val fs = for (i <- 0 until 1000)
      yield c.send("Manager", "Echo" -> Hydrant.randomData)
    for (f <- fs) Await.result(f, 10.seconds)
  }
}
