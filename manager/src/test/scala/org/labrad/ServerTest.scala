package org.labrad

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}
import org.labrad.annotations._
import org.labrad.data._
import org.labrad.manager.ManagerUtils
import org.labrad.util.{Await, Logging}
import org.scalatest.funsuite.FixtureAnyFunSuite
import scala.collection._
import scala.concurrent.duration._

object TestSrv {
  def withServer[T](m: ManagerUtils.ManagerInfo)(body: TestSrv => T) = {
    val s = new TestSrv
    Server.start(s, ServerConfig(m.host, m.port, m.credential))
    try {
      body(s)
    } finally {
      try s.stop() catch { case _: Throwable => }
    }
  }
}

class TestSrv extends Server[TestSrv, TestCtx] with Logging {

  val name = "Scala Test Server"
  val doc = "Basic server to test Scalabrad API."

  // a queue that will receive ids of expired contexts
  val expiredContexts: BlockingQueue[Context] = new LinkedBlockingQueue

  def init(): Unit = log.info("init() called on server.")
  def shutdown(): Unit = log.info("shutdown() called on server.")

  def newContext(context: Context): TestCtx = {
    new TestCtx(cxn, context, expiredContexts)
  }

  @Setting(id = 100, name = "Srv Echo", doc = "setting defined on server")
  def serverEcho(data: Data): Data = {
    log.debug(s"Echo: $data")
    data
  }

  @Setting(id = 1000, name = "Expire Context", doc = "Expire the context in which this request was made")
  def expireContext(rc: RequestContext): Unit = {
    doExpireContext(rc.context)
  }
}


class TestCtx(
  cxn: Connection,
  context: Context,
  expiredContexts: BlockingQueue[Context]
) extends ServerContext with Logging {

  private val registry = mutable.Map.empty[String, Data]

  def init(): Unit = {
    registry("Test") = Str("blah")
    log.debug(s"Context $context created")
  }

  def expire(): Unit = {
    log.debug(s"Context $context expired")
    expiredContexts.offer(context)
  }

  private def makeRequest(server: String, setting: String, data: Data = Data.NONE) =
    Await.result(cxn.send(server, context, setting -> data), 10.seconds)(0)


  //
  // Settings
  //

  @Setting(id = 1,
           name = "Echo",
           doc = "Echoes back any data sent to this setting.")
  def echo(data: Data): Data = {
    log.debug(s"Echo: $data")
    data
  }

  @Setting(id = 2,
           name = "Delayed Echo",
           doc = "Echoes back data after a specified delay.")
  def delayedEcho(@Accept("v[s]") delay: Double, payload: Data): Data = {
    log.debug(s"Delayed Echo ($delay seconds): $payload")
    Thread.sleep((delay*1000).toLong)
    payload
  }

  @Setting(id = 3,
           name = "Set",
           doc = "Sets a key value pair in the current context.")
  def set(key: String, value: Data): Data = {
    log.debug(s"Set: $key = $value")
    registry(key) = value
    value
  }

  @Setting(id = 4,
           name = "Get",
           doc = "Gets a key from the current context.")
  def get(key: String): Data = {
    log.debug(s"Get: $key")
    registry.get(key) match {
      case Some(value) => value
      case None => sys.error(s"Invalid key: $key")
    }
  }

  @Setting(id = 5,
           name = "Get All",
           doc = "Gets all of the key-value pairs defined in this context.")
  def getAll(): Seq[(String, Data)] = {
    log.debug("Get All")
    registry.toSeq.sortBy(_._1)
  }

  @Setting(id = 6,
           name = "Keys",
           doc = "Returns a list of all keys defined in this context.")
  def getKeys(): Seq[String] = {
    log.debug("Keys")
    registry.keys.toSeq.sorted
  }

  @Setting(id = 7,
           name = "Remove",
           doc = "Removes the specified key from this context.")
  def remove(key: String): Unit = {
    log.debug(s"Remove: $key")
    registry -= key
  }

  @Setting(id = 8,
           name = "Get Random Data",
           doc = """Returns random LabRAD data.

                   |If a type is specified, the data will be of that type;
                   |otherwise it will be of a random type.""")
  def getRandomData(typ: String): Data = {
    log.debug(s"Get Random Data: $typ")
    Hydrant.randomData(typ)
  }
  def getRandomData(): Data = {
    log.debug("Get Random Data (no type)")
    Hydrant.randomData
  }

  @Setting(id = 9,
           name = "Get Random Data Remote",
           doc = "Fetches random data by making a request to the python test server.")
  def getRandomDataRemote(typ: Option[String] = None): Data = typ match {
    case Some(typ) =>
      log.debug("Get Random Data Remote: $typ")
      makeRequest("Python Test Server", "Get Random Data", Str(typ))

    case None =>
      log.debug("Get Random Data Remote (no type)")
      makeRequest("Python Test Server", "Get Random Data")
  }

  @Setting(id = 10,
           name = "Forward Request",
           doc = "Forwards a request on to another server, specified by name and setting.")
  def forwardRequest(server: String, setting: String, payload: Data): Data = {
    log.debug("Forward Request: server='$server', setting='$setting', payload=$payload")
    makeRequest(server, setting, payload)
  }

  @Setting(id = 11,
           name = "Test No Args",
           doc = "Test setting that takes no arguments.")
  def noArgs(): Boolean = {
    log.debug("Test No Args")
    true
  }

  @Setting(id = 12,
           name = "Test No Return",
           doc = "Test setting with no return value.")
  def noReturn(data: Data): Unit = {
    log.debug(s"Test No Return: $data")
  }

  @Setting(id = 13,
           name = "Test No Args No Return",
           doc = "Test setting that takes no arguments and has no return value.")
  def noArgsNoReturn(): Unit = {
    log.debug("Test No Args No Return")
  }
}

