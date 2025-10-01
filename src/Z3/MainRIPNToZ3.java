package Z3;

public class MainRIPNToZ3 {

    public static void main(String[] args) throws Exception {
        // Parse the RIPN text file into a model object
        RIPNModel model = RIPNParser.parse("RIPN_PN.txt");

        // Convert the RIPN model into the Z3 network representation
        NetworkZ3 net = MapperRIPNToZ3.map(model);

        // Set number of steps (execution time horizon)
        net.numberOfSteps = 5;

        // Export the network to a Z3 SMT-LIB2 file
        RIPNToZ3Exporter exporter = new RIPNToZ3Exporter(net);
        exporter.generateZ3File("ripn_output.smt2");
        
     // ALSO generate the RunZ3Example class
     // Detectar automáticamente el número de pasos a partir del archivo generado
        QueryToRunZ3Generator generator = new QueryToRunZ3Generator();
        generator.generateRunZ3Class("queryZ3.txt", "src/Z3/RunZ3Example.java");

        System.out.println("✅ RunZ3Example.java generated.");
    }
}
