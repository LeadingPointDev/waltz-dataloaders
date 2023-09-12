package org.finos.waltz_util.loader;

public class Main {


    public static void main(String[] args) throws Exception {
        System.out.println("Loaded");


        for (int i = 0; i < args.length; i++) {

            switch (args[i]) {

                case "-A":
                    System.out.println("Loading applications");
                    ExcelToJSON appConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String appJson = appConverter.convert();
                    ApplicationLoader al = new ApplicationLoader(appJson);
                    al.synch();
                    i = i + 2;
                    break;
                case "-P":
                    System.out.println("Loading people");
                    ExcelToJSON peopleConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String peopleJson = peopleConverter.convert();

                    PersonLoader pl = new PersonLoader(peopleJson);
                    pl.synch();
                    i = i + 2;
                    break;
                case "-D":
                    System.out.println("Loading data types");
                    ExcelToJSON dtConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String dtJson = dtConverter.convert();
                    DataTypeLoader dtl = new DataTypeLoader(dtJson);
                    dtl.synch();
                    i = i + 2;
                    break;
                case "-O":
                    System.out.println("Loading org units");
                    ExcelToJSON ouConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String ouJson = ouConverter.convert();
                    OrgUnitLoader ol = new OrgUnitLoader(ouJson);
                    ol.synch();
                    i = i + 2;
                    break;
                case "-M":
                    // for measurables need to specify what category
                    // args will look like
                    // java -jar loader.jar -M PRODUCT <path to Excel file> <path to config file>
                    System.out.println("Loading measurables");
                    i++;
                    switch (args[i]) {
                        case "PRODUCT":
                            System.out.println("Product Selected");

                            break;
                        case "CAPABILITY":
                            System.out.println("Capability Selected");

                            break;
                        case "BOUNDED_CONTEXT":
                            System.out.println("Bounded Context Selected");

                            break;
                    default:
                        throw new IllegalArgumentException("Measurable category not recognised");
                    }
                    String selector = args[i];
                    ExcelToJSON mConverter = new ExcelToJSON(args[i + 1], args[i + 2]);
                    String mJson = mConverter.convert();
                    MeasurablesLoader ml = new MeasurablesLoader(mJson, selector);
                    ml.synch();
                    i = i + 2;
                    break;


                default:
                    System.out.println(args[i]);
                    break;

            }
        }


    }

}
