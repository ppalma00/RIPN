package PN;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PetriNetAnimator implements Runnable {
    private PetriNet net;
    private int refreshRate;

    public PetriNetAnimator(PetriNet net, int refreshRate) {
        this.net = net;
        this.refreshRate = refreshRate;
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
                    .filter(t -> !net.hasPNDefinition(t)) // inmediatas = no están en <PN>
                    .collect(Collectors.toList());

            while (!immediateTransitions.isEmpty()) {
                String t = immediateTransitions.get(0);
                Map<String, Boolean> beforeFire = net.captureCurrentMarking();
                List<String> discreteActions = net.fire(t);
                net.updateDurativeActions(beforeFire);
                Observer observer = net.getObserver();
                for (String action : discreteActions) {
                 //   System.out.println("🔔 Notificando acción discreta: " + action);
                    if (observer != null) {
                        observer.onDiscreteActionExecuted(action, new double[0]);
                    }
                }

                net.printState();

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
                System.out.println("No more transitions can fire. Stopping simulation.");
                break;
            }

            String t = nonImmediate.get((int) (Math.random() * nonImmediate.size()));
            Map<String, Boolean> beforeFire = net.captureCurrentMarking();
            List<String> discreteActions = net.fire(t);
        //    System.out.println("✅ Acciones devueltas por fire(t): " + discreteActions);
            net.updateDurativeActions(beforeFire);

            Observer observer = net.getObserver();
            for (String action : discreteActions) {
              //  System.out.println("🔔 Notificando acción discreta: " + action);
                if (observer != null) {
                    observer.onDiscreteActionExecuted(action, new double[0]);
                }
            }

            net.printState();


            // 🕒 Esperar antes del siguiente ciclo
            try {
                Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
                System.out.println("Simulation interrupted.");
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
                System.out.println("No more transitions can fire. Stopping simulation.");
                break;
            }

            String transitionToFire = enabledTransitions.get((int) (Math.random() * enabledTransitions.size()));
            net.fire(transitionToFire);
          //   System.out.println("🔥 Transition fired: " + transitionToFire);

            try {
                Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
                System.out.println("Simulation interrupted.");
                break;
            }
        }
    }
}
