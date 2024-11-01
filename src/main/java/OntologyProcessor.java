import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.FileManager;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.spinrdf.inference.SPINInferences;
import org.spinrdf.system.SPINModuleRegistry;
import org.spinrdf.util.JenaUtil;

public class OntologyProcessor {

    public static JsonArray process(JsonObject jsonSchema) throws Exception {
        // Initialize system functions and templates
        SPINModuleRegistry.get().init();

        // Load RDF without load
        InputStream file = FileManager.get().open("src/main/java/input_output/OnDBTuning_com_regra_e_carga1(Eric)3.rdf");
        if (file == null) {
            throw new Exception("RDF file not found.");
        }
        Model baseModel = ModelFactory.createDefaultModel();
        baseModel.read(file, null);

        // Map tables to RDF
        OntModel ontModel = JenaUtil.createOntologyModel(OntModelSpec.OWL_MEM, baseModel);
        JsonArray tables = jsonSchema.getAsJsonArray("tables");
        JsonArray all_columns = jsonSchema.getAsJsonArray("columns");

        // First, create column resources
        for (int i = 0; i < all_columns.size(); i++) {
            JsonObject separate_columns = all_columns.get(i).getAsJsonObject();
            addColumnsToRDF(ontModel, separate_columns);
        }

        // Now, create table resources referencing the columns
        for (int i = 0; i < tables.size(); i++) {
            JsonObject table = tables.get(i).getAsJsonObject();
            String tableId = table.get("id").getAsString();
            String tableName = table.get("name").getAsString();
            JsonArray columns = table.getAsJsonArray("columns");

            // Create the resource with local identifier so that rdf:ID is used
            Resource tableResource = ontModel.createResource("#" + tableId);
            tableResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasTableName"), tableName);
            tableResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#ReferencedTable"));
            tableResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#Table"));

            // Associate columns to the table by referencing their IDs
            for (int j = 0; j < columns.size(); j++) {
                String columnId = columns.get(j).getAsString();
                tableResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#constitutedOf"), columnId);
            }
        }

        // Process DML statements from JSON and add to RDF
        JsonArray dmlStatements = jsonSchema.getAsJsonArray("dmlStatements");

        // Create a map from DML IDs to their queries
        Map<String, String> dmlIdToQuery = new HashMap<>();
        for (int i = 0; i < dmlStatements.size(); i++) {
            JsonObject dml = dmlStatements.get(i).getAsJsonObject();
            String dmlId = dml.get("id").getAsString();
            String query = dml.get("query").getAsString();
            dmlIdToQuery.put(dmlId, query);

            // Format the query to include carriage returns (&#xD;)
            String formattedQuery = query.replace("\n", "&#xD;\n");

            // Create the DMLStatement resource directly with the main type 'DMLStatement'
            Resource dmlResource = ontModel.createResource("#" + dmlId, ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#QueryStatement"));

            // Add the property hasDMLDescription
            dmlResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasDMLDescription"), formattedQuery);

            // Add the secondary type (optional) as QueryStatement
            dmlResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#DMLStatement"));
        }

        // Create and add Model for inferred triples
        Model newTriples = ModelFactory.createDefaultModel();
        ontModel.addSubModel(newTriples);

        // Register locally defined functions
        SPINModuleRegistry.get().registerAll(ontModel, null);

        // Run all inferences
        SPINInferences.run(ontModel, newTriples, null, null, false, null);

        // Create a map to store the column-table relationship
        Map<String, String> columnTableMap = new HashMap<>();

        // Fill the map based on the tables and columns from JSON
        for (int i = 0; i < tables.size(); i++) {
            JsonObject table = tables.get(i).getAsJsonObject();
            String tableName = table.get("name").getAsString();
            JsonArray columns = table.getAsJsonArray("columns");

            // Map each column to its respective table
            for (int j = 0; j < columns.size(); j++) {
                String columnId = columns.get(j).getAsString();
                columnTableMap.put(columnId, tableName);
            }
        }

        // Process the inferred triples and build the result JSON
        JsonArray resultArray = processInferredTriples(newTriples, columnTableMap, dmlIdToQuery);

        return resultArray;
    }

    // Separate method to add columns to RDF
    private static void addColumnsToRDF(OntModel ontModel, JsonObject separate_columns) {
        JsonElement columns_ID_Element = separate_columns.get("id");
        JsonElement columns_Name_Element = separate_columns.get("name");

        // Check if "name" and "id" are arrays or primitives
        JsonArray columns_ID;
        JsonArray columns_Name;

        if (columns_ID_Element.isJsonArray()) {
            columns_ID = columns_ID_Element.getAsJsonArray();
        } else {
            columns_ID = new JsonArray();
            columns_ID.add(columns_ID_Element.getAsString());
        }

        if (columns_Name_Element.isJsonArray()) {
            columns_Name = columns_Name_Element.getAsJsonArray();
        } else {
            columns_Name = new JsonArray();
            columns_Name.add(columns_Name_Element.getAsString());
        }

        // Now proceed with your existing logic
        for (int j = 0; j < columns_ID.size(); j++) {
            String columnId = columns_ID.get(j).getAsString();
            String columnName = columns_Name.get(j).getAsString();

            // Create the column resource
            Resource columnResource = ontModel.createResource("#" + columnId);
            columnResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasColumnName"), columnName);
            columnResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasDataType"), "integer"); // You might need to adjust this
            columnResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#ReferencedColumn"));
            columnResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#Column"));
        }
    }

    private static JsonArray processInferredTriples(Model newTriples, Map<String, String> columnTableMap, Map<String, String> dmlIdToQuery) {
        List<Map<String, Object>> indexList = new ArrayList<>();

        Pattern indexPattern = Pattern.compile("<http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(.+?)> <http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(hasIndexName|hasHypotheticalIndexName)> \"(.+?)\" \\.");
        Pattern dmlPattern = Pattern.compile("<http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(.+?)> <http://www.semanticweb.org/ana/ontologies/2016/4/tuning#originatesIndSpec> <#(.+?)> \\.");
        Pattern rulePattern = Pattern.compile("<http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(.+?)> <http://www.semanticweb.org/ana/ontologies/2016/4/tuning#isGeneratedBy> <http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(.+?)> \\.");
        Pattern bonusPattern = Pattern.compile("<http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(.+?)> <http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasHypBonus> \"([^\"]+?)\"(?:\\^\\^<[^>]+>)? \\.");
        Pattern columnPattern = Pattern.compile("<http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(.+?)> <http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(FirstColumn|SecondColumn|ThirdColumn)> <(.*?)#(.+?)> \\.");
        Pattern wherePattern = Pattern.compile("<http://www.semanticweb.org/ana/ontologies/2016/4/tuning#(.+?)> <http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasAConditionalExpression> \"(WHERE\\s+[^\\s]+.*)\" \\.");

        Map<String, List<String>> dmlMap = new HashMap<>();
        Map<String, String> ruleMap = new HashMap<>();
        Map<String, String> bonusMap = new HashMap<>();
        Map<String, List<String>> firstColumnMap = new HashMap<>();
        Map<String, List<String>> secondColumnMap = new HashMap<>();
        Map<String, List<String>> thirdColumnMap = new HashMap<>();
        Map<String, String> whereMap = new HashMap<>();

        // Build a map from table names to DML IDs that reference them
        Map<String, List<String>> tableToDmlIds = new HashMap<>();

        for (Map.Entry<String, String> entry : dmlIdToQuery.entrySet()) {
            String dmlId = entry.getKey();
            String query = entry.getValue();

            List<String> tableNamesInQuery = extractTableNamesFromQuery(query);

            for (String tableName : tableNamesInQuery) {
                tableToDmlIds.computeIfAbsent(tableName, k -> new ArrayList<>()).add(dmlId);
            }
        }

        // Convert the inferred triples model to a string for processing
        StringWriter stringWriter = new StringWriter();
        newTriples.write(stringWriter, "N-TRIPLE");
        String inferredTriples = stringWriter.toString();

        // First pass to read DMLs, Rules, Bonuses, and Column Associations
        String[] lines = inferredTriples.split("\n");
        for (String line : lines) {
            Matcher dmlMatcher = dmlPattern.matcher(line);
            if (dmlMatcher.matches()) {
                String indexId = dmlMatcher.group(1);
                String dmlId = dmlMatcher.group(2);
                dmlMap.computeIfAbsent(indexId, k -> new ArrayList<>()).add(dmlId);
            }

            Matcher ruleMatcher = rulePattern.matcher(line);
            if (ruleMatcher.matches()) {
                String indexId = ruleMatcher.group(1);
                String rule = ruleMatcher.group(2);
                ruleMap.put(indexId, rule);
            }

            Matcher bonusMatcher = bonusPattern.matcher(line);
            if (bonusMatcher.matches()) {
                String indexId = bonusMatcher.group(1);
                String bonus = bonusMatcher.group(2);
                bonusMap.put(indexId, bonus);
            }

            Matcher columnMatcher = columnPattern.matcher(line);
            if (columnMatcher.matches()) {
                String indexId = columnMatcher.group(1);
                String column = columnMatcher.group(4);

                // Add the column to the corresponding map (First, Second, or Third Column)
                String columnType = columnMatcher.group(2);
                switch (columnType) {
                    case "FirstColumn":
                        firstColumnMap.computeIfAbsent(indexId, k -> new ArrayList<>()).add(column);
                        break;
                    case "SecondColumn":
                        secondColumnMap.computeIfAbsent(indexId, k -> new ArrayList<>()).add(column);
                        break;
                    case "ThirdColumn":
                        thirdColumnMap.computeIfAbsent(indexId, k -> new ArrayList<>()).add(column);
                        break;
                }
            }

            Matcher whereMatcher = wherePattern.matcher(line);
            if (whereMatcher.matches()) {
                String indexId = whereMatcher.group(1);
                String condition = whereMatcher.group(2);
                whereMap.put(indexId, condition);
            }
        }

        // Second pass to read indices and associate DMLs, Rules, Bonuses, Columns, and Commands
        for (String line : lines) {
            Matcher indexMatcher = indexPattern.matcher(line);
            if (indexMatcher.matches()) {
                String indexId = indexMatcher.group(1);
                String indexName = indexMatcher.group(3);

                Map<String, Object> indexData = new HashMap<>();
                indexData.put("id", indexId); // Using indexId as 'id' to ensure uniqueness
                indexData.put("indexName", indexName);

                // Add the list of SQLs (DMLs) if they exist for this index
                List<String> dmlIds = dmlMap.getOrDefault(indexId, new ArrayList<>());
                List<String> sqls = new ArrayList<>();
                for (String dmlId : dmlIds) {
                    String query = dmlIdToQuery.getOrDefault(dmlId, dmlId); // If the query is not found, keep the ID
                    sqls.add(query);
                }
                indexData.put("sqls", sqls);

                // Add the Rule if it exists for this index
                if (ruleMap.containsKey(indexId)) {
                    indexData.put("rule", ruleMap.get(indexId));
                }

                // Add the Bonus if it exists for this index
                if (bonusMap.containsKey(indexId)) {
                    indexData.put("bonus", bonusMap.get(indexId));
                }

                // Build the index creation command
                List<String> columns = new ArrayList<>();
                columns.addAll(firstColumnMap.getOrDefault(indexId, new ArrayList<>()));
                columns.addAll(secondColumnMap.getOrDefault(indexId, new ArrayList<>()));
                columns.addAll(thirdColumnMap.getOrDefault(indexId, new ArrayList<>()));

                if (!columns.isEmpty()) {
                    String tableName = columnTableMap.get(columns.get(0));
                    boolean allColumnsMatch = columns.stream().allMatch(col -> {
                        String tbl = columnTableMap.get(col);
                        return tbl != null && tbl.equals(tableName);
                    });

                    if (allColumnsMatch && tableName != null) {
                        String columnList = columns.stream()
                                .map(col -> col.startsWith("Column_") ? col.substring("Column_".length()) : col)
                                .collect(Collectors.joining(", "));
                        String command = "CREATE INDEX " + indexName + " ON " + tableName + "(" + columnList + ")";

                        if (whereMap.containsKey(indexId)) {
                            command += " " + whereMap.get(indexId);
                        }

                        indexData.put("command", command);
                        indexData.put("tableName", tableName); // Store tableName for bonus calculation
                    } else {
                        // Handle error: Columns do not all belong to the same table
                    }
                } else {
                    // Handle error: No columns found for index
                }

                // Calculate bonus for partial indices if missing
                String rule = (String) indexData.get("rule");
                if ("RuleSimplePartialIndex".equals(rule) && !indexData.containsKey("bonus")) {
                    String tableName = (String) indexData.get("tableName");
                    List<String> dmlIdsReferencingTable = tableToDmlIds.getOrDefault(tableName, new ArrayList<>());
                    int totalQueriesReferencingTable = dmlIdsReferencingTable.size();

                    @SuppressWarnings("unchecked")
                    List<String> sqlsList = (List<String>) indexData.get("sqls");
                    int numberOfQueriesSuggestingIndex = sqlsList.size();

                    if (totalQueriesReferencingTable > 0) {
                        double bonus = (double) numberOfQueriesSuggestingIndex / totalQueriesReferencingTable;
                        indexData.put("bonus", String.valueOf(bonus));
                    } else {
                        // Handle warning: No queries referencing table found
                    }
                }

                // Completeness check before adding to indexList
                boolean isComplete = true;
                List<String> missingFields = new ArrayList<>();

                // Define required fields
                if (!indexData.containsKey("id")) {
                    isComplete = false;
                    missingFields.add("id");
                }
                if (!indexData.containsKey("command")) {
                    isComplete = false;
                    missingFields.add("command");
                }
                if (!indexData.containsKey("bonus")) {
                    isComplete = false;
                    missingFields.add("bonus");
                }
                if (!indexData.containsKey("sqls") || ((List<?>) indexData.get("sqls")).isEmpty()) {
                    isComplete = false;
                    missingFields.add("sqls");
                }

                if (isComplete) {
                    indexList.add(indexData);
                } else {
                    // Handle incomplete index data
                }
            }
        }

        // Remove duplicates, keeping the index with more information
        Map<String, Map<String, Object>> uniqueIndexMap = new HashMap<>();
        for (Map<String, Object> indexData : indexList) {
            String id = (String) indexData.get("id");
            if (!uniqueIndexMap.containsKey(id) || indexData.size() > uniqueIndexMap.get(id).size()) {
                uniqueIndexMap.put(id, indexData);
            }
        }

        // Convert the unique index map to a JsonArray
        JsonArray resultArray = new JsonArray();
        for (Map<String, Object> indexData : uniqueIndexMap.values()) {
            JsonObject indexJson = new JsonObject();
            indexJson.addProperty("id", (String) indexData.get("id"));
            indexJson.addProperty("indexName", (String) indexData.get("indexName"));
            indexJson.addProperty("command", (String) indexData.get("command"));
            indexJson.addProperty("bonus", (String) indexData.get("bonus"));
            indexJson.addProperty("rule", (String) indexData.get("rule"));

            // Convert the list of SQLs to JsonArray
            @SuppressWarnings("unchecked")
            List<String> sqlsList = (List<String>) indexData.get("sqls");
            JsonArray sqlsJsonArray = new JsonArray();
            for (String sql : sqlsList) {
                sqlsJsonArray.add(sql);
            }
            indexJson.add("sqls", sqlsJsonArray);

            resultArray.add(indexJson);
        }

        return resultArray;
    }

    // Method to extract table names from a SQL query
    private static List<String> extractTableNamesFromQuery(String query) {
        List<String> tableNames = new ArrayList<>();

        // Simple regex to extract table names from FROM clause
        Pattern fromPattern = Pattern.compile("(?i)\\bFROM\\s+([\\w]+)");
        Matcher matcher = fromPattern.matcher(query);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            tableNames.add(tableName);
        }

        // Also consider JOINs
        Pattern joinPattern = Pattern.compile("(?i)JOIN\\s+([\\w]+)");
        Matcher joinMatcher = joinPattern.matcher(query);
        while (joinMatcher.find()) {
            String tableName = joinMatcher.group(1);
            tableNames.add(tableName);
        }

        return tableNames;
    }
}
