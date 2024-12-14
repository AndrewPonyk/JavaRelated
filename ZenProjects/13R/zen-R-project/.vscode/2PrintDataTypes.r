x <- 10.5
print(x)
print(class(x))

y <- 10L  # L suffix makes it integer
print(y)
print(class(y))

text <- "Hello R!"
print(text)
print(class(text))

bool <- TRUE
print(bool)
print(class(bool))

# Complex
comp <- 3 + 2i
print(comp)
print(class(comp))

# Vector - collection of elements
vec <- c(1, 2, 3, 4, 5)
print(vec)
print(class(vec))

# List - ordered collection of different objects
list_data <- list("a" = 1, "b" = TRUE, "c" = "hello")
print(list_data)
print(class(list_data))

mat <- matrix(c(1, 2, 3, 4), nrow = 2, ncol = 2)
print(mat)
print(class(mat))

# Data Frame - table with different data types
df <- data.frame(
  name = c("John", "Jane"),
  age = c(25, 30),
  student = c(TRUE, FALSE)
)
print(df)
print(class(df))

# Factor - categorical variables
gender <- factor(c("male", "female", "male"))
print(gender)
print(class(gender))
