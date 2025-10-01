/*
 * This class would be the connector to forward new EVENTS from the environment to the Petri net.
 * 
 How to use it:
 Include the following in the main class
 	EnvEventConnector connector = new EnvEventConnector(beliefStore, logger);
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
import both.LoggerManager;
import guiEvents.EventPool;

public class EnvEventConnector extends Thread {

    private final LoggerManager logger;
    private volatile boolean running = true;

    public EnvEventConnector(LoggerManager logger) {
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.log("Msg: EnvEventConnector started.", false, false);

        while (running) {
            try {
                // Simulate waiting for new events from the environment
                Thread.sleep(3000);

                // Example: a real event coming from hardware, network, etc.
                String eventName = "sensorUpdate";
                double[] params = { 100.0, 200.0 };

                addEvent(eventName, params);

            } catch (InterruptedException e) {
                logger.log("Error: EnvEventConnector interrupted: " + e.getMessage(), true, false);
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
     * Adds an event to the EventPool.
     * @param eventName the name of the event
     * @param params array of parameters
     */
    public void addEvent(String eventName, double[] params) {
        EventPool.getInstance().addEvent(eventName, params);
        logger.log("Msg: Event added: " + eventName + " → " + java.util.Arrays.toString(params), false, false);
    }
}

