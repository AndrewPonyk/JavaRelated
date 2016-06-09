/**
  * Created by aponyk on 26.05.2016.
  */
object ScalaApp {
  def main(args: Array[String]) {
    println("hello scala");
    val definedByValφ = "123";
    var integerval = 10;
    var byte = integerval.asInstanceOf[Long];
    println(byte);


    var multiLineStrin =
      """hello it is a multile line string
        in scala
      """;

    println("|" + multiLineStrin + "|");

    println(s"2/3 is appros ${2.0/3}");


    printf("Example of formatting :%4$20.3f ", 10, "str", 0L, 2.342);
    // positioning is simple % 4$ s - 4$ means fourth argument in list , 20f means right justify to 20 chars, .3 means 3 digits after comma




  }
}
