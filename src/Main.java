
import java.util.Arrays;

import core.BeliefStore;
/*
 * 
FACTS: uno(INT), dos
VARSINT: x, y
VARSREAL: z
DISCRETE: abrir(INT), cerrar(REAL)
DURATIVE: alarma()
TIMERS: t1
INIT: x:=0, y:=88, z:=4.56

<TR>
y==2 -> alarma() ++ x:=0, y:=0, forget(dos)
x==4 -> cerrar(1.3) ++ y:=2
x==3 -> abrir(2) ++ x:=4, forget(uno(3))
t1.end -> ++forget(t1.end), x:=3
x==2 -> t1.continue()
uno(3)-> t1.stop()++x:=2
True -> alarma(), t1.start(1) ++x:=1, remember(uno(4)), remember(dos)

*/

public class Main implements Observer {
    public static void main(String[] args) {
        try {
            BeliefStore beliefStore = new BeliefStore();
            String trFilePath = "RIPN_TR.txt"; 
            TRProgram program = TRParser.parse(trFilePath, beliefStore);

            // Agregar `Main` como observador
            Main observer = new Main();
            program.addObserver(observer);

            System.out.println("Initiating TR program...");
            new Thread(() -> program.run()).start();
            Thread.sleep(20000);

            System.out.println("\nFinal state of the BeliefStore:");
            beliefStore.dumpState();

            System.out.println("\nStopping TR program...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDiscreteActionExecuted(String actionName, double[] parameters) {
        System.out.println("Observer: discrete action executed: " + actionName + " with parameters: " + Arrays.toString(parameters));
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
