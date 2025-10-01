package Z3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryTranslator {

	public static String translateQuery(String input, int step) {
	    StringBuilder sb = new StringBuilder();

	    String[] lines = input.split("\\r?\\n");
	    for (String line : lines) {
	        line = line.trim();
	        if (line.isEmpty() || line.startsWith(";")) continue;

	        String translated = translateFormula(line, step);
	        sb.append("(assert ").append(translated).append(")\n");
	    }

	    return sb.toString();
	}


	private static String translateFormula(String formula, int step) {
	    formula = formula.trim();

	    if (formula.startsWith("always (") && formula.endsWith(")")) {
	        String inner = formula.substring(8, formula.length() - 1).trim();
	        return translateTemporal(inner, step, "always");
	    }

	    if (formula.startsWith("sometimes (") && formula.endsWith(")")) {
	        String inner = formula.substring(11, formula.length() - 1).trim();
	        return translateTemporal(inner, step, "sometimes");
	    }

	    if (formula.startsWith("always (") && formula.contains(") until (")) {
	        String[] parts = formula.substring(8, formula.length() - 1).split("\\) until \\(");
	        String f1 = parts[0].trim();
	        String f2 = parts[1].trim();
	        return translateTemporalBinary(f1, f2, step, "until");
	    }

	    // TODO: same for before, after, since...

	    throw new RuntimeException("Unsupported temporal operator: " + formula);
	}

	private static String translateTemporal(String expr, int step, String mode) {
	    int maxStep = 10;
	    StringBuilder sb = new StringBuilder();

	    if (mode.equals("always")) {
	        sb.append("(and");
	        for (int t = 0; t < maxStep; t++) {
	            sb.append(" ").append(translateLogical(expr, t));
	        }
	        sb.append(")");
	        return sb.toString();
	    }

	    if (mode.equals("sometimes")) {
	        sb.append("(or");
	        for (int t = 0; t < maxStep; t++) {
	            sb.append(" ").append(translateLogical(expr, t));
	        }
	        sb.append(")");
	        return sb.toString();
	    }

	    throw new RuntimeException("Unsupported unary temporal mode: " + mode);
	}

	private static String translateTemporalBinary(String f1, String f2, int step, String mode) {
	    int maxStep = 10;
	    if (mode.equals("until")) {
	        // (or (and F1_0 F1_1 ... F1_i F2_i)) for some i in 0..maxStep
	        StringBuilder sb = new StringBuilder();
	        sb.append("(or");
	        for (int i = 0; i < maxStep; i++) {
	            sb.append(" (and");
	            for (int j = 0; j < i; j++) {
	                sb.append(" ").append(translateLogical(f1, j));
	            }
	            sb.append(" ").append(translateLogical(f2, i));
	            sb.append(")");
	        }
	        sb.append(")");
	        return sb.toString();
	    }

	    // TODO: same for before, since, after...

	    throw new RuntimeException("Unsupported binary temporal mode: " + mode);
	}

	private static String translateLogical(String expr, int step) {
	    expr = expr.replaceAll("&&", "and");
	    expr = expr.replaceAll("\\|\\|", "or");
	    expr = expr.replaceAll("!", "not ");

	    // Match comparison operators and convert to SMT-LIB prefix form
	    // Supported: ==, !=, >=, <=, >, <
	    expr = expr.replaceAll("([a-zA-Z_][a-zA-Z0-9_\\(\\) ]*)\\s*==\\s*([\\w\\.\\(\\)]+)", "(= $1 $2)");
	    expr = expr.replaceAll("([a-zA-Z_][a-zA-Z0-9_\\(\\) ]*)\\s*!=\\s*([\\w\\.\\(\\)]+)", "(not (= $1 $2))");
	    expr = expr.replaceAll("([a-zA-Z_][a-zA-Z0-9_\\(\\) ]*)\\s*>=\\s*([\\w\\.\\(\\)]+)", "(>= $1 $2)");
	    expr = expr.replaceAll("([a-zA-Z_][a-zA-Z0-9_\\(\\) ]*)\\s*<=\\s*([\\w\\.\\(\\)]+)", "(<= $1 $2)");
	    expr = expr.replaceAll("([a-zA-Z_][a-zA-Z0-9_\\(\\) ]*)\\s*>\\s*([\\w\\.\\(\\)]+)", "(> $1 $2)");
	    expr = expr.replaceAll("([a-zA-Z_][a-zA-Z0-9_\\(\\) ]*)\\s*<\\s*([\\w\\.\\(\\)]+)", "(< $1 $2)");

	    // Replace each variable or fact with its indexed form: (x step)
	    Pattern varPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
	    Matcher m = varPattern.matcher(expr);
	    StringBuffer sb = new StringBuffer();

	    while (m.find()) {
	        String var = m.group(1);
	        String replacement;

	        if (var.startsWith("mark_") || var.startsWith("fired_") || var.length() == 1) {
	            replacement = "(" + var + " " + step + ")";
	        } else if (isBooleanLiteral(var) || isNumeric(var)) {
	            replacement = var;
	        } else {
	            replacement = "(" + var + " " + step + ")";
	        }

	        m.appendReplacement(sb, replacement);
	    }
	    m.appendTail(sb);

	    return sb.toString();
	}


	private static boolean isBooleanLiteral(String s) {
	    return s.equals("true") || s.equals("false");
	}

    // helper
    private static boolean isNumeric(String s) {
        return s.matches("-?\\d+(\\.\\d+)?");
    }


}
