package helloworld;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for requests to Lambda function.
 */
public class GrabberApp implements RequestHandler<Object, Object> {
    final String accessKey = "AKIA4HX6ROSVIOG7M2LZ";
    final String secretKey = "OkNEUZ6nScx2YYfwP3mZXNC1UAh/WyxPSz8GprQH";
    final String bucket = "hkbu.19201761";
    final String region = "us-east-1";  /// change it if your selected region is different

    public APIGatewayProxyResponseEvent handleRequest(final Object input, final Context context) {
        int status = 200;
        try {
            // Download the current weather data from the government RSS web site and parse the data in a XML dom tree.
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document dom = db.parse("http://rss.weather.gov.hk/rss/CurrentWeather.xml");
            // You are suggested to check the content of the xml file.

            // retrieve the last update time using regular expression
            String data = dom.getElementsByTagName("item").item(0).getTextContent();
            Pattern pattern = Pattern.compile("\\d+:\\d+\\sHKT\\s\\d+/\\d+/\\d+");
            Matcher matcher = pattern.matcher(data);
            String lastUpdate = null;
            if (matcher.find())
                lastUpdate = data.substring(matcher.start(), matcher.end());

            // retrieve the necessary part - temperatures of places <table>...</table>.
            data = dom.getElementsByTagName("description").item(1).getTextContent();
            int start = data.indexOf("<table");
            int end = data.indexOf("</table>", start) + 8;
            data = data.substring(start, end);

            // parse the table in a XML dom tree.
            dom = db.parse(new InputSource(new StringReader(data)));

            // get all font nodes that contain the temperature info.
            NodeList nodes = dom.getElementsByTagName("font");

            HashMap<String, String> items = new HashMap<>();
            NodeList tr_nodes = dom.getElementsByTagName("tr");
            System.out.println("Number of rows: " + tr_nodes.getLength());
            for (int i=0; i<tr_nodes.getLength(); i++) {
                String place = tr_nodes.item(i).getFirstChild().getFirstChild().getTextContent();
                String temperature = tr_nodes.item(i).getLastChild().getFirstChild().getTextContent();
                temperature = temperature.substring(0, temperature.indexOf("degrees")).trim();
                items.put(place, temperature);
                System.out.println(place + "=>" + temperature);  /// debug
            }
            items.put("lastupdate", lastUpdate);

            String timestamp = new Date().getTime() + "";
            items.put("timestamp", timestamp);

            /// convert (serialize) the hash map into a string
            String serialized = serialize(items);

            /// change the region and credentials to your own regions and credentials.
            String region = this.region;    /// replace this string with your selected region if you use another one
            AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
            String bucket = this.bucket;
            String objKey = "temperature";

            /// Serialize the hash map and write the data to S3
            AmazonS3 s3Client = AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(region)
                    .build();

            s3Client.putObject(bucket, objKey, serialized);

        } catch (IOException | SAXException | ParserConfigurationException e) {
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
