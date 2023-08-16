package dataloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class JSONLoader {

    private String fileName;
    private ArrayList<String> requiredFields;
    private ArrayList<String> otherFields;
    private HashMap<String, HashMap> data;
    private String primaryKey;

    public JSONLoader(String fileName, String primaryKey, ArrayList<String> requiredFields, ArrayList<String> otherFields) {
        this.fileName = fileName;
        this.data = new HashMap<>();
        this.requiredFields = requiredFields;
        this.otherFields = otherFields;
        this.primaryKey = primaryKey;
        loadJsonData();
    }

    private void loadJsonData() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(new File(fileName));
            JsonNode rows = rootNode.get("rows");

            if (rows != null && rows.isArray()) {
                for (JsonNode row : rows) {
                    HashMap<String, String> rowData = new HashMap<>();
                    for (String field : requiredFields){
                        if (row.hasNonNull(field)){
                            rowData.put(field, row.get(field).asText());
                        } else {
                            throw new NullPointerException("Row with email " + row.get("email").asText("Unknown") + " is missing mandatory field " + field + " and will not be loaded.");
                        }
                    }
                    for (String field : otherFields){
                        if (row.hasNonNull(field)){
                            rowData.put(field, row.get(field).asText(null));
                        }
                    }

                    data.put(row.get(primaryKey).asText(), rowData);



                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public HashMap<String, HashMap> getData() {
        return data;
    }

}
