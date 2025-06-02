package guiEvents;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventPool {

    private static final EventPool instance = new EventPool();
    private final Map<String, EventSpec> declaredEvents = new HashMap<>();
    private final Map<String, List<EventInstance>> activeEvents = new HashMap<>();
    private final List<String> unattendedEvents = new ArrayList<>();
    private static final String LOG_FILE = "log_EV.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    static {
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, false)); // Sobrescribe el archivo
            writer.println("[LOG STARTED] " + LocalDateTime.now().format(formatter));
            writer.close();
        } catch (IOException e) {
            System.err.println("❌ Error initializing event log: " + e.getMessage());
        }
    }

    private EventPool() {}

    public static EventPool getInstance() {
        return instance;
    }

    public synchronized void registerEvent(String name, double timeToLive, String... parameterTypes) {
        declaredEvents.put(name, new EventSpec(timeToLive, Arrays.asList(parameterTypes)));
    }

    public synchronized void printDeclaredEvents() {
    	dumpActiveEvents("Declared events listed");
    }

    public synchronized void clearUnattendedEvents() {
        unattendedEvents.clear();
        dumpActiveEvents("Cleared unattended events");
    }

    public synchronized void addEvent(String eventName, double... parameters) {
        cleanExpiredEvents();
        EventSpec spec = declaredEvents.get(eventName);
        if (spec == null) {
            unattendedEvents.add(eventName + Arrays.toString(parameters) + " (undeclared)");
            dumpActiveEvents("Event NOT declared: " + eventName);
            return;
        }
        if (parameters.length != spec.parameterTypes.size()) {
            unattendedEvents.add(eventName + Arrays.toString(parameters) + " (wrong arity)");
            dumpActiveEvents("Event wrong arity: " + eventName);
            return;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (spec.parameterTypes.get(i).equalsIgnoreCase("INT") && parameters[i] != Math.floor(parameters[i])) {
                unattendedEvents.add(eventName + Arrays.toString(parameters) + " (type mismatch)");
                dumpActiveEvents("Event type mismatch: " + eventName);
                return;
            }
        }
        activeEvents.computeIfAbsent(eventName, k -> new ArrayList<>())
                    .add(new EventInstance(parameters, spec.timeToLiveSeconds));
        dumpActiveEvents("Event added: " + eventName);  
    }
    public Map<String, EventSpec> getDeclaredEvents() {
        return Collections.unmodifiableMap(declaredEvents);
    }
    public boolean hasDeclaredEvents() {
        return declaredEvents != null && !declaredEvents.isEmpty();
    }

    public synchronized EventInstance consumeEvent(String eventName) {
        cleanExpiredEvents();
        List<EventInstance> list = activeEvents.get(eventName);
        if (list != null && !list.isEmpty()) {
            EventInstance instance = list.remove(0);
            if (list.isEmpty()) {
                activeEvents.remove(eventName);
            }
            dumpActiveEvents("Event consumed: " + eventName + " with parameters " + Arrays.toString(instance.getParameters()));
            return instance;
        }
        return null;
    }

    public synchronized void deleteAll() {
        activeEvents.clear();
        unattendedEvents.clear();
        dumpActiveEvents("All events deleted");
    }

    public synchronized void cleanExpiredEvents() {
        long now = System.currentTimeMillis();
        AtomicBoolean anyExpired = new AtomicBoolean(false);

        Iterator<Map.Entry<String, List<EventInstance>>> it = activeEvents.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<EventInstance>> entry = it.next();
            entry.getValue().removeIf(e -> {
                if (e.isExpired(now)) {
                    anyExpired.set(true);
                    return true;
                }
                return false;
            });
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }

        if (anyExpired.get()) {
            dumpActiveEvents("Expired events cleaned");
        }
    }

    private void dumpActiveEvents(String action) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(formatter);
            writer.println("[" + timestamp + "] " + action);
            writer.println("Active Events:");
            for (Map.Entry<String, List<EventInstance>> entry : activeEvents.entrySet()) {
                for (EventInstance instance : entry.getValue()) {
                    writer.println("   " + entry.getKey() + " " + Arrays.toString(instance.getParameters()) + " expires in " + instance.getRemainingTime() + " ms");
                }
            }
            writer.println("Unattended Events:");
            for (String ev : unattendedEvents) {
                writer.println("   " + ev);
            }
            writer.println();
        } catch (IOException e) {
            System.err.println("❌ Error writing to event log: " + e.getMessage());
        }
    }

    // Subclasses
    public static class EventSpec {
        double timeToLiveSeconds;
        List<String> parameterTypes;

        public EventSpec(double ttl, List<String> types) {
            this.timeToLiveSeconds = ttl;
            this.parameterTypes = types;
        }
    }
    public static class EventInstance {
        private final double[] parameters;
        private final long expirationTimeMs;

        public EventInstance(double[] parameters, double timeToLiveSeconds) {
            this.parameters = parameters;
            if (timeToLiveSeconds > 0) {
                this.expirationTimeMs = System.currentTimeMillis() + (long) (timeToLiveSeconds * 1000);
            } else {
                this.expirationTimeMs = Long.MAX_VALUE;
            }
        }
        public double[] getParameters() {
            return parameters;
        }
        public boolean isExpired(long now) {
            return now > expirationTimeMs;
        }
        public long getRemainingTime() {
            return expirationTimeMs - System.currentTimeMillis();
        }
    }
}
