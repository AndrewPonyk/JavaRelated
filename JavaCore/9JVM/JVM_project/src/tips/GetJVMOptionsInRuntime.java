package tips;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

public class GetJVMOptionsInRuntime {

	public static void main(String[] args) {
		
		
		//java -X    - prints all java non-standard options 
		
		//
		// java -client -XX:+PrintFlagsFinal Benchmark  - prints all jvm DEVELOPER (-XX) options
		
		
		//// java -client -XX:+PrintFlagsFinal Benchmark | FINDSTR erm  -     print options related to permanent memory (in Windows FINDSTR is equivalent to grep)
		
		
		// System.out.println(System.getProperty("sun.java.command"));
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
		List<String> aList = bean.getInputArguments();

		for (int i = 0; i < aList.size(); i++) {
			System.out.println(aList.get(i));
		}
	}
}