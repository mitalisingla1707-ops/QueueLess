package queueless.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import queueless.exception.QueueLessException;
import queueless.model.Customer;
import queueless.model.Priority;
import queueless.repository.CustomerRepository;
import queueless.util.AppLogger;
import java.util.concurrent.atomic.AtomicInteger;
import queueless.util.Validator;

/** Customer registration and maintenance. */
public class CustomerService {

    private final CustomerRepository repo;
    private final AtomicInteger ids = new AtomicInteger();

    public CustomerService(CustomerRepository repo) throws QueueLessException {
        this.repo = repo;
        repo.findAll().stream().mapToInt(Customer::getId).max().ifPresent(v -> ids.accumulateAndGet(v, Math::max));
    }

    public static Priority suggestPriority(int age) {
        return age >= 60 ? Priority.SENIOR_CITIZEN : Priority.REGULAR;
    }

    public synchronized Customer register(String name, String phone, int age, Priority priority)
            throws QueueLessException {
        String cleanName = Validator.validateName(name);
        String cleanPhone = Validator.validatePhone(phone);
        int cleanAge = Validator.validateAge(age);
        if (repo.findByPhone(cleanPhone).isPresent()) throw QueueLessException.duplicateCustomer(cleanPhone);
        Customer c = new Customer(ids.incrementAndGet(), cleanName, cleanPhone, cleanAge, priority, LocalDateTime.now());
        repo.save(c);
        AppLogger.info("Registered customer " + c);
        return c;
    }

    /** Used by CSV import: keeps the data but assigns a fresh id; duplicates (same phone) are rejected. */
    public synchronized Customer importCustomer(Customer c) throws QueueLessException {
        if (repo.findByPhone(c.getPhone()).isPresent()) throw QueueLessException.duplicateCustomer(c.getPhone());
        c.setId(ids.incrementAndGet());
        repo.save(c);
        return c;
    }

    public Customer getById(int id) throws QueueLessException {
        return repo.findById(id).orElseThrow(() -> QueueLessException.invalidInput("No customer with id " + id));
    }

    public Optional<Customer> findByPhone(String phone) throws QueueLessException {
        return repo.findByPhone(phone.trim());
    }

    public List<Customer> listAll() throws QueueLessException {
        return repo.findAll().stream().sorted().collect(Collectors.toList());
    }

    public List<Customer> search(String keyword) throws QueueLessException {
        String k = keyword.trim().toLowerCase();
        return repo.findAll().stream()
                .filter(c -> c.getName().toLowerCase().contains(k) || c.getPhone().contains(k))
                .sorted()
                .collect(Collectors.toList());
    }

    public synchronized Customer updatePhone(int id, String newPhone) throws QueueLessException {
        String phone = Validator.validatePhone(newPhone);
        Customer c = getById(id);
        Optional<Customer> owner = repo.findByPhone(phone);
        if (owner.isPresent() && owner.get().getId() != id) throw QueueLessException.duplicateCustomer(phone);
        c.setPhone(phone);
        repo.update(c);
        AppLogger.info("Updated phone of customer " + id);
        return c;
    }

    public synchronized boolean delete(int id) throws QueueLessException {
        boolean removed = repo.deleteById(id);
        if (removed) AppLogger.info("Deleted customer " + id);
        return removed;
    }

    public int count() throws QueueLessException { return repo.count(); }
}
