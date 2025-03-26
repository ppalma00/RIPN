import java.util.Arrays; // Import necesario para usar Arrays

public class ParameterizedAction extends Action {
    private final String[] parameters;

    public ParameterizedAction(String name, String[] parameters) {
        super(name);
        this.parameters = parameters;
    }

    @Override
    public void execute() {
        System.out.println("Executing action: " + name + " with parameters: " + Arrays.toString(parameters));
    }
}
