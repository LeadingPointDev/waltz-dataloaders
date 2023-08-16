import dataloader.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) throws Exception {
        PersonTU PTU = new PersonTU("data\\person.json");
        ApplicationTU ATU = new ApplicationTU("data\\application.json");
        PTU.updateAll(); // just runs delete, PTU.insertNewEntries and PTU.updateExistingEntries in order
        ATU.updateAll(); // just runs delete, ATU.insertNewEntries and ATU.updateExistingEntries in order
    }


}