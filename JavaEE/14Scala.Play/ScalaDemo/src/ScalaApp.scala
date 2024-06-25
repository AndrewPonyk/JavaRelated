/**
 * Created by aponyk on 26.05.2016.
 */
object ScalaApp {
  def main(args: Array[String]): Unit = {
    println("hello scala")
    val definedByValφ = "123"
    var integerval = 10
    var byte = integerval.asInstanceOf[Long]
    println(byte)

    var multiLineStrin =
      """hello it is a multile line string
          in scala
        """

    println("|" + multiLineStrin + "|")

    //println(s"2/3 is appros ${2.0/3}")

    printf("Exaample of formatting :%4$20.3f ", 10, "str", 0L, 2.342)
    // positioning is simple % 4$ s - 4$ means fourth argument in list , 20f means right justify to 20 chars, .3 means 3 digits after comma
   println()
    println(factorial(5))

  }

  //create factorial function
  def factorial(n: Int): Int = {
    if (n == 0) 1
    else n * factorial(n - 1)

  }
}



/*
* n older versions of Scala (Scala 2), you could define a method with no
*  return type (a procedure) without using an equals sign. This was known as procedure syntax.
* Here's an example of a main method defined using procedure syntax:
def main(args: Array[String]) {
  println("Hello, world!")
}


In Scala 2.9, the procedure syntax was deprecated, and in Scala 2.11, it was removed.
In Scala 3, procedure syntax is no longer supported. You need to explicitly specify the return type of the method (Unit for a procedure)
* and use an equals sign before the method body. Here's how you can define a main method in Scala 3:
*
* def main(args: Array[String]): Unit = {
  println("Hello, world!")
}
*
* */