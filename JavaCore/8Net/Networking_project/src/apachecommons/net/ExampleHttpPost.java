package apachecommons.net;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpResponse;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;


public class ExampleHttpPost {

    private static final String downloads = System.getProperty("user.dir")
            + "/downloads/";


    public void postRequest() throws IOException {
        try {


            String url = "http://localhost:9000/Application/processPost";
            HttpClient client = new DefaultHttpClient();

            HttpPost post = new HttpPost(url);
            post.setHeader(
                    "User-Agent",
                    " Mozilla/5.0 (iPad; U; CPU OS 3_2_1 like Mac OS X; en-us) AppleWebKit/531.21.10 (KHTML, like Gecko) Mobile/7B405");

            MultipartEntity reqEntity = new MultipartEntity();
            reqEntity.addPart("param1", new StringBody("hello1"));
            reqEntity.addPart("file", new FileBody(new File(downloads + "Lenta_za_lentoju_Rington.mp3")));

            post.setEntity(reqEntity);

            HttpResponse response = client.execute(post);

            System.out.println("Response Code : "
                    + response.getStatusLine().getStatusCode());
            BufferedReader rd = new BufferedReader(new InputStreamReader(response
                    .getEntity().getContent()));

            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            System.out.println(result);
        } catch (HttpHostConnectException e) {
            e.printStackTrace();
        }

    }

    public static void main(String args[]) throws Exception {
        new ExampleHttpPost().postRequest();
    }
}