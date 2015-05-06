package com.documentation.server;

import java.util.ArrayList;

import org.json.JSONObject;

import com.documentation.client.StockPrice;
import com.documentation.client.StockPriceService;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class StockPriceServiceImpl extends RemoteServiceServlet implements StockPriceService {

	@Override
	public ArrayList<StockPrice> getPrices(String[] symbols) {
		StockPrice price = new StockPrice("abc", 3, 1);
		ArrayList<StockPrice> results = new ArrayList<StockPrice>();
		results.add(price);

		return results;
	}

	@Override
	public String getJSON(Integer n) {
/*		JSONObject json = new JSONObject(); // not found in dev mode !!! from eclipse (((( cant find solution use MAVEN !!!
		try {
			json.put("ab", "123");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return json.toString();
*/
		return "I cant use JSONObject on server, some problems with dependencies, use MAVEN!!!";
	}


	public static void main(String[] args) {
		System.out.println("Test");
		/*double c = 1.5;*/
		/*try{
		}
		catch(Throwable e){
			System.out.println("Catched");
		}*/
		//throw new OutOfMemoryError();
	}
}
