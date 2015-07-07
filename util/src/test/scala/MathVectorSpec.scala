import info.fotm.util.MathVector
import org.scalatest._

class MathVectorSpec extends FlatSpec with Matchers {

  implicit val comparer = new org.scalactic.Equality[MathVector] {
    override def areEqual(a: MathVector, b: Any): Boolean =
        b.isInstanceOf[MathVector] && a.coords == b.asInstanceOf[MathVector].coords
  }

  "MathVector" should "add vectors correctly" in {
    (MathVector(1.0, 1.0) + MathVector(2.0, 2.0)) should equal (MathVector(3.0, 3.0))
  }

  it should "throw IllegalArgumentException for vectors of different cardinality" in {
    an [IllegalArgumentException] should be thrownBy {
      MathVector(1.0, 1.0) + MathVector(1.0)
    }
  }

  it should "subtract vectors correctly" in {
    (MathVector(1.0, 1.0) - MathVector(2.0, 2.0)) should equal (MathVector(-1.0, -1.0))
  }

  it should "calc dist between vectors correctly" in {
    (MathVector(1.0, 1.0) distTo MathVector(2.0, 2.0)) should equal (Math.sqrt(2))
  }

  it should "divide vector by number correctly" in {
    (MathVector(1.0, 1.0) / 2) should equal (MathVector(.5, .5))
  }

  it should "multiply vector by a number correctly" in {
    (MathVector(1.0, 1.0) * 2) should equal (MathVector(2, 2))
  }

  it should "multiply vector by a zero correctly" in {
    (MathVector(1.0, 1.0) * 0) should equal (MathVector(0, 0))
  }

  it should "calc vector length correctly" in {
    MathVector(3.0, 4.0).length should equal (5.0)
  }

  it should "normalize vector correctly" in {
    //MathVector(2.0, 0, 0).normalize should equal (MathVector(1.0, 0, 0))
  }
  it should "unary minus should be applied correctly" in {
    (-MathVector(1.0, 1.0, 1.0)) should equal (MathVector(-1.0, -1.0, -1.0))
  }
  //little change

  it should "calculate average correctly" in {
    MathVector.avg(Seq(MathVector(1,2), MathVector(3,6))) should equal (MathVector(2.0,4.0))
  }

  it should "the triangle inequality must be carried" in {
    val v1 = MathVector(1,2)
    val v2 = MathVector(3,4)
    val v3 = MathVector(2,1)
    ((v1 distTo v2) + (v2 distTo v3) > (v1 distTo v3)) should equal (true)
  }
}
