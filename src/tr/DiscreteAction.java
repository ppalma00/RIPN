package tr;
import both.LoggerManager;
public class DiscreteAction extends Action {
	private final LoggerManager logger;
	public DiscreteAction(String name, LoggerManager logger) {
	    super(name);
	    this.logger = logger;
	}
	@Override
	public void execute() {
	    logger.log("⏩ Ejecutando acción discreta: " + name, true, true);
	}
}
