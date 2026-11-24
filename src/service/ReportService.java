package queueless.service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import queueless.exception.QueueLessException;
import queueless.model.Customer;
import queueless.model.Priority;
import queueless.model.ServiceRecord;
import queueless.model.ServiceType;
import queueless.model.Token;
import queueless.model.TokenStatus;

/** Reports built entirely with lambdas and the Stream API. */
public class ReportService {

    private final HistoryService history;
    private final CustomerService customers;
    private final TokenService tokens;

    public ReportService(HistoryService history, CustomerService customers, TokenService tokens) {
        this.history = history;
        this.customers = customers;
        this.tokens = tokens;
    }

    public double averageWaitSeconds() throws QueueLessException {
        return history.all().stream().mapToLong(ServiceRecord::getWaitSeconds).average().orElse(0);
    }

    public double averageServiceSeconds() throws QueueLessException {
        return history.all().stream().mapToLong(ServiceRecord::getServiceSeconds).average().orElse(0);
    }

    public Map<ServiceType, Long> servedPerService() throws QueueLessException {
        return history.all().stream().collect(Collectors.groupingBy(
                ServiceRecord::getServiceType, () -> new EnumMap<>(ServiceType.class), Collectors.counting()));
    }

    public Map<String, Long> servedPerCounter() throws QueueLessException {
        return history.all().stream().collect(Collectors.groupingBy(
                ServiceRecord::getCounterName, TreeMap::new, Collectors.counting()));
    }

    public Map<Priority, Long> customersByPriority() throws QueueLessException {
        return customers.listAll().stream().collect(Collectors.groupingBy(
                Customer::getPriority, () -> new EnumMap<>(Priority.class), Collectors.counting()));
    }

    public Map<TokenStatus, Long> tokensByStatus() throws QueueLessException {
        return tokens.all().stream().collect(Collectors.groupingBy(
                Token::getStatus, () -> new EnumMap<>(TokenStatus.class), Collectors.counting()));
    }

    public List<ServiceRecord> longestWaits(int n) throws QueueLessException {
        return history.all().stream()
                .sorted(Comparator.comparingLong(ServiceRecord::getWaitSeconds).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /** Pagination with skip() and limit(); page numbers start at 1. */
    public List<ServiceRecord> historyPage(int page, int size) throws QueueLessException {
        return history.all().stream()
                .sorted(Comparator.comparingInt(ServiceRecord::getRecordId).reversed())
                .skip((long) (page - 1) * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    public List<String> distinctCustomersServed() throws QueueLessException {
        return history.all().stream()
                .map(ServiceRecord::getCustomerName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
