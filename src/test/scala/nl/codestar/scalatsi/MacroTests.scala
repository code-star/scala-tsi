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

  it should "not compile if a nested definition is missing" in {

    """{
       case class A(foo: Boolean)
       case class B(a: A)
       TSIType.fromCaseClass[B]
    }""".stripMargin shouldNot compile
  }

  "The sealed trait/class to Typescript type macro" should "handle sealed traits" in {
    sealed trait FooOrBar
    case class Foo(foo: String) extends FooOrBar
    case class Bar(bar: Int)    extends FooOrBar

    implicit val tsFoo = TSType.fromCaseClass[Foo]
    implicit val tsBar = TSType.fromCaseClass[Bar]

    TSType.fromSealed[FooOrBar] shouldBe TSType.alias("FooOrBar", TSTypeReference("IFoo") | TSTypeReference("IBar"))
  }

  it should "handle sealed abstract classes" in {
    sealed abstract class FooOrBar(tpe: String)
    case class Foo(foo: String) extends FooOrBar("Foo")
    case class Bar(bar: Int)    extends FooOrBar("Bar")

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

    implicit val tsFoo = TSType.fromCaseClass[Foo]
    implicit val tsBar = TSType.sameAs[Bar, Int]

    tsFoo shouldBe TSType.interface("IFoo", "foo" -> TSString)
    tsBar.get shouldBe TSNumber

    TSType.fromSealed[FooOrBar] shouldBe TSType.alias("FooOrBar", TSTypeReference("IFoo") | TSNumber)
  }

  it should "handle sealed traits with recursive definitions" in {
    sealed trait LinkedList
    case object Nil                                     extends LinkedList
    case class Node(value: Int, next: LinkedList = Nil) extends LinkedList

    implicit val nilType: TSType[Nil.type] = TSType(TSNull)
    implicit val llType: TSType[Node]      = TSType.alias("INode", TSNull | TSTypeReference("ILinkedList"))

    TSType.fromSealed[LinkedList] shouldBe TSType.alias("LinkedList", TSNull | TSTypeReference("INode"))
  }

  it should "handle sealed traits without subclasses" in {
    sealed trait Empty

    // Expect a warning here
    TSType.fromSealed[Empty] shouldBe TSNamedType[Empty](TSAlias("IEmpty", TSNever))
  }

  "The default mapping construct" should "use available implicit if in scope" in {
    case class A(foo: String)

    implicit val tsA = TSType[A](TSNumber)

    TSType.mappingOrElse[A](TSType.generateMapping[A]) shouldBe TSType[A](TSNumber)
  }

  it should "use available implicit TSNamedType if in scope" in {
    case class A(foo: String)

    import dsl._
    implicit val tsA: TSNamedType[A] = TSType.fromCaseClass[A] + ("type" -> "A")

    TSType.mappingNamedOrElse[A](TSType.generateNamedMapping[A]) shouldBe TSType.interface(
      "IA",
      "foo"  -> TSString,
      "type" -> TSLiteralString("A")
    )
  }

  it should "use case class generator for case classes" in {
    case class A(foo: String)

    TSType.mappingOrElse[A](TSType.generateMapping[A]) shouldBe TSType.fromCaseClass[A].get
  }

  it should "use sealed trait generator for sealed traits" in {
    sealed trait A
    case class B(foo: String) extends A

    TSType.mappingOrElse[A](TSType.generateMapping[A]) shouldBe TSType.fromSealed[A]
  }

  it should "give a compile error for unsupported types" in {
    class A

    "TSType.getMappingOrUseDefault[A]" shouldNot compile
  }
}
