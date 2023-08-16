package dataloader;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;

public class ApplicationTU extends TableUpdater{
    public ApplicationTU(String ExternalJSONFile) throws Exception {
        super(ExternalJSONFile, "application", "asset_code");
        requiredFields.add("asset_code");
        requiredFields.add("organisational_unit_id");
        requiredFields.add("kind");
        requiredFields.add("lifecycle_phase");
        requiredFields.add("name");
        requiredFields.add("overall_rating");
        requiredFields.add("provenance");
        requiredFields.add("business_criticality");
        requiredFields.add("entity_lifecycle_status");

        optionalFields.add("description");
        optionalFields.add("parent_asset_code");
        optionalFields.add("planned_retirement_date");
        optionalFields.add("actual_retirement_date");
        optionalFields.add("commission_date");

        this.externalData = new JSONLoader(this.ExternalJSONFile, primaryKey, requiredFields, optionalFields).getData();

    }



    private void processApplication(ArrayList<String> Entries) throws Exception {
        // Processing Org Units

        HashMap<String, String> org_unit_dependencies = new HashMap<>();
        org_unit_dependencies.put("table", "organisational_unit");
        org_unit_dependencies.put("external_identifier", "PARENT ORG");
        org_unit_dependencies.put("internal_external_identifier", "external_id");
        org_unit_dependencies.put("internal_identifier", "id");
        for (String entry : Entries) {
            String value = (String) externalData.get(entry).get("organisational_unit_id");
            String new_data = getDependentField(org_unit_dependencies, value);
            if (new_data != null) {
                BigInteger longData = new BigInteger(new_data);
                externalData.get(entry).put("organisational_unit_id", longData);
            } else {
                throw new Exception("Cannot find Org Unit: " + value);
            }
        }

        // setting is_removed to false
        for (String entry : Entries) {
            externalData.get(entry).put("is_removed", false);
        }


        // adding updated_at
        for (String entry : Entries) {
            externalData.get(entry).put("updated_at", new Timestamp(System.currentTimeMillis()));
        }

    }


    public void insertNewEntries() throws Exception {
        ArrayList<String> entries = findNewEntries();
        processApplication(entries);
        HashMap<String, Integer> internalTypes = getInternalTypes();
        for (String entry : entries) {
            HashMap<String, String> data = externalData.get(entry);
            StringBuilder addQuery = new StringBuilder();
            addQuery.append("INSERT INTO ")
                    .append(tableName)
                    .append(" (")
                    .append(String.join(",", data.keySet()))
                    .append(") VALUES (");
            for (Object value : data.keySet()) {
                addQuery.append("?, ");
            }
            addQuery.deleteCharAt(addQuery.length() - 2).append(")");
            try (PreparedStatement preparedStatement = conn.prepareStatement(addQuery.toString())) {
                int i = 1;

                for (Object value : data.values()) {
                    if (internalTypes != null) {
                        // get column name
                        String columnName = data.keySet().toArray()[i - 1].toString();
                        Integer jdbcType = internalTypes.get(columnName);
                        switch (jdbcType) {
                            case Types.VARCHAR:

                                preparedStatement.setString(i, (String) value);
                                break;
                            case Types.BIGINT:
                                preparedStatement.setObject(i,  value, Types.BIGINT);
                                break;
                            case Types.BIT:
                                preparedStatement.setBoolean(i, (Boolean) value);
                                break;
                            case Types.TIMESTAMP:
                                preparedStatement.setTimestamp(i, (Timestamp) value);
                                break;

                            // Add more cases for other data types as needed.
                            default:
                                preparedStatement.setObject(i, value); // Default to setObject.
                                break;
                        }
                    }

                    preparedStatement.setObject(i, value);
                    i++;
                }
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void updateExistingEntries() throws Exception {

        ArrayList<String> entries = findUpdatableEntries();
        processApplication(entries);
        HashMap<String, HashMap> updatedFields = new HashMap<>();
        for (String entry : entries) {
            HashMap<String, String> externalEntry = externalData.get(entry);
            HashMap<String, String> internalEntry = getInternalEntry(entry);
            HashMap<String, Object> newData = new HashMap<>();
            for (String key : internalEntry.keySet()) {
                // if external data contains key
                if(externalEntry.containsKey(key)){
                    // This is because java doesn't like BigInt + Strings Very much and I have to cast at somepoint for comparisons
                    String val = String.valueOf((Object) internalEntry.get(key));
                    String extEnt = String.valueOf((Object) externalEntry.get(key));
                    if (!extEnt.equals(val)){
                        newData.put(key, externalEntry.get(key));
                    }
                }


            }
            if (newData.size() > 0){
                updatedFields.put(entry, newData);
            }

        }
        for (String key : updatedFields.keySet()){
            StringBuilder updateQuery = new StringBuilder();
            updateQuery.append("UPDATE ")
                    .append(tableName)
                    .append(" SET ");

            HashMap<String, Object> fieldsToUpdate = updatedFields.get(key);

            // Append column = value pairs
            ArrayList<String> setClauses = new ArrayList<>();
            for (String column : fieldsToUpdate.keySet()) {
                setClauses.add(column + " = ?");
            }
            updateQuery.append(String.join(", ", setClauses))
                    .append(" WHERE ")
                    .append(primaryKey)
                    .append( " = ?");
            // At this point, updateQuery contains the SQL statement.
            // You would then prepare the statement and bind the values using a PreparedStatement.

            try (PreparedStatement preparedStatement = conn.prepareStatement(updateQuery.toString())) {
                HashMap<String, Integer> internalTypes = getInternalTypes();
                int i = 1;
                for (Object value : fieldsToUpdate.values()) {
                    // check if workOrder has internalTypes, else just set the object
                    if (internalTypes != null) {
                        // get column name
                        String columnName = fieldsToUpdate.keySet().toArray()[i - 1].toString();
                        Integer jdbcType = internalTypes.get(columnName);
                        switch (jdbcType) {
                            case Types.VARCHAR:

                                preparedStatement.setString(i, (String) value);
                                break;
                            case Types.BIGINT:
                                preparedStatement.setObject(i, value, Types.BIGINT);
                                break;
                            case Types.BIT:
                                preparedStatement.setBoolean(i, (Boolean) value);
                                break;
                            case Types.TIMESTAMP:
                                preparedStatement.setTimestamp(i, (Timestamp) value);
                                break;

                            // Add more cases for other data types as needed.
                            default:
                                preparedStatement.setObject(i, value); // Default to setObject.
                                break;
                        }
                    } else {

                        preparedStatement.setObject(i, value);
                    }
                    i++;
                }
                preparedStatement.setString(i, key);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                throw new SQLException("Could not execute the add query: " + e.getMessage());
            }
        }

    }
}
