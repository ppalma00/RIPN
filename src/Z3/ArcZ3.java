package Z3;

public class ArcZ3 {
    public String from;
    public String to;

    public ArcZ3(String from, String to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public String toString() {
        return from + " -> " + to;
    }
}

