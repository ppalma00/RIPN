package pn;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import both.LoggerManager;
import bs.BeliefStore;
import bs.Observer;
/*
 * FACTS: dos
VARSINT: x; y
VARSREAL: 
INIT: x:=1; y:=1
DISCRETE: act1(); act3(INT, REAL)
DURATIVE: act2(INT); act4(INT)
# hola 

PLACES: p1; p2
TRANSITIONS: t1; t2
ARCS: p1->t1; t1->p2; p2->t2; t2->p1
INITMARKING: (1,0)

<pn>
p1: [x:=5; act1(); act3(5,6.7); act4(5); remember(dos)] if (y>=1)
p2: [x:=x+1; act2(8)] 
t2: [x:=1]
 */
/*
 * Versión que considera condiciones en lugares y transiciones con '_' en hechos con parámetros, 
 * y funcionan acciones discretas y durativas con y sin parámetros.
 * incluye temporizadores arcos inhibidores 
 * voy a trabajar los evento externos
 */
public class MainPN implements Observer {
    public static void main(String[] args) {
    	LoggerManager logger = new LoggerManager(false);
        try {
            // 1️⃣ Crear o recibir una instancia de BeliefStore
            BeliefStore beliefStore = new BeliefStore();

            // 2️⃣ Crear estructura para almacenar las actualizaciones de variables en los lugares
            Map<String, List<String>> placeVariableUpdates = new HashMap<>();

            // 3️⃣ Nombre del archivo de configuración
            String filename = "RIPN_PN.txt";

            // 4️⃣ Cargar datos en la BeliefStore (variables, hechos, acciones)
            BeliefStoreLoader.loadFromFile(filename, beliefStore, logger);
 
            // 6️⃣ Cargar la red de Petri desde el mismo archivo
            PetriNet net = PetriNetLoader.loadFromFile(filename, beliefStore);
            MainPN observer = new MainPN();
            net.setObserver(observer);
            // 7️⃣ Asociar las reglas de modificación de variables en los lugares
            net.setPlaceVariableUpdates(placeVariableUpdates);
         // 8️⃣ Cargar modificaciones de variables en transiciones
            Map<String, List<String>> transitionVariableUpdates = new HashMap<>();
         // 7️⃣ Estructuras para almacenar condiciones en lugares y transiciones
            Map<String, String> placeConditions = new HashMap<>();
            Map<String, String> transitionConditions = new HashMap<>();
            Map<String, String> placeDiscreteActions = new HashMap<>();
            // 8️⃣ Cargar modificaciones de variables y condiciones desde <pn>
            BeliefStoreLoader.loadPNVariableUpdates(filename, net, placeVariableUpdates, placeConditions, transitionVariableUpdates, transitionConditions, placeDiscreteActions, logger);
            net.setPlaceVariableUpdates(placeVariableUpdates);
            net.setPlaceConditions(placeConditions);
            net.setTransitionVariableUpdates(transitionVariableUpdates);
            net.setTransitionConditions(transitionConditions);
            net.setPlaceDiscreteActions(placeDiscreteActions);
            
            Map<String, Boolean> emptyMarking = new HashMap<>(); // todo falso

            for (String placeName : net.getPlaces().keySet()) {
                Place place = net.getPlaces().get(placeName);
                if (place.hasToken()) {
                    net.executePlaceActions(placeName);          
                }
            }

            // 💡 Notificar también acciones durativas
            net.updateDurativeActions(emptyMarking); // simula paso de 0 → 1      
            net.printState();

         // 🔟 Iniciar la simulación automática con un intervalo de 1000ms
            PetriNetAnimator animator = new PetriNetAnimator(net, 2000, logger);
            new Thread(animator).start();

        } catch (IOException e) {
        	logger.logPN("❌ Error loading files: " + e.getMessage());
        }
    }
    @Override
    public void onDiscreteActionExecuted(String actionName, double[] parameters) {
        System.out.println("Observer: Executing discrete action: " + actionName + " with parameters: " + Arrays.toString(parameters));
    }

    @Override
    public void onDurativeActionStarted(String actionName, double[] parameters) {
        System.out.println("Observer: Starting durative action: " + actionName + " with parameters: " + Arrays.toString(parameters));
    }

    @Override
    public void onDurativeActionStopped(String actionName) {
        System.out.println("Observer: Stopping durative action: " + actionName);
    }
}
