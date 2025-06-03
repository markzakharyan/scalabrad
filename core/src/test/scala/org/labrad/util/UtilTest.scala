package org.labrad.util

import org.scalatest.funsuite.AnyFunSuite

class UtilTest extends AnyFunSuite {

  test("interpolateEnvironmentVars interpolates defined values into strings") {
    val result = Util.interpolateEnvironmentVars(
      "%NODE_NAME% server",
      env = Map("NODE_NAME" -> "foo")
    )
    assert(result == "foo server")
  }

  test("interpolateEnvironmentVars fails if variable is not defined") {
    intercept[Exception] {
      Util.interpolateEnvironmentVars("%NODE_NAME% server", env = Map())
    }
  }
}
