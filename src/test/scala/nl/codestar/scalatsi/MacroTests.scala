package nl.codestar.scalatsi

import org.scalatest.{FlatSpec, Matchers}
import nl.codestar.scalatsi.TypescriptType._

case class Person(name: String, age: Int)

class MacroTests extends FlatSpec with Matchers with DefaultTSTypes {
  "The case class to TypeScript type macro" should "be able to translate a simple case class" in {
    case class Person(name: String, age: Int)
    TSType.fromCaseClass[Person] shouldBe TSType.interface("IPerson", "name" -> TSString, "age" -> TSNumber)
  }

  it should "handle optional types" in {
    case class TestOptional(opt: Option[Long])
    TSType.fromCaseClass[TestOptional] shouldBe TSType.interface("ITestOptional", "opt" -> TSUnion.of(TSNumber, TSUndefined))
  }

  it should "handle nested definitions" in {
    case class A(foo: Boolean)
    case class B(a: A)

    implicit val tsA: TSIType[A] = TSType.fromCaseClass

    TSType.fromCaseClass[B] shouldBe TSType.interface("IB", "a" -> tsA.get)
  }

  it should "handle sealed traits" in {
    sealed trait FooOrBar
    case class Foo(foo: String) extends FooOrBar
    case class Bar(bar: Int)    extends FooOrBar

    import nl.codestar.scalatsi.dsl._
    implicit val tsFoo = TSType.fromCaseClass[Foo] + ("type" -> "Foo")
    implicit val tsBar = TSType.fromCaseClass[Bar] + ("type" -> "Bar")

    tsFoo shouldBe TSType.interface("IFoo", "foo" -> TSString, "type" -> TSLiteralString("Foo"))
    tsBar shouldBe TSType.interface("IBar", "bar" -> TSNumber, "type" -> TSLiteralString("Bar"))

    TSType.fromSealed[FooOrBar] shouldBe TSType.alias("FooOrBar", TSTypeReference("IFoo") | TSTypeReference("IBar"))
  }

  it should "handle sealed traits with a non-named mapping" in {
    sealed trait FooOrBar
    case class Foo(foo: String) extends FooOrBar
    case class Bar(bar: Int)    extends FooOrBar

    import nl.codestar.scalatsi.dsl._
    implicit val tsFoo = TSType.fromCaseClass[Foo]
    implicit val tsBar = TSType.sameAs[Bar, Int]

    tsFoo shouldBe TSType.interface("IFoo", "foo" -> TSString)
    tsBar.get shouldBe TSNumber

    TSType.fromSealed[FooOrBar] shouldBe TSType.alias("FooOrBar", TSTypeReference("IFoo") | TSNumber)
  }

  it should "handle sealed traits with recursive definitions" in {
    sealed trait LinkedList
    case object Nil extends LinkedList
    case class Node(value: Int, next: LinkedList = Nil)

    implicit val nilType: TSType[Nil.type] = TSType(TSNull)
    implicit val llType: TSType[Node]      = TSType(TSNull | TSTypeReference("ILinkedList"))

    TSType.fromSealed[LinkedList] shouldBe TSType.alias("LinkedList", TSNull | TSTypeReference("INode"))
  }

  it should "not compile if a nested definition is missing" in {

    """{
       case class A(foo: Boolean)
       case class B(a: A)
       TSIType.fromCaseClass[B]
    }""".stripMargin shouldNot compile
  }
}
