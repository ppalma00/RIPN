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
            logger.logTR("⏳ Acción durativa INICIADA: " + name);
        }
    }

    public void stop() {
        if (active) {
            active = false;
            logger.logTR("✅ Acción durativa FINALIZADA: " + name);
        }
    }

    @Override
    public void execute() {
        // Las acciones durativas no necesitan ejecutar algo específico
        // Solo se inician o se detienen por TRProgram
    }
}
