# Waltz Data Loader README

## Overview
The Waltz Data Loader is a Java application designed to load various types of data (applications, people, data types, organizational units, and measurables) into the Waltz system. This tool is useful for batch processing and automating the integration of large datasets.

## Requirements
- Java Runtime Environment (JRE)
- Waltz Data Loader JAR file
- Data files in the JSON format

## Usage
Run the Waltz Data Loader using the Java command-line interface. The loader accepts several command-line arguments to specify the type of data to load and the corresponding data file paths.

### Command Syntax
java -jar waltz-util-loader.jar [options]


### Options
- `-A <path>`: Load applications data from the specified file.
- `-P <path>`: Load people data from the specified file.
- `-D <path>`: Load data types from the specified file.
- `-O <path>`: Load organizational units data from the specified file.
- `-M <category> <path>`: Load measurables data. The category can be PRODUCT, CAPABILITY, or BOUNDED_CONTEXT.

### (Optional) Specifiy Properties file path
If running the data loader in a setup that does not have a concept of user.home, you can alternatively specify the full location of a properties file.

Set: external.config.path

```
java -Dexternal.config.path=/different/path/to/waltz.properties -jar waltz-util-loader.jar -A /path/to/applications.json
```

### Examples
1. Load applications:
```
java -jar waltz-util-loader.jar -A /path/to/applications.json
```
2. Load people:
```
java -jar waltz-util-loader.jar -P /path/to/people.json
```
3. Load data types:
```
java -jar waltz-util-loader.jar -D /path/to/data_types.json
```
4. Load organizational units:
```
java -jar waltz-util-loader.jar -O /path/to/org_units.json
```
5. Load measurables (e.g., products):
```
java -jar waltz-util-loader.jar -M PRODUCT /path/to/measurables.json
```