package tr;

public abstract class Action {
    protected final String name;

    public Action(String name) {
        this.name = name;
    }

    public abstract void execute();
}
