package org.finos.waltz_util.loader;

import java.io.IOException;

public class Main {


    public static void main(String[] args) throws IOException {
        System.out.println("Loaded");


        for (int i = 0; i < args.length; i++) {

            switch (args[i]) {

                case "-A":
                    System.out.println("Loading applications");
                    ExcelToJSON appConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String appJson = appConverter.convert();
                    ApplicationLoader al = new ApplicationLoader(appJson);
                    al.synch();
                    i = i + 3;
                    break;
                case "-P":
                    System.out.println("Loading people");
                    ExcelToJSON peopleConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String peopleJson = peopleConverter.convert();

                    PersonLoader pl = new PersonLoader(peopleJson);
                    pl.synch();
                    i = i + 3;
                    break;
                case "-D":
                    System.out.println("Loading data types");
                    ExcelToJSON dtConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String dtJson = dtConverter.convert();
                    DataTypeLoader dtl = new DataTypeLoader(dtJson);
                    dtl.synch();
                    i = i + 3;
                    break;
                case "-O":
                    System.out.println("Loading org units");
                    ExcelToJSON ouConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String ouJson = ouConverter.convert();

                    OrgUnitLoader ol = new OrgUnitLoader(ouJson);
                    ol.synch();
                    i = i + 3;
                    break;
                default:
                    System.out.println(args[i]);
                    break;

            }
        }


    }

}
