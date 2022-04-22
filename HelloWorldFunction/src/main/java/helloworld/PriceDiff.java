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
public class PriceDiff implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {
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
            output = this.getDiff();

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

    private String getDiff() throws IOException, ClassNotFoundException {
        /// change the region and credentials to your own region and credentials.
        AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
        String objKeyF = "price_fortress";
        String objKeyH = "price_hktv";

        AmazonS3 s3Client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(this.region)
                .build();

        if (!s3Client.doesObjectExist(this.bucket, objKeyF))
            throw new IOException("fortress data unavailable");
        if (!s3Client.doesObjectExist(this.bucket, objKeyH))
            throw new IOException("hktv mall data unavailable");

        /// retrieve data from the S3 storage
        String dataF = s3Client.getObjectAsString(this.bucket, objKeyF);
        HashMap<String, String> itemsF = (HashMap<String, String>) deserialize(dataF);

        String dataH = s3Client.getObjectAsString(this.bucket, objKeyH);
        HashMap<String, String> itemsH = (HashMap<String, String>) deserialize(dataH);

        String priceFString = itemsF.get("price_fortress");

        String priceHString = itemsH.get("price_hktv");

        float priceDiff = Float.parseFloat(priceFString) - Float.parseFloat(priceHString);

        return String.format("{ \"priceDiff\":\"%.2f\"}",
                priceDiff);
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
