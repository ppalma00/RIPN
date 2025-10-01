package Z3;

public class PlaceActionZ3 {
    public String placeName;
    public String discreteAction;        // Ej: act
    public String durativeAction;        // Ej: keepopen
    public String rememberFact;          // Ej: see
    public String forgetFact;            // Ej: see
    public String variableAssign;        // Ej: x := 4

    public PlaceActionZ3(String placeName) {
        this.placeName = placeName;
    }
}

