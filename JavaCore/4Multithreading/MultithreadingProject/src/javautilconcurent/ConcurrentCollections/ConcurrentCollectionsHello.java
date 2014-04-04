package javautilconcurent.ConcurrentCollections;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrentCollectionsHello {
	private static final String CONCURRENCY_LEVEL_DEFAULT = "50";
	private static final String CONCURRENCY_KEY = "concurrency";
	private ConcurrentMap<Double, Double> sqrtCache = new ConcurrentHashMap<Double, Double>();

	public static void main(String[] args) {
		final ConcurrentCollectionsHello test = new ConcurrentCollectionsHello();
		final int concurrencyLevel = 10;

		final ExecutorService executor = Executors.newFixedThreadPool(3);
		final ExecutorService executor1 = Executors.newFixedThreadPool(3);
		
		executor.execute(new Runnable() {
			
			@Override
			public void run() {
				System.out.println("helre");
				
			}
		});
		
			for (int i = 0; i < concurrencyLevel; i++) {
					final Double d = Double.valueOf(i);
					executor.execute(new Runnable() {
						@Override
						public void run() {
							System.out.printf("sqrt of %s = %s in thread %s%n",
									d, test.getSqrt(d), Thread.currentThread()
											.getName());
							try {
								Thread.sleep(450);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					});
			}
			
			for (int i = 0; i < concurrencyLevel; i++) {
				final Double d = Double.valueOf(i);
				executor1.execute(new Runnable() {
					@Override
					public void run() {
						System.out.printf("sqrt of %s = %s in thread %s%n",
								d, test.getSqrt(d), Thread.currentThread()
										.getName());
						try {
							Thread.sleep(450);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				});
		}
			executor.shutdown();
	}

	public double getSqrt(Double d) {
		Double sqrt = sqrtCache.get(d);
		if (sqrt == null) {
			sqrt = Math.sqrt(d);
			System.out.printf("calculated sqrt of %s = %s%n", d, sqrt);
			Double existing = sqrtCache.putIfAbsent(d, sqrt);
			if (existing != null) {
				System.out
						.printf("discard calculated sqrt %s and use the cached sqrt %s",
								sqrt, existing);
				sqrt = existing;
			}
		}else{
			System.out.printf("getting sqrt from cacheee of %s = %s%n", d, sqrt);
		}
		return sqrt;
	}
}
