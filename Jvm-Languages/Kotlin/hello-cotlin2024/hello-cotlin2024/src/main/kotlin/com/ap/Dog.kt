
class Dog : Animal() {
    fun bark() {
        println("Woof Woof")
    }

    override fun eat() {
        println("Dog is eating")
    }

    fun sleep() {
        println("Dog is sleeping")
    }

    //create static method
    companion object {
        fun create(): Dog {
            return Dog()
        }
        fun printCurrentDateTime() {
            println("Current date time is: ${java.time.LocalDateTime.now()}")
        }
    }
}