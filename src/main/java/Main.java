import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

public class Main {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        server.createContext("/api", new API());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port 8000");
        
    }


    public static void oldMain(String[] args) {
        // Load JSON schema and queries
        JsonObject jsonSchema = null;
        try {
            FileReader reader = new FileReader("src/main/java/input_output/schema_and_queries.json");
            jsonSchema = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();
        } catch (IOException e) {
            System.out.println("Error reading JSON file: " + e.getMessage());
            return;
        }

        try {
            // Call the static processing method and get the result
            JsonArray resultArray = OntologyProcessor.process(jsonSchema);

            // Optionally, write the result to a file or handle it as needed
            try (FileWriter jsonWriter = new FileWriter("src/main/java/input_output/Result.json")) {
                Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                gson.toJson(resultArray, jsonWriter);
                System.out.println("Processing complete. Result saved to Result.json");
            } catch (IOException e) {
                System.out.println("Error writing result to JSON file: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("An error occurred during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
