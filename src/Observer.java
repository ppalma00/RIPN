public interface Observer {
    void onDiscreteActionExecuted(String actionName, double[] parameters);
    void onDurativeActionStarted(String actionName, double[] parameters);
    void onDurativeActionStopped(String actionName);
}
