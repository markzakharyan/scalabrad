package org.labrad.manager.auth

import org.labrad.registry._
import org.labrad.util.Files
import org.scalatest.funsuite.FixtureAnyFunSuite

class AuthStoreTest extends FixtureAnyFunSuite {

  case class Fixture(store: AuthStore, globalPassword: String)
  type FixtureParam = Fixture

  def withFixture(test: OneArgTest) = {
    Files.withTempFile { file =>
      val globalPassword = "VerySecurePassword"
      val authStore = AuthStore(file, globalPassword.toCharArray)
      withFixture(test.toNoArgTest(Fixture(authStore, globalPassword)))
    }
  }

  test("addUser") { fix =>
    fix.store.addUser("someuser", isAdmin = true, passwordOpt = None)
    assert(fix.store.listUsers() == Seq(("someuser", true)))
    assert(fix.store.checkUser("someuser"))
  }

  test("addUser fails if user already exists") { fix =>
    fix.store.addUser("someuser", isAdmin = true, passwordOpt = None)
    intercept[Exception] {
      fix.store.addUser("someuser", isAdmin = true, passwordOpt = None)
    }
  }

  test("addUser fails with empty username") { fix =>
    intercept[Exception] {
      fix.store.addUser("", isAdmin = true, passwordOpt = None)
    }
  }

  test("admin flag is stored") { fix =>
    fix.store.addUser("an_admin", isAdmin = true, passwordOpt = None)
    fix.store.addUser("not_an_admin", isAdmin = false, passwordOpt = None)
    assert(fix.store.isAdmin("an_admin"))
    assert(!fix.store.isAdmin("not_an_admin"))
  }

  test("admin flag can be changed") { fix =>
    fix.store.addUser("sometimes_an_admin", isAdmin = true, passwordOpt = None)
    assert(fix.store.isAdmin("sometimes_an_admin"))
    fix.store.setAdmin("sometimes_an_admin", false)
    assert(!fix.store.isAdmin("sometimes_an_admin"))
    fix.store.setAdmin("sometimes_an_admin", true)
    assert(fix.store.isAdmin("sometimes_an_admin"))
  }

  test("checkUserPassword") { fix =>
    fix.store.addUser("user", isAdmin = false, passwordOpt = Some("xyz"))
    assert(fix.store.checkUserPassword("user", "xyz"))
    assert(!fix.store.checkUserPassword("user", "abc"))
  }

  test("checkUserPassword fails if user does not exist") { fix =>
    intercept[Exception] {
      fix.store.checkUserPassword("no_such_user", "abc")
    }
  }

  test("checkUserPassword fails if user has no password") { fix =>
    fix.store.addUser("user", isAdmin = false, passwordOpt = None)
    intercept[Exception] {
      fix.store.checkUserPassword("user", "abc")
    }
  }

  test("checkUserPassword works for global user and password") { fix =>
    assert(fix.store.checkUserPassword("", fix.globalPassword))
  }

  test("removeUser") { fix =>
    fix.store.addUser("user", isAdmin = false, passwordOpt = None)
    assert(fix.store.checkUser("user"))
    fix.store.removeUser("user")
    assert(!fix.store.checkUser("user"))
  }

  test("changePassword") { fix =>
    fix.store.addUser("user", isAdmin = false, passwordOpt = Some("blah"))
    assert(fix.store.checkUserPassword("user", "blah"))
    fix.store.changePassword("user", Some("blah"), Some("woot"), isAdmin = false)
    assert(!fix.store.checkUserPassword("user", "blah"))
    assert(fix.store.checkUserPassword("user", "woot"))
  }

  test("changePassword can add password") { fix =>
    fix.store.addUser("user", isAdmin = false, passwordOpt = None)
    intercept[Exception] {
      fix.store.checkUserPassword("user", "foo")
    }
    fix.store.changePassword("user", Some("ignored"), Some("woot"), isAdmin = false)
    assert(fix.store.checkUserPassword("user", "woot"))
  }

  test("changePassword can clear password") { fix =>
    fix.store.addUser("user", isAdmin = false, passwordOpt = Some("blah"))
    assert(fix.store.checkUserPassword("user", "blah"))
    fix.store.changePassword("user", Some("blah"), None, isAdmin = false)
    intercept[Exception] {
      assert(!fix.store.checkUserPassword("user", "blah"))
    }
  }

  test("changePassword by admin does not require old password") { fix =>
    fix.store.addUser("user", isAdmin = false, passwordOpt = Some("blah"))
    assert(fix.store.checkUserPassword("user", "blah"))
    fix.store.changePassword("user", Some("nope"), Some("woot"), isAdmin = true)
    assert(!fix.store.checkUserPassword("user", "blah"))
    assert(fix.store.checkUserPassword("user", "woot"))
  }
}
