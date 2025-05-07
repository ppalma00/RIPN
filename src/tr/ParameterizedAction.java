package tr;
import java.util.Arrays; 

import both.LoggerManager;

public class ParameterizedAction extends Action {
    private final String[] parameters;

    private final LoggerManager logger;

    public ParameterizedAction(String name, String[] parameters, LoggerManager logger) {
        super(name);
        this.parameters = parameters;
        this.logger = logger;
    }

    @Override
    public void execute() {
        logger.log("Executing action: " + name + " with parameters: " + Arrays.toString(parameters), true, true);
    }

}
