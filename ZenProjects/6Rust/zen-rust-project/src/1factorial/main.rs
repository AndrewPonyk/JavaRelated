fn main() {
    println!("Hello, world!");
    println!("Factorial of 5 is {}", factorial(5));
}

fn factorial(n: u64) -> u64 {
    match n {
        0 | 1 => 1,
        _ => n * factorial(n - 1),
    }
}
