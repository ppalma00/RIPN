package Z3;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class QueryToRunZ3Generator {

    private int maxSteps;

    public QueryToRunZ3Generator() {
        // El valor real de maxSteps se detectará desde el archivo SMT
    }

    public void generateRunZ3Class(String queriesFile, String outputJavaFile) throws Exception {
        // Detectar número máximo de pasos del archivo ripn_output.smt2
        this.maxSteps = detectMaxStepFromSMTFile("ripn_output.smt2");

        List<String> assertsSMT = new ArrayList<>();

        // Leer queries
        List<String> lines = readLines(queriesFile);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String smtAssert = convertTemporalQueryToSMT(line);
            assertsSMT.add(smtAssert);
        }

        // Escribir RunZ3Example.java
        try (PrintWriter pw = new PrintWriter(outputJavaFile)) {
            writeRunZ3ExampleClass(pw, assertsSMT);
        }
        System.out.println("✅ RunZ3Example.java generated at: " + outputJavaFile);
    }


    private List<String> readLines(String file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private String convertTemporalQueryToSMT(String query) {
        query = query.trim();

        // 1) always (F) until (F2)
        Pattern pUntil = Pattern.compile("^always\\s*\\((.*?)\\)\\s*until\\s*\\((.*?)\\)$");
        Matcher mUntil = pUntil.matcher(query);
        if (mUntil.find()) {
            String f  = mUntil.group(1).trim();
            String f2 = mUntil.group(2).trim();
            return buildAlwaysUntil(f, f2);
        }

        // 2) sometimes (F) after (F2)
        Pattern pAfter = Pattern.compile("^sometimes\\s*\\((.*?)\\)\\s*after\\s*\\((.*?)\\)$");
        Matcher mAfter = pAfter.matcher(query);
        if (mAfter.find()) {
            String f  = mAfter.group(1).trim();
            String f2 = mAfter.group(2).trim();
            return buildSometimesAfter(f, f2);
        }

        // 3) always (F)
        Pattern pAlways = Pattern.compile("^always\\s*\\((.*?)\\)$");
        Matcher mAlways = pAlways.matcher(query);
        if (mAlways.find()) {
            String f = mAlways.group(1).trim();
            return buildAlways(f);
        }

        // 4) sometimes (F)
        Pattern pSometimes = Pattern.compile("^sometimes\\s*\\((.*?)\\)$");
        Matcher mSometimes = pSometimes.matcher(query);
        if (mSometimes.find()) {
            String f = mSometimes.group(1).trim();
            return buildSometimes(f);
        }

        // Fallback
        return "; Could not parse query: " + query;
    }

    private String buildAlways(String f) {
        List<String> clauses = new ArrayList<>();
        for (int i = 0; i <= maxSteps; i++) {
            clauses.add(translateFormula(f, i));
        }
        return "(assert (and " + String.join(" ", clauses) + "))";
    }

    private String buildSometimes(String f) {
        List<String> clauses = new ArrayList<>();
        for (int i = 0; i <= maxSteps; i++) {
            clauses.add(translateFormula(f, i));
        }
        return "(assert (or " + String.join(" ", clauses) + "))";
    }

    private String buildAlwaysUntil(String f, String f2) {
        List<String> bigOrs = new ArrayList<>();

        for (int j = 0; j <= maxSteps; j++) {
            List<String> andClause = new ArrayList<>();

            // F debe cumplirse en todos los pasos desde 0 hasta j (inclusive)
            for (int k = 0; k <= j; k++) {
                andClause.add(translateFormula(f, k));
            }

            // F2 debe cumplirse en el paso j
            andClause.add(translateFormula(f2, j));

            bigOrs.add("(and " + String.join(" ", andClause) + ")");
        }

        return "(assert (or " + String.join(" ", bigOrs) + "))";
    }

    private String buildSometimesAfter(String f, String f2) {
        List<String> bigOrs = new ArrayList<>();

        for (int j = 0; j <= maxSteps; j++) {
            String f2Reached = translateFormula(f2, j);
            List<String> fAfter = new ArrayList<>();
            for (int k = j + 1; k <= maxSteps; k++) {
                fAfter.add(translateFormula(f, k));
            }
            bigOrs.add("(and " + f2Reached + " (or " + String.join(" ", fAfter) + "))");
        }
        return "(assert (or " + String.join(" ", bigOrs) + "))";
    }

    private String translateFormula(String expr, int step) {
        expr = expr.replaceAll("&&", "&&");
        expr = expr.replaceAll("\\|\\|", "||");
        expr = expr.replaceAll("!", "!");

        // NO reemplaces las variables aquí
        List<String> postfix = infixToPostfix(expr);
        return postfixToSMT(postfix, step);
    }
    private String postfixToSMT(List<String> postfix, int step) {
        Stack<String> stack = new Stack<>();

        Set<String> binaryOps = new HashSet<>(Arrays.asList(
            "&&", "||", "==", "!=", "<", "<=", ">", ">=", "+", "-", "*", "/"
        ));
        Set<String> unaryOps = new HashSet<>(Collections.singletonList("!"));

        for (String token : postfix) {
            if (binaryOps.contains(token)) {
                String b = stack.pop();
                String a = stack.pop();
                String op;
                if (token.equals("&&")) {
                    op = "and";
                } else if (token.equals("||")) {
                    op = "or";
                } else if (token.equals("==")) {
                    op = "=";
                } else if (token.equals("!=")) {
                    op = "distinct";
                } else {
                    op = token;
                }
                stack.push("(" + op + " " + a + " " + b + ")");
            } else if (unaryOps.contains(token)) {
                String a = stack.pop();
                stack.push("(not " + a + ")");
            } else {
                // Si es una variable, conviértela a (var step)
                if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    stack.push("(" + token + " " + step + ")");
                } else {
                    stack.push(token);
                }
            }
        }

        return stack.pop();
    }
    
    private List<String> infixToPostfix(String expr) {
        // Añadir espacios alrededor de paréntesis y operadores
        expr = expr.replaceAll("([()])", " $1 ");
        expr = expr.replaceAll("(\\|\\||&&|==|!=|<=|>=|<|>|\\+|-|\\*|/|!)", " $1 ");

        Map<String, Integer> precedence = new HashMap<>();
        precedence.put("(", 0); precedence.put(")", 0);
        precedence.put("||", 1);
        precedence.put("&&", 2);
        precedence.put("==", 3); precedence.put("!=", 3);
        precedence.put("<", 4);  precedence.put("<=", 4);
        precedence.put(">", 4);  precedence.put(">=", 4);
        precedence.put("+", 5);  precedence.put("-", 5);
        precedence.put("*", 6);  precedence.put("/", 6);
        precedence.put("!", 7);

        Stack<String> stack = new Stack<>();
        List<String> output = new ArrayList<>();
        String[] tokens = expr.trim().split("\\s+");

        for (String token : tokens) {
            if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*|\\d+(?:\\.\\d+)?")) {
                output.add(token);
            } else if (token.equals("(")) {
                stack.push(token);
            } else if (token.equals(")")) {
                while (!stack.isEmpty() && !stack.peek().equals("(")) {
                    output.add(stack.pop());
                }
                if (!stack.isEmpty() && stack.peek().equals("(")) stack.pop();
            } else {
                while (!stack.isEmpty() &&
                       precedence.getOrDefault(token, -1) <= precedence.getOrDefault(stack.peek(), -1)) {
                    output.add(stack.pop());
                }
                stack.push(token);
            }
        }
        while (!stack.isEmpty()) output.add(stack.pop());
        return output;
    }


    private int detectMaxStepFromSMTFile(String smtFilePath) throws IOException {
        int max = 0;
        Pattern pattern = Pattern.compile("\\(x\\s+(\\d+)\\)");
        try (BufferedReader br = new BufferedReader(new FileReader(smtFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                while (m.find()) {
                    int step = Integer.parseInt(m.group(1));
                    if (step > max) {
                        max = step;
                    }
                }
            }
        }
        return max;
    }




    private void writeRunZ3ExampleClass(PrintWriter pw, List<String> asserts) {
        pw.println("package Z3;");
        pw.println();
        pw.println("import com.microsoft.z3.*;");
        pw.println("import java.nio.file.*;");
        pw.println();
        pw.println("public class RunZ3Example {");
        pw.println();
        pw.println("    public static void main(String[] args) throws Exception {");
        pw.println("        System.load(\"/usr/local/lib/libz3.dylib\");");
        pw.println("        Context ctx = new Context();");
        pw.println();
        pw.println("        // Leer el archivo ripn_output.smt2");
        pw.println("        String smt2 = new String(Files.readAllBytes(Paths.get(\"ripn_output.smt2\")));");
        pw.println();
        pw.println("        // Concatenar queries al contenido original");
        pw.println("        StringBuilder fullSMT = new StringBuilder(smt2);");
        for (String a : asserts) {
            pw.println("        fullSMT.append(\"\\n\").append(\"" + a.replace("\"", "\\\"") + "\");");
        }
        pw.println();
        pw.println("        // Parsear todo junto");
        pw.println("        BoolExpr[] allConstraints = ctx.parseSMTLIB2String(fullSMT.toString(), null, null, null, null);");
        pw.println("        Solver s = ctx.mkSolver();");
        pw.println("        s.add(allConstraints);");
        pw.println();
        pw.println("        Status result = s.check();");
        pw.println("        System.out.println(result);");
        pw.println("        if (result == Status.SATISFIABLE) {");
        pw.println("            System.out.println(\"Model: \" + s.getModel());");
        pw.println("        } else {");
        pw.println("            System.out.println(\"No solution found.\");");
        pw.println("        }");
        pw.println("        ctx.close();");
        pw.println("    }");
        pw.println("}");
    }
}
