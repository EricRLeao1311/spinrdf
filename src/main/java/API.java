import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class API implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set the response message
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        String requestBody = stringBuilder.toString();

        // Parse the input JSON
        Gson gson = new Gson();
        JsonObject inputJson = gson.fromJson(requestBody, JsonObject.class);
        JsonArray jsonArray = null;
        try {
            jsonArray = OntologyProcessor.process(inputJson);
        } catch (Exception e) {
            System.out.println("An error occurred during processing: " + e.getMessage());
            e.printStackTrace();
        }
        String response = gson.toJson(jsonArray);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(response.getBytes());
        outputStream.close();

    }
}
