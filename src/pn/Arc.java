package pn;

public class Arc {
    private Place place;
    private Transition transition;
    private boolean isInput;
    private boolean isInhibitor; 

    public Arc(Place place, Transition transition, boolean isInput, boolean isInhibitor) {
        this.place = place;
        this.transition = transition;
        this.isInput = isInput;
        this.isInhibitor = isInhibitor;
    }

    public Place getPlace() {
        return place;
    }

    public Transition getTransition() {
        return transition;
    }

    public boolean isInput() {
        return isInput;
    }

    public boolean isInhibitor() {
        return isInhibitor;
    }
}
