public class DiscreteAction extends Action {
    public DiscreteAction(String name) {
        super(name);
    }

    @Override
    public void execute() {
        System.out.println("⏩ Ejecutando acción discreta: " + name);
        // TRProgram se encarga de notificar al observador
    }
}
