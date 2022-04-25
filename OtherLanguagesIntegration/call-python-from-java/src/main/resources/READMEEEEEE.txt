USE EGG FILE (setting env variable PYTHONPATH)
    1) Create egg file
    ....src\main\resources\python\fibonacci_egg>python setup.py bdist_egg

    creates file: fibonacci-0.1-py3.8.egg
    2) Use it from python command line
    Set python classpath: >>set PYTHONPATH=D:\mygit\JavaRelated\OtherLanguagesIntegration\call-python-from-java\src\main\r
    esources\python\fibonacci_egg\dist\fibonacci-0.1-py3.8.egg

    run 'python'
    >>> from fibonacci_pack.fibonacci_function_class import *
    >>> print(Fibonacci(4))
     (намучився з тими імпортами)
-------------------------------
-------------------------------
USE egg file DYNAMICALLY ADD to sys.path
    import sys
    print("test")
    # must be '\\' in python also '\' is reserved
    egg_path='D:\\mygit\JavaRelated\\OtherLanguagesIntegration\\call-python-from-java\\src\\main\\resources\python\\fibonacci_egg\dist\\fibonacci-0.1-py3.8.egg'
    print(sys.path)
    from fibonacci_pack.fibonacci_function_class import *
    print(Fibonacci(5))

