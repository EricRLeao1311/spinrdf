import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Main2 {
    public static JsonArray sendJsonRequest(JsonObject jsonObject) {
        JsonArray jsonArray = new JsonArray();
        try {
            // The URL of the API endpoint
            URL url = new URL("http://localhost:8000/api");

            // Create an HTTP connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Convert JsonObject to JSON string
            Gson gson = new Gson();
            String jsonInputString = gson.toJson(jsonObject);

            // Write the JSON data to the output stream
            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                outputStream.write(input, 0, input.length);
            }

            // Read the response from the API
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            // Parse the response JSON into a JsonArray
            jsonArray = gson.fromJson(response.toString(), JsonArray.class);

            // Close the connection
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Return the JsonArray
        return jsonArray;
    }

    public static void main(String[] args) {
        // Example usage
        JsonObject jsonSchema = null;
        try {
            FileReader reader = new FileReader("src/main/java/input_output/schema_and_queries.json");
            jsonSchema = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();
        } catch (IOException e) {
            System.out.println("Error reading JSON file: " + e.getMessage());
            return;
        }

        JsonArray result = sendJsonRequest(jsonSchema);
        System.out.println(result);
    }
}
