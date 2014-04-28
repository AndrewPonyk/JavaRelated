package vogellapack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.imageio.plugins.common.InputStreamAdapter;

public class GetLinksFromPage {

	public static void main(String[] args) {
		String pageUrl = "http://onlinecare.wds.co";
		
		try {
			BufferedReader reader = 
					new BufferedReader(new InputStreamReader( (new URL(pageUrl).openStream() ) ));
			
			String line = "";
			String pageText = "";
			while ((line= reader.readLine()) != null) {
				pageText += line;
			}
			
			System.out.println(pageText);
			
			Pattern pattern = Pattern.compile( "(?i)<a(\\s[^>]+)>(.+?)</a>" );
			Matcher matcher = pattern.matcher(pageText);
			
			System.out.println("\n\nLinks on the page :");
			while (matcher.find()) {
				System.out.println(matcher.group());
			}
		
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

}
