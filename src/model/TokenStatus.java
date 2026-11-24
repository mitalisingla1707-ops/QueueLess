package queueless.model;

/** Life-cycle of a token. */
public enum TokenStatus {
    WAITING("Waiting"),
    SERVING("Being served"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled / No-show");

    private final String label;

    TokenStatus(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
