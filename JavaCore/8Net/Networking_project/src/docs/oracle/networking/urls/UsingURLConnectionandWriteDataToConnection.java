package docs.oracle.networking.urls;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Random;

public class UsingURLConnectionandWriteDataToConnection {
	
	
	private static final String USER_AGENT = "Mozilla/5.0";
	private static final String downloads = System.getProperty("user.dir")
			+ "/downloads/";

	public static void main(String[] args) {

		// read text from connection
		urlConnectionReader();

		// write data to connection
		uRLConnectionPost();
	}

	// the easier way to send f
	public static void urlConnectionPostUsingApacheCommons(){
		
	}
	
	// POST REQUEST
	public static void uRLConnectionPost() {
		try {
			String paramToSend = "fubar";
			String fileToUpload = downloads + "/Lenta_za_lentoju_Rington.mp3";
			
			URL url = new URL("http://localhost:9000/Application/processPost");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			// add reuqest header
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", USER_AGENT);
			con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

			// Send post request
			con.setDoOutput(true);
			con.setRequestProperty("Content-Type",
					"multipart/form-data; boundary=" + 100);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());

			PrintWriter writer = new PrintWriter(con.getOutputStream());

			writer.println("--" + 100);
			writer.println("Content-Disposition: form-data; name=\"paramToSend\"");
			writer.println("Content-Type: text/plain; charset=UTF-8");
			writer.println();
			writer.println(paramToSend);

			writer.println("--" + 100);
			writer.println("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileToUpload +  "\"");
			writer.println("Content-Type: text/plain; charset=UTF-8");
			writer.println();

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(fileToUpload), "UTF-8"));
			
			
			for (String line; (line = reader.readLine()) != null;) {
				writer.println(line);
			}
			
			writer.println("--" + 100 + "--");
			writer.close();
			reader.close();
			// Connection is lazily executed whenever you request any status.
			int responseCode = con.getResponseCode();
			System.out.println(responseCode); // Should be 200
		} catch (MalformedURLException e) {

			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	// HTTP GET request
	private void sendGet() throws Exception {

		String url = "http://www.google.com/search?q=mkyong";

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();

		// optional default is GET
		con.setRequestMethod("GET");

		// add request header
		con.setRequestProperty("User-Agent", USER_AGENT);

		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'GET' request to URL : " + url);
		System.out.println("Response Code : " + responseCode);

		BufferedReader in = new BufferedReader(new InputStreamReader(
				con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		// print result
		System.out.println(response.toString());

	}

	public static void urlConnectionReader() {
		URLConnection conn = null;
		InputStream in = null;
		try {
			URL playApp = new URL("http://localhost:9000");

			conn = playApp.openConnection();
			in = conn.getInputStream();

			BufferedReader bufReader = new BufferedReader(
					new InputStreamReader(in));

			String inputLine;
			while ((inputLine = bufReader.readLine()) != null)
				System.out.println(inputLine);

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}
