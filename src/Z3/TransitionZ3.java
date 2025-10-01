package Z3;

import java.util.*;

public class TransitionZ3 {
    public String name;

    // List of places this transition reads tokens from
    public List<String> inputPlaces = new ArrayList<>();

    // List of places this transition puts tokens into
    public List<String> outputPlaces = new ArrayList<>();

    // Optional condition string
    public String condition;

    public List<String> rememberFacts = new ArrayList<>();
    public List<String> forgetFacts = new ArrayList<>();
    public List<String> assignments = new ArrayList<>();

    public TransitionZ3(String name) {
        this.name = name;
    }
}

