import sys
print("test")
# must be '\\' in python also '\' is reserved
egg_path='D:\\mygit\JavaRelated\\OtherLanguagesIntegration\\call-python-from-java\\src\\main\\resources\python\\fibonacci_egg\dist\\fibonacci-0.1-py3.8.egg'
print(sys.path)
from fibonacci_pack.fibonacci_function_class import *
print(Fibonacci(5))