package queueless.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** One completed service (a row of the service history). */
public class ServiceRecord {

    public static final String CSV_HEADER =
            "recordId,tokenNumber,customerId,customerName,serviceType,counterName,issuedAt,startedAt,completedAt";

    private final int recordId;
    private final String tokenNumber;
    private final int customerId;
    private final String customerName;
    private final ServiceType serviceType;
    private final String counterName;
    private final LocalDateTime issuedAt;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;

    public ServiceRecord(int recordId, String tokenNumber, int customerId, String customerName,
                         ServiceType serviceType, String counterName,
                         LocalDateTime issuedAt, LocalDateTime startedAt, LocalDateTime completedAt) {
        this.recordId = recordId;
        this.tokenNumber = tokenNumber;
        this.customerId = customerId;
        this.customerName = customerName;
        this.serviceType = serviceType;
        this.counterName = counterName;
        this.issuedAt = issuedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public int getRecordId() { return recordId; }
    public String getTokenNumber() { return tokenNumber; }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public ServiceType getServiceType() { return serviceType; }
    public String getCounterName() { return counterName; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }

    public long getWaitSeconds() { return Duration.between(issuedAt, startedAt).getSeconds(); }
    public long getServiceSeconds() { return Duration.between(startedAt, completedAt).getSeconds(); }

    public String toCsv() {
        return recordId + "," + tokenNumber + "," + customerId + "," + customerName + ","
                + serviceType.name() + "," + counterName + ","
                + issuedAt + "," + startedAt + "," + completedAt;
    }

    @Override
    public String toString() {
        return tokenNumber + " served by " + counterName;
    }
}
