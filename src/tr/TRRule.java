package tr;
import java.util.List;
import java.util.function.Predicate;

import bs.BeliefStore;

public class TRRule {
    private final Predicate<BeliefStore> condition;
    private final String conditionText;
    private final List<String> discreteActions;
    private final List<String> durativeActions;
    private final Runnable beliefStoreUpdates;

    public TRRule(Predicate<BeliefStore> condition, String conditionText,
                  List<String> discreteActions, List<String> durativeActions,
                  Runnable beliefStoreUpdates) {
        this.condition = condition;
        this.conditionText = conditionText;
        this.discreteActions = discreteActions;
        this.durativeActions = durativeActions;
        this.beliefStoreUpdates = beliefStoreUpdates;
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
