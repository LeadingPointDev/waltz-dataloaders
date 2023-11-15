package org.finos.waltz_util.loader;

public class Main {


    public static void main(String[] args) throws Exception {
        System.out.println("Loaded");


        for (int i = 0; i < args.length; i++) {

            switch (args[i]) {

                case "-A":
                    System.out.println("Loading applications");
                    ApplicationLoader al = new ApplicationLoader(args[i + 1]);
                    al.synch();
                    i = i + 1;
                    break;
                case "-P":
                    System.out.println("Loading people");
                    PersonLoader pl = new PersonLoader(args[i + 1]);
                    pl.synch();
                    i = i + 1;
                    break;
                case "-D":
                    System.out.println("Loading data types");
                    DataTypeLoader dtl = new DataTypeLoader(args[i + 1]);
                    dtl.synch();
                    i = i + 2;
                    break;
                case "-O":
                    System.out.println("Loading org units");

                    OrgUnitLoader ol = new OrgUnitLoader(args[i + 1]);
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
                    MeasurablesLoader ml = new MeasurablesLoader(args[i+1], selector);
                    ml.synch();
                    i = i + 1;
                    break;


                default:
                    System.out.println(args[i]);
                    break;

            }
        }


    }

}
