package queueless.model;

/** Customer priority categories. A lower level number is served first. */
public enum Priority {
    EMERGENCY(1, "Emergency"),
    SENIOR_CITIZEN(2, "Senior Citizen"),
    DIFFERENTLY_ABLED(2, "Differently Abled"),
    REGULAR(3, "Regular");

    private final int level;
    private final String label;

    Priority(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int getLevel() { return level; }
    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}
