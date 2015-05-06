package com.documentation.client;

import com.google.gwt.junit.client.GWTTestCase;

public class StackWatcherTest extends GWTTestCase {

	@Override
	public String getModuleName() {
		return null;
	}

	public void testSome() {
		assertEquals(3, 3);
	}

	public void testStockPriceCtor() {
		String symbol = "XYZ";
		double price = 70.0;
		double change = 2.0;
		double changePercent = 100.0 * change / price;

		StockPrice sp = new StockPrice(symbol, price, change);
		assertNotNull(sp);
		assertEquals(symbol, sp.getSymbol());
		assertEquals(price, sp.getPrice(), 0.001);
		assertEquals(change, sp.getChange(), 0.001);
		assertEquals(changePercent, sp.getChangePercent(), 0.001);


		assertEquals(1.5, 3.0/2);
	}
}