package PN;

public class Place {
    String name;
    boolean token;

    public Place(String name, boolean token) {
        this.name = name;
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public boolean hasToken() {
        return token;
    }

    public void setToken(boolean token) {
        this.token = token;
    }
}

