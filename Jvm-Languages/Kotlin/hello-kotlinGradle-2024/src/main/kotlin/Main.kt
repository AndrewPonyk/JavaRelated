package com.ap

import kotlin.io.readLine
import com.ap.getNthCity

fun main() {
    println("Hello World!!!!A")
    var test : String;

    println("You entered: test")
    println(getNthCity(10));
}

fun getNthCity(n: Int): String {
    val cities = listOf(
        "Tokyo", "Delhi", "Shanghai", "Sao Paulo", "Mumbai", "Mexico City", "Beijing", "Osaka", "Cairo", "New York",
        "Dhaka", "Karachi", "Buenos Aires", "Chongqing", "Istanbul", "Kolkata", "Manila", "Rio de Janeiro", "Shenzhen", "Jakarta",
        "Lahore", "Bangalore", "Moscow", "Nanjing", "Tehran", "Chennai", "Bangkok", "Hyderabad", "Johannesburg", "Hangzhou",
        "Hong Kong", "Bogota", "Ahmedabad", "Ho Chi Minh City", "Riyadh", "Pune", "Singapore", "Santiago", "Alexandria", "St Petersburg",
        "Shijiazhuang", "Ankara", "Jinan", "Casablanca", "Wuhan", "Lima", "Bangkok", "Riyadh", "Hanoi", "Guangzhou"
    )


    return if (n > 0 && n <= cities.size) {
        cities[n - 1]
    } else {
        "City not found"
    }
}