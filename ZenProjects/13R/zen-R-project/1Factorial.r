
# Function to calculate factorial
factorial <- function(n) {
  if (n < 0) {
    return("Factorial is not defined for negative numbers")
  }
  if (n == 0 || n == 1) {
    return(1)
  }
  result <- 1
  for (i in 1:n) {
    result <- result * i
  }
  return(result)
}

# Example usage
num <- 5
cat("Factorial of", num, "is:", factorial(num))
