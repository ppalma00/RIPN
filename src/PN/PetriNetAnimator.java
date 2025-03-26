package PN;

import java.util.List;
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

            try {
                Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
                System.out.println("Simulation interrupted.");
                break;
            }
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
