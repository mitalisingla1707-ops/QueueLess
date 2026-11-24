package queueless.model;

/** Services offered by the centre. Each counter handles exactly one service type. */
public enum ServiceType {
    GENERAL_ENQUIRY("GEN", "General Enquiry"),
    PAYMENT("PAY", "Payments & Billing"),
    DOCUMENTATION("DOC", "Documentation"),
    CONSULTATION("CON", "Consultation");

    private final String prefix;
    private final String label;

    ServiceType(String prefix, String label) {
        this.prefix = prefix;
        this.label = label;
    }

    public String getPrefix() { return prefix; }
    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}
