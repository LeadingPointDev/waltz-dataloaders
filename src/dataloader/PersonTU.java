package dataloader;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;

public class PersonTU extends TableUpdater{
    public PersonTU(String ExternalJSONFile) throws Exception {
        super(ExternalJSONFile, "person", "email");
        requiredFields.add("display_name");
        requiredFields.add("email");
        requiredFields.add("kind");
        requiredFields.add("manager_email");
        requiredFields.add("organisational_unit_id");
        requiredFields.add("title");
        requiredFields.add("employee_id");

        optionalFields.add("user_principal_name");
        optionalFields.add("department_name");
        optionalFields.add("office_phone");
        optionalFields.add("mobile_phone");




        this.externalData = new JSONLoader(this.ExternalJSONFile, primaryKey,requiredFields, optionalFields).getData();
    }

    private void processPeople(ArrayList<String> Entries) throws Exception {
        // processing manager ID



        for (String entry : Entries){
            // generate employee id

            String managerEmail = (String) externalData.get(entry).get("manager_email");
            for (HashMap<String, String> person : internalData){
                if (person.get("email").equals(managerEmail)){
                    externalData.get(entry).put("manager_employee_id", person.get("employee_id"));
                    externalData.get(entry).remove("manager_email");
                }
            }
            if (externalData.get(entry).get("manager_employee_id") == null){
                // check if manager is in external data
                if (externalData.get(externalData.get(entry).get("manager_email")) != null){
                    externalData.get(entry).put("manager_employee_id", externalData.get(externalData.get(entry).get("manager_email")).get("id"));
                    externalData.get(entry).remove("manager_email");
                }
            }
        }


        // processing Org Unit
        HashMap<String, String> org_unit_dependencies = new HashMap<>();
        org_unit_dependencies.put("table", "organisational_unit");
        org_unit_dependencies.put("external_identifier", "PARENT ORG");
        org_unit_dependencies.put("internal_external_identifier", "external_id");
        org_unit_dependencies.put("internal_identifier", "id");
        for (String entry: Entries){
            String value = (String) externalData.get(entry).get("organisational_unit_id");
            String new_data = getDependentField(org_unit_dependencies, value);
            if (new_data != null){
                BigInteger longData = new BigInteger(new_data);
                externalData.get(entry).put("organisational_unit_id",   longData);
            } else {
                throw new Exception("Cannot find Org Unit: " + value);
            }
        }

        // setting is_removed to false
        for (String entry : Entries){
            externalData.get(entry).put("is_removed", false);
        }




    }
    public void insertNewEntries() throws Exception {
        ArrayList<String> entries = findNewEntries();
        processPeople(entries);
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
        processPeople(entries);
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
