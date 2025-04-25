package pn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import both.LoggerManager;
import bs.Observer;

public class PetriNetAnimator implements Runnable {
    private PetriNet net;
    private int refreshRate;
    private LoggerManager logger;

    public PetriNetAnimator(PetriNet net, int refreshRate, LoggerManager logger) {
        this.net = net;
        this.refreshRate = refreshRate;
        this.logger = logger;
    }
    
    @Override
    public void run() {
        Map<String, Boolean> previousMarking = net.captureCurrentMarking();

        while (true) {
            // 🔄 1. Disparar transiciones inmediatas mientras haya
            List<String> enabledTransitions = net.getTransitions().keySet().stream()
                    .filter(net::canFire)
                    .collect(Collectors.toList());

            List<String> immediateTransitions = enabledTransitions.stream()
                    .filter(t -> !net.hasPNDefinition(t)) // inmediatas = no están en <pn>
                    .collect(Collectors.toList());

            while (!immediateTransitions.isEmpty()) {
                String t = immediateTransitions.get(0);
                Map<String, Boolean> beforeFire = net.captureCurrentMarking();
                List<String> discreteActions = net.fire(t);
                net.updateDurativeActions(beforeFire);
                net.checkExpiredTimers(); 
                Observer observer = net.getObserver();
                for (String action : discreteActions) {             
                    if (observer != null) {
                        observer.onDiscreteActionExecuted(action, new double[0]);
                    }
                }

                net.printState();
                try {
                    Thread.sleep(refreshRate); // 💤 dormir tras cada transición
                } catch (InterruptedException e) {
                    return;
                }
                // Recalcular transiciones habilitadas tras disparo
                enabledTransitions = net.getTransitions().keySet().stream()
                        .filter(net::canFire)
                        .collect(Collectors.toList());

                immediateTransitions = enabledTransitions.stream()
                        .filter(tr -> !net.hasPNDefinition(tr))
                        .collect(Collectors.toList());
            }

            // 🔵 2. Estado estable alcanzado (sin inmediatas habilitadas)
            //     No hacemos nada aquí — ya se actualizaron durativas tras cada fire()

            // 🔥 3. Disparar una transición no inmediata si hay
            List<String> nonImmediate = enabledTransitions.stream()
                    .filter(net::hasPNDefinition)
                    .collect(Collectors.toList());

            if (nonImmediate.isEmpty()) {
            	logger.log("No more transitions can fire. Stopping simulation.", true, true);
                break;
            }

            String t = nonImmediate.get((int) (Math.random() * nonImmediate.size()));
            Map<String, Boolean> beforeFire = net.captureCurrentMarking();
            List<String> discreteActions = net.fire(t);
            net.updateDurativeActions(beforeFire);

            Observer observer = net.getObserver();
            for (String action : discreteActions) {       
                if (observer != null) {
                    observer.onDiscreteActionExecuted(action, new double[0]);
                }
            }
            net.checkExpiredTimers();
            net.printState();


            // 🕒 Esperar antes del siguiente ciclo
            try {
                Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
            	logger.log("Simulation interrupted.", true, true);
                break;
            }

            // Actualizar marcado anterior para el siguiente ciclo completo
            previousMarking = net.captureCurrentMarking();
        }
    }
    
    public void simulate() {
        while (true) {
            net.printState();

            List<String> enabledTransitions = net.getTransitions().keySet().stream()
                    .filter(net::canFire)
                    .collect(Collectors.toList());

            if (enabledTransitions.isEmpty()) {
            	logger.log("No more transitions can fire. Stopping simulation.", true, true);
                break;
            }

            String transitionToFire = enabledTransitions.get((int) (Math.random() * enabledTransitions.size()));
            net.fire(transitionToFire);
            try {
                Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
            	logger.log("Simulation interrupted.", true, true);
                break;
            }
        }
    }
}
