package Z3;

import java.util.*;

public class PlaceZ3 {
    public String name;
    public boolean initiallyMarked;
    public List<String> rememberFacts = new ArrayList<>();
    public List<String> forgetFacts = new ArrayList<>();
    public List<String> actions = new ArrayList<>();
    public List<String> assignments = new ArrayList<>();
    public String condition="";

    public PlaceZ3(String name, boolean marked) {
        this.name = name;
        this.initiallyMarked = marked;
    }
}
