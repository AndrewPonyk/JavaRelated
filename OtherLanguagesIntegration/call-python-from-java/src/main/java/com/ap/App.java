package com.ap;

import javax.script.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringWriter;

public class App {
    public static void main(String[] args) throws FileNotFoundException, ScriptException {
        //executePythonScriptAndGetStringOutput();
        executeFunctionFromPythonFileAndGetInteger();
    }

    private static void executeFunctionFromPythonFileAndGetInteger() throws FileNotFoundException, ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("python");
        engine.eval(new FileReader(resolvePythonScriptPath("python/fibonacci_function.py")));
        engine.eval("result = Fibonacci(6)");
        Object result = engine.get("result");
        System.out.println("result = " + result + " type of result:" +result.getClass()) ;
    }

    /**
     * Inside script we have print("hello world");
     * this "hello world" is saved to {@link StringWriter}
     * @throws ScriptException
     * @throws FileNotFoundException
     */
    private static void executePythonScriptAndGetStringOutput() throws ScriptException, FileNotFoundException {
        StringWriter writer = new StringWriter();
        ScriptContext context = new SimpleScriptContext();
        context.setWriter(writer);

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("python");
        engine.eval(new FileReader(resolvePythonScriptPath("python/print_hello_world.py")), context);
        System.out.println(writer.toString());
    }

    private static void printAvailableScriptEngines(){
        ScriptEngineManagerUtils.listEngines();
    }

    private static String resolvePythonScriptPath(String filename) {
        File file = new File("src/main/resources/" + filename);
        return file.getAbsolutePath();
    }
}
