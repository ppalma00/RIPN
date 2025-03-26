public class DurativeAction extends Action {
    private boolean active = false;

    public DurativeAction(String name) {
        super(name);
    }

    public void start() {
        if (!active) {
            active = true;
            System.out.println("⏳ Acción durativa INICIADA: " + name);
            // TRProgram se encarga de notificar al observador
        }
    }

    public void stop() {
        if (active) {
            active = false;
            System.out.println("✅ Acción durativa FINALIZADA: " + name);
            // TRProgram se encarga de notificar al observador
        }
    }

    @Override
    public void execute() {
        // Las acciones durativas no necesitan ejecutar algo específico
        // Solo se inician o se detienen por TRProgram
    }
}
