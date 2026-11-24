package queueless.model;

/** A service counter that serves one {@link ServiceType}. */
public class ServiceCounter {

    private final int id;
    private final String name;
    private final ServiceType service;
    private boolean open = true;
    private int currentTokenId;   // 0 = idle
    private int servedCount;

    public ServiceCounter(int id, String name, ServiceType service) {
        this.id = id;
        this.name = name;
        this.service = service;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public ServiceType getService() { return service; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public int getCurrentTokenId() { return currentTokenId; }
    public void setCurrentTokenId(int currentTokenId) { this.currentTokenId = currentTokenId; }
    public boolean isBusy() { return currentTokenId != 0; }
    public int getServedCount() { return servedCount; }
    public void incrementServed() { servedCount++; }

    public String stateLabel() {
        if (!open) return "CLOSED";
        return isBusy() ? "BUSY" : "IDLE";
    }

    @Override
    public String toString() { return name + " (" + service + ")"; }
}
