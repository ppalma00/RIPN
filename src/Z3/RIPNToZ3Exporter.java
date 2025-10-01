// Z3/RIPNToZ3Exporter.java
package Z3;

import java.io.*;
import java.util.*;

public class RIPNToZ3Exporter {
    private final NetworkZ3 net;
    private int numberOfSteps = 5;

    public RIPNToZ3Exporter(NetworkZ3 net) {
        this.net = net;
    }

    public void setNumberOfSteps(int n) {
        this.numberOfSteps = n;
    }

    public void generateZ3File(String filePath) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(filePath))) {
            w.println("; RIPN translated to SMT-LIB2 for Z3");
            w.println("(set-logic ALL)");
            w.println();

            // declare int vars
            for (String v : net.varsInt) {
                
                    w.printf("(declare-fun %s (Int) Int)\n", v);
                
            }
            w.println();

            // declare marks and fired flags
            for (PlaceZ3 p : net.places) {
    
                    w.printf("(declare-fun mark_%s (Int) Bool)\n", p.name);
                
            }
            for (TransitionZ3 t : net.transitions) {
              
                    w.printf("(declare-fun fired_%s (Int) Bool)\n", t.name);
                
            }
            w.println();

            // init assignments
            for (String assign : net.initAssignments) {
                String[] parts = assign.split(":=");
                String var = parts[0].trim();
                String val = parts[1].trim();
                w.printf("(assert (= (%s 0) %s))\n", var, toSMT(val, 0));
            }
            w.println();

            // init markings
            for (PlaceZ3 p : net.places) {
                w.printf("(assert (= (mark_%s 0) %s))\n", p.name, p.initiallyMarked ? "true" : "false");
            }
            w.println();

            // step semantics
            for (int step = 0; step < numberOfSteps; step++) {
                // only one transition fires per step
            	List<String> disjNoWhen = new ArrayList<>();
            	List<String> disjWithWhen = new ArrayList<>();
            	List<String> firedNames = new ArrayList<>();

            	for (TransitionZ3 t : net.transitions) {
            	    List<String> conjuncts = new ArrayList<>();
            	    String fired = "(fired_" + t.name + " " + step + ")";
            	    firedNames.add(fired);

            	    conjuncts.add(fired);
            	    conjuncts.add(buildGuard(t, step));

            	    for (String assign : t.assignments) {
            	        String[] parts = assign.split(":=");
            	        String var = parts[0].trim();
            	        String val = parts[1].trim();
            	        conjuncts.add(String.format("(= (%s %d) %s)", var, step + 1, toSMT(val, step)));
            	    }

            	    for (String fact : t.rememberFacts) {
            	        conjuncts.add(String.format("(= (%s %d) true)", fact, step + 1));
            	    }
            	    for (String fact : t.forgetFacts) {
            	        conjuncts.add(String.format("(= (%s %d) false)", fact, step + 1));
            	    }

            	    String full = "(and " + String.join(" ", conjuncts) + ")";

            	    if (t.condition == null || t.condition.trim().isEmpty()) {
            	        disjNoWhen.add(full);
            	    } else {
            	        disjWithWhen.add(full);
            	    }
            	}

            	// Prioridad: transiciones sin 'when', luego con 'when'
            	if (!disjNoWhen.isEmpty()) {
            	    w.printf("(assert (or %s))\n", String.join(" ", disjNoWhen));
            	} else if (!disjWithWhen.isEmpty()) {
            	    w.printf("(assert (or %s))\n", String.join(" ", disjWithWhen));
            	}

            	// En cualquier caso, forzamos que una transición dispare
            	w.printf("(assert (or %s))\n\n", String.join(" ", firedNames));

                for (TransitionZ3 t : net.transitions) {
                    firedNames.add(String.format("(fired_%s %d)", t.name, step));
                }
                for (int i = 0; i < firedNames.size(); i++) {
                    for (int j = i + 1; j < firedNames.size(); j++) {
                        w.printf("(assert (not (and %s %s)))\n", firedNames.get(i), firedNames.get(j));
                    }
                }

                // marking evolution
                for (PlaceZ3 p : net.places) {
                    List<String> sources = new ArrayList<>();
                    for (ArcZ3 a : net.arcs) {
                        if (a.to.equals(p.name)) {
                            sources.add("(fired_" + a.from + " " + step + ")");
                        }
                    }
                    List<String> sinks = new ArrayList<>();
                    for (ArcZ3 a : net.arcs) {
                        if (a.from.equals(p.name)) {
                            sinks.add("(fired_" + a.to + " " + step + ")");
                        }
                    }

                    String markExpr = "(mark_" + p.name + " " + step + ")";
                    String addExpr = sources.isEmpty() ? "false" : "(or " + String.join(" ", sources) + ")";
                    String remExpr = sinks.isEmpty() ? "false" : "(or " + String.join(" ", sinks) + ")";

                    String update = "(or " + markExpr + " " + addExpr + ")";
                    if (!sinks.isEmpty()) {
                        update = "(and " + update + " (not " + remExpr + "))";
                    }
                    w.printf("(assert (= (mark_%s %d) %s))\n", p.name, step + 1, update);
                }
                w.println();

                // place assignments (guarded by mark and condition at same step)
                for (PlaceZ3 p : net.places) {
                    for (String assign : p.assignments) {
                        String[] parts = assign.split(":=");
                        String var = parts[0].trim();
                        String val = parts[1].trim();
                        String guard = "(mark_" + p.name + " " + step + ")";
                        if (p.condition != null && !p.condition.trim().isEmpty()) {
                            guard = "(and " + guard + " " + toSMT(p.condition, step) + ")";
                        }

                        String effect = String.format("(= (%s %d) %s)", var, step, toSMT(val, step));
                        w.printf("(assert (=> %s %s))\n\n", guard, effect);
                    }
                }
            }

            w.println("(check-sat)");
            w.println("(get-model)");
        }
    }

    private String buildGuard(TransitionZ3 t, int step) {
        List<String> guards = new ArrayList<>();
        for (String ip : t.inputPlaces) {
            guards.add(String.format("(mark_%s %d)", ip, step));
        }
        if (t.condition != null && !t.condition.isEmpty()) {
            guards.add(toSMT(t.condition, step));
        }
        if (guards.isEmpty()) return "true";
        if (guards.size() == 1) return guards.get(0);
        return "(and " + String.join(" ", guards) + ")";
    }

    // === infix -> postfix -> SMT translation ===
    private String toSMT(String expr, int step) {
        if (expr == null || expr.trim().isEmpty()) return "true";
        List<String> postfix = infixToPostfix(expr);
        return postfixToSMT(postfix, step);
    }

    private List<String> infixToPostfix(String expr) {
        expr = expr.replaceAll("([()])", " $1 ");
        expr = expr.replaceAll("(\\|\\||&&|==|!=|<=|>=|<|>|\\+|-|\\*|/|!)", " $1 ");
        Map<String, Integer> prec = new HashMap<>();
        prec.put("(", 0); prec.put(")", 0);
        prec.put("||", 1); prec.put("&&", 2);
        prec.put("==", 3); prec.put("!=", 3);
        prec.put("<", 4); prec.put("<=", 4);
        prec.put(">", 4); prec.put(">=", 4);
        prec.put("+", 5); prec.put("-", 5);
        prec.put("*", 6); prec.put("/", 6);
        prec.put("!", 7);

        Stack<String> st = new Stack<>();
        List<String> out = new ArrayList<>();
        for (String tok : expr.trim().split("\\s+")) {
            if (tok.matches("[a-zA-Z_][a-zA-Z0-9_]*|\\d+(?:\\.\\d+)?")) {
                out.add(tok);
            } else if (tok.equals("(")) {
                st.push(tok);
            } else if (tok.equals(")")) {
                while (!st.isEmpty() && !st.peek().equals("(")) out.add(st.pop());
                if (!st.isEmpty() && st.peek().equals("(")) st.pop();
            } else {
                while (!st.isEmpty() && prec.getOrDefault(tok,-1) <= prec.getOrDefault(st.peek(),-1)) {
                    out.add(st.pop());
                }
                st.push(tok);
            }
        }
        while (!st.isEmpty()) out.add(st.pop());
        return out;
    }

    private String postfixToSMT(List<String> pf, int step) {
        Stack<String> st = new Stack<>();
        Set<String> bin = new HashSet<>(Arrays.asList("&&","||","==","!=","<","<=" ,">",">=","+","-","*","/"));
        Set<String> un  = new HashSet<>(Collections.singletonList("!"));
        for (String t : pf) {
            if (bin.contains(t)) {
                String b = st.pop(), a = st.pop();
                String op;
                if (t.equals("&&")) {
                    op = "and";
                } else if (t.equals("||")) {
                    op = "or";
                } else if (t.equals("==")) {
                    op = "=";
                } else if (t.equals("!=")) {
                    op = "distinct";
                } else {
                    op = t;
                }
                st.push("(" + op + " " + a + " " + b + ")");
            } else if (un.contains(t)) {
                String a = st.pop();
                st.push("(not " + a + ")");
            } else {
                if (t.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    st.push("(" + t + " " + step + ")");
                } else {
                    st.push(t);
                }
            }
        }
        return st.pop();
    }
}
