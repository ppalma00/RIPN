package tr;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import bs.BeliefStore;

public class TRRule {
    private final Predicate<BeliefStore> condition;
    private final String conditionText;
    private final List<String> discreteActions;
    private final List<String> durativeActions;
    private final Runnable beliefStoreUpdates;
    
    private Map<String, Boolean> outVarsMap;

    public TRRule(Predicate<BeliefStore> condition,
            String conditionStr,
            List<String> discreteActions,
            List<String> durativeActions,
            Runnable beliefStoreUpdates,
            Map<String, Boolean> outVarsMap) {
  this.condition = condition;
  this.conditionText = conditionStr;
  this.discreteActions = discreteActions;
  this.durativeActions = durativeActions;
  this.beliefStoreUpdates = beliefStoreUpdates;
  this.outVarsMap = outVarsMap;
}


    public boolean evaluateCondition(BeliefStore beliefStore) {
        return condition.test(beliefStore);
    }

    public String getConditionText() {
        return conditionText;
    }

    public List<String> getDiscreteActions() {
        return discreteActions;
    }

    public List<String> getDurativeActions() {
        return durativeActions;
    }

    public void applyUpdates() {
        if (beliefStoreUpdates != null) {
            beliefStoreUpdates.run();
        }
    }
    public boolean hasUpdates() {
        return beliefStoreUpdates != null;
    }

}
