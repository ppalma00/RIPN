package Z3;
import java.util.*;

public class NetworkZ3 {
    public int numberOfSteps;
    public List<PlaceZ3> places = new ArrayList<>();
    public List<TransitionZ3> transitions = new ArrayList<>();
    public List<ArcZ3> arcs = new ArrayList<>();

    public List<String> varsInt = new ArrayList<>();
    public List<String> initAssignments = new ArrayList<>();

    public PlaceZ3 findPlace(String name) {
        for (PlaceZ3 p : places)
            if (p.name.equals(name)) return p;
        return null;
    }

    public TransitionZ3 findTransition(String name) {
        for (TransitionZ3 t : transitions)
            if (t.name.equals(name)) return t;
        return null;
    }
}