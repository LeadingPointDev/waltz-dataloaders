package dataloader;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class TableUpdater {
    Connection conn;
    String tableName;
    String primaryKey;
    ArrayList<HashMap> internalData;
    HashMap<String, HashMap> externalData;
    String ExternalJSONFile;
    ArrayList<String> requiredFields = new ArrayList<>();
    ArrayList<String> optionalFields = new ArrayList<>();
    public TableUpdater(String externalJSONFile, String tableName, String primaryKey) throws Exception {
        this.conn = getDBConnection();
        this.tableName = tableName;
        this.primaryKey = primaryKey;
        // for data, search by email (HashMap)
        // for params in data, search by column name (HashMap)
        this.internalData = getInternalData(tableName);
        this.ExternalJSONFile = externalJSONFile;

    }

    protected Connection getDBConnection() throws ClassNotFoundException, SQLException {
        String url = "jdbc:postgresql://localhost:5432/waltz_clone";
        String user = "postgres";
        String password = "1123";
        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(url, user, password);
        return conn;
    }

    protected ArrayList<HashMap> getInternalData(String tableName) throws Exception {
        Connection conn = getDBConnection();
        ArrayList<HashMap> data = new ArrayList<>();
        String sql = "SELECT * FROM " + tableName;
        ResultSet rs = conn.createStatement().executeQuery(sql);
        ResultSetMetaData rsmd = rs.getMetaData();

        Integer columnCount = rsmd.getColumnCount();
        List<String> columnNames = new ArrayList<>();
        int bound = rsmd.getColumnCount() + 1;
        for (int i = 1; i < bound; i++) {
            String columnName = rsmd.getColumnName(i);
            columnNames.add(columnName);
        }


        while (rs.next()){
            HashMap<String, String> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnValue = rs.getString(i);
                row.put(columnNames.get(i - 1), columnValue);
            };
            data.add(row);


        }



        conn.close();
        return data;
    }

    protected ArrayList<String> findNewEntries(){
        // this finds entries that are in externalData but not in internalData
        ArrayList<String> newEntries = new ArrayList<>();
        HashSet<String> emailSet = new HashSet<>();
        for (HashMap<String, String> map : internalData) {
            emailSet.add(map.get(primaryKey));
        }
        for (String key : externalData.keySet()) {
            if (!emailSet.contains(key)){
                newEntries.add(key);
            }
        }

        return newEntries;
    }

    protected ArrayList<String> findUpdatableEntries(){
        // This finds entries that are in both internal and external data
        ArrayList<String> existingEntries = new ArrayList<>();
        HashSet<String> emailSet = new HashSet<>();
        for (HashMap<String, String> map : internalData) {
            emailSet.add(map.get(primaryKey));
        }
        for (String key : externalData.keySet()) {
            if (emailSet.contains(key)){
                existingEntries.add(key);
            }
        }

        return existingEntries;
    }

    protected ArrayList<String> findDeletedEntries(){
        // finds entries that are in internalData but not in externalData

        HashSet<String> internalSet = new HashSet<>();
        for (HashMap<String, String> map : internalData) {
            internalSet.add(map.get(primaryKey));
        }
        HashSet<String> externalSet = new HashSet<>();
        for (String key : externalData.keySet()){
            externalSet.add(key);
        }
        internalSet.removeAll(externalSet);
        ArrayList<String> deletedEntries = new ArrayList<>(internalSet);

        return deletedEntries;
    }
    protected HashMap<String, String> getInternalEntry(String primaryKeyValue){
        for (HashMap<String, String> entry : internalData) {
            if (entry.get(primaryKey).equals(primaryKeyValue)){
                return entry;
            }
        }
        return null;
    }


    public void updateExistingEntries() throws Exception {
        ArrayList<String> entries = findUpdatableEntries();
        // will likely need to process data here, so this function is often overridden.
        HashMap<String, HashMap> updatedFields = new HashMap<>();
        for (String entry : entries) {
            HashMap<String, String> externalEntry = externalData.get(entry);
            HashMap<String, String> internalEntry = getInternalEntry(entry);
            HashMap<String, String> newData = new HashMap<>();
            for (String key : internalEntry.keySet()) {
                // if external data contains key
                if(externalEntry.containsKey(key)){
                    // if they dont match
                    if (!externalEntry.get(key).equals(internalEntry.get(key))){
                        newData.put(key, externalEntry.get(key));
                    }
                }


            }
            if (newData.size() > 0){
                updatedFields.put(entry, newData);
            }
        }
    }





    private void updateEntry(String PK, HashMap newData){

    }


    protected HashMap<String, Integer> getInternalTypes() throws SQLException, ClassNotFoundException {
        conn = getDBConnection();
        // get database metadata
        DatabaseMetaData metadata = conn.getMetaData();
        ResultSetMetaData rsmd = conn.createStatement().executeQuery("SELECT * FROM " + this.tableName + " LIMIT 0").getMetaData();
        int columnsNumber = rsmd.getColumnCount();
        HashMap<String, Integer> columnTypes = new HashMap<>();
        for (int i = 1; i <= columnsNumber; i++) {
            columnTypes.put(rsmd.getColumnName(i), rsmd.getColumnType(i));
        }
        return columnTypes;
    }




    protected String getDependentField(HashMap<String, String> dependencies, String value) throws SQLException, ClassNotFoundException {
        Connection conn = getDBConnection();
        StringBuilder getQuery = new StringBuilder("SELECT ");
        ResultSet rs;
        getQuery.append(dependencies.get("internal_identifier"))
                .append(" FROM ")
                .append(dependencies.get("table"))
                .append(" WHERE ")
                .append(dependencies.get("internal_external_identifier"))
                .append(" = ?");
        try (PreparedStatement preparedStatement = conn.prepareStatement(getQuery.toString())) {
            preparedStatement.setObject(1, value );
            rs = preparedStatement.executeQuery();
            rs.next();
            return rs.getString(1);








        } catch (SQLException e) {
            throw new SQLException("Could not execute the add query: " + e.getMessage());
        }




    }
    public void insertNewEntries() throws Exception{
        // Method to insert new entries into the database
        // differes from table to table
    }
    protected void disableDeletedEntries(){
        // set "is_removed" to true for all deleted entries
        ArrayList<String> deletedEntries = findDeletedEntries();
        for (String entry : deletedEntries){
            HashMap<String, String> data = externalData.get(entry);
            StringBuilder setIsRemovedQuery = new StringBuilder();

            setIsRemovedQuery
                    .append("UPDATE ")
                    .append(tableName)
                    .append(" SET is_removed = ? WHERE ")
                    .append(primaryKey)
                    .append(" = ?");
            try(PreparedStatement preparedStatement = conn.prepareStatement(setIsRemovedQuery.toString())){
                preparedStatement.setBoolean(1, true);
                preparedStatement.setString(2, entry);
                preparedStatement.executeUpdate();

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

    }



    public void updateAll() throws Exception {
        disableDeletedEntries();
        insertNewEntries();
        updateExistingEntries();
        System.out.println(this.tableName + " has been updated.");
    }
}
