package staff;

import java.util.Map;

public class GetCallStackfromJavaCode {
	public static void main(String[] args) {
		System.out.println("Getting call stack of running program");
		StackTraceDumper.dumpAllStackTraces();
	}
}

class StackTraceDumper {
	public static void dumpAllStackTraces() {
		for (Map.Entry<Thread, StackTraceElement[]> entry : Thread
				.getAllStackTraces().entrySet()) {
			System.out.println(entry.getKey().getName() + ":");
			for (StackTraceElement element : entry.getValue())
				System.out.println("\t" + element);
		}
	}
}
