package tr;
import both.LoggerManager;
public class DurativeAction extends Action {
    private boolean active = false;
    private final LoggerManager logger;
    public DurativeAction(String name, LoggerManager logger) {
        super(name);
        this.logger = logger;
    }
    public void start() {
        if (!active) {
            active = true;
            logger.log("⏳ Acción durativa INICIADA: " + name, true, true);
        }
    }
    public void stop() {
        if (active) {
            active = false;
            logger.log("✅ Acción durativa FINALIZADA: " + name, true, true);
        }
    }
    @Override
    public void execute() {    
    }
}
