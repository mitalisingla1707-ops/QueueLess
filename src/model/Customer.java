package queueless.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import queueless.exception.QueueLessException;
import queueless.util.Validator;

/** A registered customer / visitor of the service centre. */
public class Customer implements Comparable<Customer> {

    public static final String CSV_HEADER = "id,name,phone,age,priority,registeredAt";

    private int id;
    private final String name;
    private String phone;
    private final int age;
    private final Priority priority;
    private final LocalDateTime registeredAt;

    public Customer(int id, String name, String phone, int age, Priority priority, LocalDateTime registeredAt) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.age = age;
        this.priority = priority;
        this.registeredAt = registeredAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public int getAge() { return age; }
    public Priority getPriority() { return priority; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }

    /** Serialises this customer as one CSV line (names are validated to contain no commas). */
    public String toCsv() {
        return id + "," + name + "," + phone + "," + age + "," + priority.name() + "," + registeredAt;
    }

    /** Parses and validates one CSV line. The id column is ignored; the caller assigns a fresh id. */
    public static Customer fromCsv(String line) throws QueueLessException {
        String[] p = line.split(",", -1);
        if (p.length != 6) {
            throw QueueLessException.invalidInput("Expected 6 columns but found " + p.length);
        }
        try {
            String name = Validator.validateName(p[1]);
            String phone = Validator.validatePhone(p[2]);
            int age = Validator.validateAge(Integer.parseInt(p[3].trim()));
            Priority priority = Priority.valueOf(p[4].trim());
            LocalDateTime at = LocalDateTime.parse(p[5].trim());
            return new Customer(0, name, phone, age, priority, at);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw QueueLessException.invalidInput("Invalid value in row: " + e.getMessage());
        }
    }

    @Override
    public int compareTo(Customer other) {
        return name.compareToIgnoreCase(other.name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer)) return false;
        return phone.equals(((Customer) o).phone);
    }

    @Override
    public int hashCode() { return Objects.hash(phone); }

    @Override
    public String toString() {
        return "Customer{" + id + ", " + name + ", " + phone + ", " + priority + "}";
    }
}
