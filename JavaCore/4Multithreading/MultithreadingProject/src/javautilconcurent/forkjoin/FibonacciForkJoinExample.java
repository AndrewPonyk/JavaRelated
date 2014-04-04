package javautilconcurent.forkjoin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class FibonacciForkJoinExample {
	public static void main(String[] args) {
/*		System.out.println(fib(1));
		System.out.println(fib(4));
		System.out.println(fib(8));
		System.out.println(fib(10));
		*/
		
		// using FibonacciForkJoin
		Integer[] result = {0};
		Integer n = 7;
		ForkJoinPool pool = new ForkJoinPool();
		FibonacciForkJoin fibonacciForkJoin = new FibonacciForkJoin(n, result);
		
		pool.invoke(fibonacciForkJoin);
		
		System.out.println(result[0]);
		System.out.println("end of mdain");
		

	}
	
	public static int fib(int n){
		if (n < 0)
			throw new IllegalArgumentException("n must be > 0");
		
		if (n == 1 || n == 0) {
			return n;
		}else {
			return fib(n - 1) + fib(n - 2);
		}
	}
	
	//the recursive Fibonacci algorithm is extremely expensive, requiring time O(2^n)
	//It also performs a huge amount of redundant work because it computes many Fibonnaci
	//values from scratch many times. A simple linear-time iterative approach which
	//calculates each value of fib successively can avoid these issues:
	public static int fibNonRecursive(int n) {
		int prev1=0, prev2=1;
		
		for (int i = 0; i < n; i++) {
			int savePrev1 = prev1;
			prev1 = prev2;
			prev2 = savePrev1 + prev2;
		}
		return prev1;
	}
	
}

class FibonacciForkJoin extends RecursiveAction{
	Integer n ;
	Integer[] result;
	
	public FibonacciForkJoin(Integer n , Integer[] result) {
		this.n = n;
		this.result = result;
	}
	
	@Override
	protected void compute() {
		if(n.equals(1) || n.equals(0)){
			result[0] += n;
		}else if(n>1){
			List<FibonacciForkJoin> tasks = new ArrayList<>();
			tasks.add(new FibonacciForkJoin(n-1, result));
			tasks.add(new FibonacciForkJoin(n-2, result));
			invokeAll(tasks);
		}
	}
}

class Obj {
	public Integer val = 0;
	
	public Obj(Integer val) {
		this.val = val;
	}
}