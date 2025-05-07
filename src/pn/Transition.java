package pn;

import java.util.ArrayList;
import java.util.List;

public class Transition {
    String name;
    private List<String> triggerEvents = new ArrayList<>();

    public void setTriggerEvents(List<String> events) {
        this.triggerEvents = events;
    }

    public List<String> getTriggerEvents() {
        return triggerEvents;
    }

    public Transition(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    private List<String> triggerVariables = new ArrayList<>();

    public List<String> getTriggerVariables() {
        return triggerVariables;
    }
    public void setTriggerVariables(List<String> triggerVariables) {
        this.triggerVariables = triggerVariables;
    }

}
