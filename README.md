# Waltz Data Loader

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
- `-APPLICATION <path>`: Load applications data from the specified file.
- `-PERSON <path>`: Load people data from the specified file.
- `-DATATYPE <path>`: Load data types from the specified file.
- `-ORGANISATIONALUNIT <path>`: Load organizational units data from the specified file.
- `-MEASURABLE <category> <path>`: Load measurables data. The category can be PRODUCT, CAPABILITY, or BOUNDED_CONTEXT.

### (Optional) Specify Properties file path
If running the data loader in a setup that does not have a concept of user.home, you can alternatively specify the full location of a properties file.

Set: external.config.path

```
java -Dexternal.config.path=/different/path/to/waltz.properties -jar waltz-util-loader.jar -A /path/to/applications.json
```

### Examples
1. Load Applications:
```
java -jar waltz-util-loader.jar -APPLICATION /path/to/applications.json
```
2. Load People:
```
java -jar waltz-util-loader.jar -PERSON /path/to/people.json
```
3. Load Data Types:
```
java -jar waltz-util-loader.jar -DATATYPE /path/to/data_types.json
```
4. Load Organizational Units:
```
java -jar waltz-util-loader.jar -ORGANISATIONALUNIT /path/to/org_units.json
```
5. Load Measurables (e.g. Product, Capability, Bounded Context):
```
java -jar waltz-util-loader.jar -MEASURABLE PRODUCT /path/to/measurables.json
```