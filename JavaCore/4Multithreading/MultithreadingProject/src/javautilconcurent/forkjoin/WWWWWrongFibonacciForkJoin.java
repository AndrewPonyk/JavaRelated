package javautilconcurent.forkjoin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

	public class WWWWWrongFibonacciForkJoin{
		public static void main(String[] args) {
			// using FibonacciForkJoin
			Integer result = 0;
			Integer n = 7;
			ForkJoinPool pool = new ForkJoinPool();
			FibonacciForkJoin1 fibonacciForkJoin = new FibonacciForkJoin1(n, result);
			
			pool.invoke(fibonacciForkJoin);
			
			System.out.println(result);   // OUTPUT is 0 , but should be 13
			
			

		}
	}

	class FibonacciForkJoin1 extends RecursiveAction{
		Integer n ;
		Integer result ;
		
		public FibonacciForkJoin1(Integer n , Integer result) {
			this.n = n;
			this.result = result;
		}
		
		@Override
		protected void compute() {
			if(n.equals(1) || n.equals(0)){
				result += n;
			}else if(n>1){
				List<FibonacciForkJoin1> tasks = new ArrayList<>();
				tasks.add(new FibonacciForkJoin1(n-1, result));
				tasks.add(new FibonacciForkJoin1(n-2, result));
				invokeAll(tasks);
			}
		}
	}

	
	
/*// SOlution using AtomicInteger
	public class WWWWWrongFibonacciForkJoin{
		public static void main(String[] args) {
			// using FibonacciForkJoin
			Integer n =   8;
			AtomicInteger result = new AtomicInteger(0);
			ForkJoinPool pool = new ForkJoinPool();
			FibonacciForkJoin1 fibonacciForkJoin = new FibonacciForkJoin1(n, result);
			
			pool.invoke(fibonacciForkJoin);
			
			System.out.println(result);   // OUTPUT is good
			
		}
	}

	class FibonacciForkJoin1 extends RecursiveAction{
		Integer n ;
		AtomicInteger result ;
		
		public FibonacciForkJoin1(Integer n , AtomicInteger result) {
			this.n = n;
			this.result = result;
		}
		
		@Override
		protected void compute() {
			if(n.equals(1) || n.equals(0)){
				//result += n;
				result.addAndGet(n);
			}else if(n>1){
				List<FibonacciForkJoin1> tasks = new ArrayList<>();
				tasks.add(new FibonacciForkJoin1(n-1, result));
				tasks.add(new FibonacciForkJoin1(n-2, result));
				invokeAll(tasks);
			}
		}
	}
*/