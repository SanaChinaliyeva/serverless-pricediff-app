package helloworld;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Handler for requests to Lambda function.
 */
public class HKTVMallWatch implements RequestHandler<Object, Object> {
    final String accessKey = "AKIA4HX6ROSVIOG7M2LZ";
    final String secretKey = "OkNEUZ6nScx2YYfwP3mZXNC1UAh/WyxPSz8GprQH";
    final String bucket = "hkbu.19201761";
    final String region = "us-east-1";  /// change it if your selected region is different

    public static String loadContent(String urlString) {
        byte[] buffer = new byte[80*1024];
        String content = new String();
        try {
            URL url = new URL(urlString);
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.connect();
            BufferedReader r = new BufferedReader(new
                    InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")));
            String line;
            while ((line = r.readLine()) != null) {
                content += line;
            }
        } catch (IOException e) {
            content = e.getMessage() + ": " + urlString;
        }
        return content;
    }

    public APIGatewayProxyResponseEvent handleRequest(final Object input, final Context context) {
        int status = 200;
        try {
            // Download the current price data from the fortress web site and parse the data in an HTML dom tree.
            String data = new String();
            String html = loadContent("https://www.comp.hkbu.edu.hk/~mandel/comp4057/hktvmall.html");
            Document doc = Jsoup.parse(html);
            data = doc.select("span.discount").first().ownText();

            float price = Float.parseFloat(data.replaceAll("[^\\d.]", ""));

            HashMap<String, String> items = new HashMap<>();
            items.put("price_hktv", Float.toString(price));

            String timestamp = new Date().getTime() + "";
            items.put("timestamp", timestamp);

            /// convert (serialize) the hash map into a string
            String serialized = serialize(items);

            /// change the region and credentials to your own regions and credentials.
            String region = this.region;    /// replace this string with your selected region if you use another one
            AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
            String bucket = this.bucket;
            String objKey = "price_hktv";

            /// Serialize the hash map and write the data to S3
            AmazonS3 s3Client = AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(region)
                    .build();

            s3Client.putObject(bucket, objKey, serialized);

        } catch (IOException e) {
            e.printStackTrace();    ///the printed stack trace will be displayed in the debug console only.
            status = 500;
        }

        /// put the json string to the response object
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(headers);
        response.setStatusCode(status);

        return response;
    }

    private static String serialize(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

}
