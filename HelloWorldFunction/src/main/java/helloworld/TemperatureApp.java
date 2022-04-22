package helloworld;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for requests to Lambda function.
 */
public class TemperatureApp implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {
    final String accessKey = "AKIA4HX6ROSVIOG7M2LZ";
    final String secretKey = "OkNEUZ6nScx2YYfwP3mZXNC1UAh/WyxPSz8GprQH";
    final String bucket = "hkbu.19201761";
    final String region = "us-east-1";  /// change it if your selected region is different

    public APIGatewayProxyResponseEvent handleRequest(final Map<String, Object> input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        String output = "{}";
        int status = 200;
        try {
            Map<String, Object> map = input;
            System.out.println(map.containsKey("body"));

            String body = map.get("body").toString();
            System.out.println(input);
            if (map.containsKey("isBase64Encoded") && map.get("isBase64Encoded").toString().equals("true"))
                body = decode(body);

            String place = body.split("=")[1];

            output = this.getTemperature(place);

        } catch (NullPointerException e) {
            output = "{\"error\":\"invalid input\"}";
        } catch (IOException | ClassNotFoundException e) {
            output = "{\"error\":\"internal error\"}";
        }
        return response
                .withHeaders(headers)
                .withBody(output)
                .withStatusCode(status);
    }

    private String getTemperature(String place) throws IOException, ClassNotFoundException {
        /// change the region and credentials to your own region and credentials.
        AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
        String objKey = "temperature";

        AmazonS3 s3Client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(this.region)
                .build();

        if (!s3Client.doesObjectExist(this.bucket, objKey))
            throw new IOException("data unavailable");

        /// retrieve data from the S3 storage
        String data = s3Client.getObjectAsString(this.bucket, objKey);
        HashMap<String, String> items = (HashMap<String, String>) deserialize(data);

        String temp = items.containsKey(place) ? items.get(place) : "--";
        String lastupdate = items.get("lastupdate");
        String timestamp = items.get("timestamp");

        return String.format("{\"lastupdate\":\"%s\", \"timestamp\":\"%s\", \"place\":\"%s\", \"temperature\":\"%s\"}",
                lastupdate, timestamp, place, temp);
    }

    private static Object deserialize(String s) throws IOException,
            ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    private static String decode(String encodedStr) {
        byte[] decodedBytes = Base64.getDecoder().decode(encodedStr);
        return new String(decodedBytes)
                .replaceAll("\\+", " ")
                .replaceAll("%27", "'");
    }
}
