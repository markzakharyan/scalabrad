package org.labrad.data

import java.nio.ByteOrder
import java.util.{Date, Random}
import org.labrad.types._
import org.scalatest.funsuite.AnyFunSuite

class GetterTest extends AnyFunSuite {
  test("doubleArrayGetter without units") {
    val d = Data("*v")
    d.setArraySize(2)
    d(0).setValue(0.5)
    d(1).setValue(1.5)
    assert(d.get[Array[Double]].toSeq == Seq(0.5, 1.5))
  }

  test("doubleArrayGetter with empty units") {
    val d = Data("*v[]")
    d.setArraySize(2)
    d(0).setValue(0.5)
    d(1).setValue(1.5)
    assert(d.get[Array[Double]].toSeq == Seq(0.5, 1.5))
  }

}
