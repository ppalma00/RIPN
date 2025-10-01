package Z3;

import com.microsoft.z3.*;
import java.nio.file.*;

public class RunZ3Example {

    public static void main(String[] args) throws Exception {
        System.load("/usr/local/lib/libz3.dylib");
        Context ctx = new Context();

        // Leer el archivo ripn_output.smt2
        String smt2 = new String(Files.readAllBytes(Paths.get("ripn_output.smt2")));

        // Concatenar queries al contenido original
        StringBuilder fullSMT = new StringBuilder(smt2);
        fullSMT.append("\n").append("(assert (or (= (x 0) 2) (= (x 1) 2) (= (x 2) 2) (= (x 3) 2) (= (x 4) 2) (= (x 5) 2)))");

        // Parsear todo junto
        BoolExpr[] allConstraints = ctx.parseSMTLIB2String(fullSMT.toString(), null, null, null, null);
        Solver s = ctx.mkSolver();
        s.add(allConstraints);

        Status result = s.check();
        System.out.println(result);
        if (result == Status.SATISFIABLE) {
            System.out.println("Model: " + s.getModel());
        } else {
            System.out.println("No solution found.");
        }
        ctx.close();
    }
}