class ServerTest extends FixtureAnyFunSuite {

  import ManagerUtils._

  case class Fixture(
    server: TestSrv,
    client: Client,
    manager: ManagerInfo
  )
  type FixtureParam = Fixture

  def withFixture(test: OneArgTest) = {
    withManager() { m =>
      TestSrv.withServer(m) { server =>
        withClient(m) { client =>
          withFixture(test.toNoArgTest(Fixture(server, client, m)))
        }
      }
    }
  }

  test("server can log in and log out of manager") { fix =>
    val msg = "This is a test"
    val result = Await(fix.client.send("Scala Test Server", "Echo" -> Str(msg)))
    val Str(s) = result(0)
    assert(s == msg)

    val aVal = 1
    Await(fix.client.send("Scala Test Server", "Set" -> Cluster(Str("a"), UInt(aVal))))
    val result2 = Await(fix.client.send("Scala Test Server", "Get" -> Str("a")))
    val UInt(a) = result2(0)
    assert(a == 1)
  }

  test("can call setting defined on server object") { fix =>
    val msg = "This is a test"
    val result = Await(fix.client.send("Scala Test Server", "Srv Echo" -> Str(msg)))
    val Str(s) = result(0)
    assert(s == msg)
  }

  test("can expire context in a separate packet") { fix =>
    Await(fix.client.send("Scala Test Server", "Set" -> Cluster(Str("a"), Str("test"))))
    val keys = Await(fix.client.send("Scala Test Server", "Keys" -> Data.NONE))(0).get[Seq[String]]
    assert(keys.contains("a"))

    Await(fix.client.send("Scala Test Server", "Expire Context" -> Data.NONE))

    val keys2 = Await(fix.client.send("Scala Test Server", "Keys" -> Data.NONE))(0).get[Seq[String]]
    assert(!keys2.contains("a"))
  }

  test("can expire context within a single packet") { fix =>
    val result = Await(fix.client.send("Scala Test Server",
                                "Set" -> Cluster(Str("a"), Str("test")),
                                "Keys" -> Data.NONE,
                                "Expire Context" -> Data.NONE,
                                "Keys" -> Data.NONE))
    val keys = result(1).get[Seq[String]]
    val keys2 = result(3).get[Seq[String]]
    assert(keys.contains("a"))
    assert(!keys2.contains("a"))
  }

  test("server gets context expiration message when client disconnects") { fix =>
    val ctx = withClient(fix.manager) { client2 =>
      val ctx = client2.newContext.copy(high = client2.id)
      Await(client2.send("Scala Test Server", ctx,
                         "Set" -> Cluster(Str("a"), Str("test"))))
      ctx
    }
    val expiredCtx = fix.server.expiredContexts.poll(1, TimeUnit.SECONDS)
    assert(expiredCtx == ctx)
    val result = Await(fix.client.send("Scala Test Server", ctx,
                                       "Keys" -> Data.NONE))
    val keys = result(0).get[Seq[String]]
    assert(keys == Seq("Test"))  // should just be the test key
  }

}

object TestServer {
  def main(args: Array[String]): Unit = {
    Server.run(new TestSrv, args)
  }
}
