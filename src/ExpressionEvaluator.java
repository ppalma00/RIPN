import org.mvel2.MVEL;

import core.BeliefStore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ExpressionEvaluator {
    public boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore) {
        try {
            // ✅ Asegurar compatibilidad con MVEL
            condition = condition.replaceAll("\\bTrue\\b", "true").replaceAll("\\bFalse\\b", "false");

            // ✅ Reemplazar `t1.end` por `t1_end`
            condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");

            // Crear el contexto con variables y hechos activos
            Map<String, Object> context = new HashMap<>();
            context.putAll(beliefStore.getAllIntVars());
            context.putAll(beliefStore.getAllRealVars());

            // Agregar hechos ACTIVOS sin parámetros como `true`
            for (String fact : beliefStore.getActiveFactsNoParams()) {
                context.put(fact, true);
            }

            // Agregar hechos ACTIVOS con parámetros
            for (Map.Entry<String, List<List<Integer>>> entry : beliefStore.getActiveFacts().entrySet()) {
                String factBase = entry.getKey();
                for (List<Integer> params : entry.getValue()) {
                    String factWithParams = factBase + "(" + params.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")) + ")";
                    context.put(factWithParams, true);
                }
            }

            // ✅ Reemplazar hechos en la condición por `true` o `false` ANTES de evaluar
            Pattern factPattern = Pattern.compile("\\b(\\w+)\\(([^)]*)\\)"); // Captura `uno(4,2)`
            Matcher matcher = factPattern.matcher(condition);
            StringBuffer processedCondition = new StringBuffer();

            while (matcher.find()) {
                String factBase = matcher.group(1);
                String params = matcher.group(2).trim();

                // ✅ Si contiene `_`, buscar cualquier coincidencia en `BeliefStore`
                if (params.contains("_")) {
                    boolean matchFound = beliefStore.getActiveFacts().entrySet().stream()
                            .anyMatch(entry -> entry.getKey().equals(factBase) && entry.getValue().stream()
                                    .anyMatch(list -> list.size() == params.split(",").length));

                    matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
                } else {
                    String fullFact = factBase + "(" + params + ")";
                    boolean isActive = context.containsKey(fullFact) && context.get(fullFact) != null && (Boolean) context.get(fullFact);
                    matcher.appendReplacement(processedCondition, String.valueOf(isActive));
                }
            }
            matcher.appendTail(processedCondition);
            condition = processedCondition.toString();

            // ✅ Reemplazar hechos SIN parámetros (`dos`) por `true` o `false`
            for (String fact : beliefStore.getDeclaredFacts()) {
                if (!fact.contains("(")) { // Solo hechos sin parámetros
                    boolean isActive = context.containsKey(fact) && context.get(fact) != null && (Boolean) context.get(fact);
                    condition = condition.replaceAll("\\b" + fact + "\\b", String.valueOf(isActive));
                }
            }

            // Incluir los temporizadores terminados en el contexto
            for (String timer : beliefStore.getDeclaredTimers()) {
                String timerEndFact = timer + "_end";
                boolean isActive = beliefStore.isFactActive(timerEndFact);
                context.put(timerEndFact, isActive); // True si expiró, False si aún no
            }

            // 🔍 Depuración: Imprimir el contexto antes de evaluar
            //System.out.println("🔍 Context before evaluation: " + context);
            //System.out.println("🔍 Processed Condition: " + condition);

            // Evaluar la expresión con MVEL
            Object result = MVEL.eval(condition, context);

            // Retornar el resultado booleano
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            System.err.println("❌ Error evaluating logical expression: " + condition);
            e.printStackTrace();
            return false;
        }
    }
}

