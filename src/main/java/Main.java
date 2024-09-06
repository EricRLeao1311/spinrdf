import java.util.List;
import java.util.ArrayList;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.FileManager;
import org.apache.jena.rdf.model.StmtIterator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.FileReader;

import org.spinrdf.constraints.ConstraintViolation;
import org.spinrdf.constraints.SPINConstraints;
import org.spinrdf.inference.SPINInferences;
import org.spinrdf.system.SPINLabels;
import org.spinrdf.system.SPINModuleRegistry;
import org.spinrdf.util.JenaUtil;

public class Main {

    public static void main(String[] args) {

        // Initialize system functions and templates
        SPINModuleRegistry.get().init();

        // Load RDF without load
        InputStream file = FileManager.get().open("src/main/java/input_output/OnDBTuning_com_regra_e_sem_carga.rdf");
        Model baseModel = ModelFactory.createDefaultModel();
        baseModel.read(file, null);

        // Load JSON schema and queries
        JsonObject jsonSchema = null;
        try {
            FileReader reader = new FileReader("src/main/java/input_output/schema_and_queries_2.json");
            jsonSchema = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();
        } catch (FileNotFoundException e) {
            System.out.println("JSON file not found.");
            return;
        } catch (IOException e) {
            System.out.println("Error reading JSON file.");
            return;
        }

        // Map tables to RDF
        OntModel ontModel = JenaUtil.createOntologyModel(OntModelSpec.OWL_MEM, baseModel);
        JsonArray tables = jsonSchema.getAsJsonArray("tables");
        JsonArray all_columns = jsonSchema.getAsJsonArray("columns");

        // Primeiro, criaremos os recursos das colunas
        for (int i = 0; i < all_columns.size(); i++) {
            JsonObject separate_columns = all_columns.get(i).getAsJsonObject();
            addColumnsToRDF(ontModel, separate_columns);
        }
        
        // Agora, criaremos os recursos das tabelas referenciando as colunas
        for (int i = 0; i < tables.size(); i++) {
            JsonObject table = tables.get(i).getAsJsonObject();
            String tableId = table.get("id").getAsString();
            String tableName = table.get("name").getAsString();
            JsonArray columns = table.getAsJsonArray("columns");
        
            // Cria o recurso com identificador local para que rdf:ID seja usado
            Resource tableResource = ontModel.createResource("#" + tableId);
            tableResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasTableName"), tableName);
            tableResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.w3.org/2002/07/owl#NamedIndividual"));
            tableResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#Table"));
        
            // Associa as colunas à tabela referenciando-as pelo ID
            for (int j = 0; j < columns.size(); j++) {
                String columnId = columns.get(j).getAsString();
                tableResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#constitutedOf"), columnId);
            }        
        }

        // Process DML statements from JSON and add to RDF
        JsonArray dmlStatements = jsonSchema.getAsJsonArray("dmlStatements");

        for (int i = 0; i < dmlStatements.size(); i++) {
            JsonObject dml = dmlStatements.get(i).getAsJsonObject();
            String dmlId = dml.get("id").getAsString();
            String query = dml.get("query").getAsString();

            // Format the query to include carriage returns (&#xD;)
            String formattedQuery = query.replace("\n", "&#xD;\n");

            // Create the DMLStatement resource
            Resource dmlResource = ontModel.createResource("#" + dmlId, ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#DMLStatement"));
            
            // Add the formatted query description
            dmlResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasDMLDescription"), formattedQuery);
            
            // Add the necessary types
            dmlResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#QueryStatement"));
            dmlResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.w3.org/2002/07/owl#NamedIndividual"));
        }

        

        // Save the modified RDF with the added table data
        try {
            FileOutputStream out = new FileOutputStream("src/main/java/input_output/Modified_OnDBTuning.rdf");
            ontModel.write(out, "RDF/XML-ABBREV");
            out.close();
        } catch (FileNotFoundException e) {
            System.out.println("Arquivo não encontrado.");
        } catch (Exception e) {
            System.out.println("Ocorreu um erro ao escrever no arquivo.");
        }

        // Create and add Model for inferred triples
        Model newTriples = ModelFactory.createDefaultModel();
        ontModel.addSubModel(newTriples);

        // Register locally defined functions
        SPINModuleRegistry.get().registerAll(ontModel, null);

        // Run all inferences
        SPINInferences.run(ontModel, newTriples, null, null, false, null);
        System.out.println("Inferred triples: " + newTriples.size());

        // Save the new model with inferred triples to a file
        try {
            FileOutputStream out = new FileOutputStream("src/main/java/input_output/Result.xml");
            newTriples.write(out);
            out.close();
        } catch (FileNotFoundException e) {
            System.out.println("Arquivo não encontrado.");
        } catch (Exception e) {
            System.out.println("Ocorreu um erro ao escrever no arquivo.");
        }

        // Process the generated XML to create dictionaries
        Model model = ModelFactory.createDefaultModel();
        model.read("src/main/java/input_output/Result.xml");

        List<Map<String, Object>> dictionaries = new ArrayList<>();

        // Criar um mapa para armazenar a relação coluna-tabela
        Map<String, String> columnTableMap = new HashMap<>();

        // Preencher o mapa com base nas tabelas e colunas do JSON
        for (int i = 0; i < tables.size(); i++) {
            JsonObject table = tables.get(i).getAsJsonObject();
            String tableName = table.get("name").getAsString();
            JsonArray columns = table.getAsJsonArray("columns");

            // Mapear cada coluna para a respectiva tabela
            for (int j = 0; j < columns.size(); j++) {
                String columnId = columns.get(j).getAsString();
                columnTableMap.put(columnId, tableName);
            }
        }

        // Processar índices hipotéticos
        StmtIterator hypotheticalIndexIter = model.listStatements(null, model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasHypotheticalIndexName"), (String) null);
        processIndexes(hypotheticalIndexIter, model, dictionaries, "HypotheticalIndex", columnTableMap);

        // Processar índices parciais
        StmtIterator partialIndexIter = model.listStatements(null, model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasIndexName"), (String) null);
        processIndexes(partialIndexIter, model, dictionaries, "PartialIndex", columnTableMap);

        // Write dictionaries to a JSON file
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (FileWriter writer = new FileWriter("src/main/java/input_output/Result.json")) {
            gson.toJson(dictionaries, writer);
        } catch (IOException e) {
            System.out.println("Ocorreu um erro ao escrever o JSON.");
        }
    }

    // Método para processar tanto índices hipotéticos quanto parciais
    private static void processIndexes(StmtIterator iter, Model model, List<Map<String, Object>> dictionaries, String indexType, Map<String, String> columnTableMap) {
        while (iter.hasNext()) {
            Statement stmt = iter.nextStatement();
            Resource resource = stmt.getSubject();
            Map<String, Object> dictionary = new HashMap<>();

            // Extrair id (nome local do recurso)
            String id = resource.getLocalName();
            dictionary.put("id", id);

            // Extrair nome do índice (diferente para índices parciais e hipotéticos)
            String indexName = stmt.getLiteral().getString();
            dictionary.put("indexName", indexName);

            // Extrair bônus ou expressão condicional, dependendo do tipo de índice
            if (indexType.equals("HypotheticalIndex")) {
                String bonus = resource.getProperty(model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasHypBonus")).getString();
                dictionary.put("bonus", bonus);
                String rule = resource.getProperty(model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#isGeneratedBy")).getResource().getLocalName();
                dictionary.put("rule", rule);
                // Processar SQLs relacionados (caso haja mais de um)
                StmtIterator sqlIter = resource.listProperties(model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#originatesIndSpec"));
                List<String> sqlList = new ArrayList<>();
                while (sqlIter.hasNext()) {
                    // Para cada entrada de originatesIndSpec, capturar o nome local do recurso (SQL)
                    String sql = sqlIter.nextStatement().getResource().getLocalName();
                    sqlList.add(sql);
                }
                // Armazenar a lista de SQLs no dicionário
                dictionary.put("sqls", sqlList);

            } else if (indexType.equals("PartialIndex")) {
                String condition = resource.getProperty(model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasAConditionalExpression")).getString();
                dictionary.put("condition", condition);
                String rule = resource.getProperty(model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#isGeneratedBy")).getResource().getLocalName();
                dictionary.put("rule", rule);
                // Processar SQLs relacionados (caso haja mais de um)
                StmtIterator sqlIter = resource.listProperties(model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#originatesIndSpec"));
                List<String> sqlList = new ArrayList<>();
                while (sqlIter.hasNext()) {
                    // Para cada entrada de originatesIndSpec, capturar o nome local do recurso (SQL)
                    String sql = sqlIter.nextStatement().getResource().getLocalName();
                    sqlList.add(sql);
                }
                // Armazenar a lista de SQLs no dicionário
                dictionary.put("sqls", sqlList);
            }

            // Processar colunas para índices hipotéticos
            StringBuilder columns = new StringBuilder();
            // Itera sobre as propriedades hypIndexesColumn
            StmtIterator columnsIter = resource.listProperties(model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hypIndexesColumn"));

            // Verifica se columnsIter está vazio ou nulo, e se sim, usa FirstColumn
            if (columnsIter == null || !columnsIter.hasNext()) {
                // Se não houver hypIndexesColumn, busca as colunas a partir de FirstColumn
                columnsIter = resource.listProperties(model.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#FirstColumn"));
            }
            String tableName = null;

            while (columnsIter.hasNext()) {
                String columnId = columnsIter.nextStatement().getResource().getLocalName();

                if (columns.length() > 0) {
                    columns.append(", ");
                }
                columns.append(columnId);

                // Atribuir a tabela associada à coluna
                if (tableName == null) {
                    tableName = columnTableMap.get(columnId);
                }
            }

            // Criar o comando SQL com o nome da tabela correta
            String command = "CREATE INDEX " + id + " ON " + tableName + "(" + columns.toString() + ")";
            dictionary.put("command", command);

            // Adicionar dicionário à lista
            dictionaries.add(dictionary);
        }
    }


    // Método separado para adicionar colunas ao RDF
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

            // Cria o recurso da coluna
            Resource columnResource = ontModel.createResource("#" + columnId);
            columnResource.addProperty(ontModel.createProperty("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#hasColumnName"), columnName);
            columnResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.w3.org/2002/07/owl#NamedIndividual"));
            columnResource.addProperty(ontModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ontModel.createResource("http://www.semanticweb.org/ana/ontologies/2016/4/tuning#Column"));
        }
    }
}
