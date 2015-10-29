package part6Nashorn;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class HelloNashorn {
    public static void main(String[] arg) throws ScriptException {
        System.out.println("Hello world Nashorn");


        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("nashorn");

        engine.eval("function p(s) { print(s) }");
        engine.eval("p('Hello Nashorn');");
        engine.eval("var mas = [1,2,3,4,5]");
        engine.eval("var spliced=mas.splice(1,3)");

        jdk.nashorn.api.scripting.ScriptObjectMirror mas = (jdk.nashorn.api.scripting.ScriptObjectMirror)engine.get("mas");
        jdk.nashorn.api.scripting.ScriptObjectMirror spliced = (jdk.nashorn.api.scripting.ScriptObjectMirror)engine.get("spliced");

        // [1,5]
        mas.entrySet().forEach(e->{
            System.out.println(e.getValue());
        });
        System.out.println();

        // [2,3,4]
        spliced.entrySet().forEach(e->{
            System.out.println(e);
        });
    }
}
