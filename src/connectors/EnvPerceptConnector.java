/*
 * This class would be the connector to forward new PERCEPTS from the environment to the TR program
 * 
 How to use it:
 Include the following in the main class
 	EnvPerceptConnector connector = new EnvPerceptConnector(beliefStore, logger);
	connector.start();
 And to stop it:
 	connector.stopConnector();
 
 Replace this:
	String perceptFact = "see(200)";

 with the actual reading from your environment:
	Hardware sensors → serial port, USB, etc.
	MQTT messages
	TCP/UDP sockets
	Files that get updated continuously
	Industrial messaging buses (CAN, MODBUS, etc.)
*/
package connectors;
import bs.BeliefStore;
import both.LoggerManager;

public class EnvPerceptConnector extends Thread {

    private final BeliefStore beliefStore;
    private final LoggerManager logger;
    private volatile boolean running = true;

    public EnvPerceptConnector(BeliefStore beliefStore, LoggerManager logger) {
        this.beliefStore = beliefStore;
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.log("🌐 EnvPerceptConnector started.", false, false);

        while (running) {
            try {
                // Simulated data source (replace this with real sensor input, network, etc.)
                Thread.sleep(3000); // Simulate delay

                // Example: receiving a new percept
                String perceptFact = "see(200)";

                addPercept(perceptFact);

                // Example: removing a percept
                removePercept(perceptFact);

            } catch (InterruptedException e) {
                logger.log("Error: EnvPerceptConnector interrupted: " + e.getMessage(), true, false);
                running = false;
            }
        }
    }

    /**
     * Call this method to stop the connector thread gracefully.
     */
    public void stopConnector() {
        running = false;
    }

    /**
     * Adds a percept fact to the BeliefStore.
     * @param fact the fact string, e.g. "see(200)"
     */
    public void addPercept(String fact) {
        beliefStore.addFact(fact);
        logger.log("Msg: Percept added: " + fact, false, false);
    }

    /**
     * Removes a percept fact from the BeliefStore.
     * Supports wildcards in parameters.
     * @param fact the fact string, e.g. "see(_)"
     */
    public void removePercept(String fact) {
        if (fact.contains("_")) {
            beliefStore.removeFactWithWildcard(fact);
            logger.log("Msg:️ Percept removed with wildcard: " + fact, false, false);
        } else {
            beliefStore.removeFact(fact);
            logger.log("Msg:️ Percept removed: " + fact, false, false);
        }
    }
}
