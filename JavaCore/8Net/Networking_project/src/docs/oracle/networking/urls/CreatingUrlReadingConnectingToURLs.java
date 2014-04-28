package docs.oracle.networking.urls;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class CreatingUrlReadingConnectingToURLs {
	private static final String downloads = System.getProperty("user.dir") + "/downloads/";
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// CREATING urls ///////////////////////////////////////////////
		// URL google = new URL("http://google.com");
		URL g = new URL("http", "localhost", 9000, "");

		BufferedReader in = new BufferedReader(new InputStreamReader(
				g.openStream()));

		String inputLine;
		while ((inputLine = in.readLine()) != null)
			System.out.println(inputLine);
		in.close();
		
		
		// download file
		saveFromUrl("http://localhost:9000/public/Lenta_za_lentoju_Rington.mp3", "Lenta_za_lentoju_Rington.mp3");
	}
	

	
	// Download file from url ////////////////////////////////////////////////
	public static void saveFromUrl(String urlString, String filename) {

		try (BufferedInputStream in = new BufferedInputStream(
				new URL(urlString).openStream());
				FileOutputStream fout = new FileOutputStream(downloads + filename)) {

			final byte data[] = new byte[1024];
			int count;

			while ((count = in.read(data, 0, 1024)) != -1) {
				fout.write(data, 0, count);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// 
	}

