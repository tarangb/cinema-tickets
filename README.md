# Ticket Booking System

## Table of Contents
#### Overview
#### Features
#### Validation Rules
#### Assumptions
#### Exception Handling
#### Atomicity and Idempotency
#### Usage
#### Testing
#### Future Enhancements


### Overview

The Ticket Booking System is a Java-based application designed to handle ticket purchase requests. It validates each request based on predefined business rules and processes payments and seat reservations through external services. The system ensures atomicity and idempotency, preventing duplicate transactions and maintaining data consistency.



### Features

- Validation of Purchase Requests: Ensures that all ticket purchase requests adhere to business rules.
- Payment Processing: Integrates with an external payment service to handle transactions.
- Seat Reservation: Integrates with an external seat reservation service to allocate seats.
- Thread Safety: Utilizes synchronization to ensure thread-safe operations.
- Idempotency: Prevents duplicate processing of the same purchase request.
- Exception Handling: Provides clear and specific error messages for different failure scenarios.

### Validation Rules

The system enforces the following business rules for each ticket purchase request:
- Valid Account ID:
  - The accountId must be a positive number (> 0).
  - Failure Message: "Invalid account ID: [accountId]"
- Non-Empty Ticket Requests:
  - At least one TicketTypeRequest must be provided.
  - Failure Message: "No tickets requested."
- Maximum Tickets Per Purchase:
  - The total number of tickets requested cannot exceed 25.
  - Failure Message: "Cannot purchase more than 25 tickets at a time."
- Adult Ticket Requirement:
  - If child or infant tickets are requested, at least one adult ticket must also be purchased.
  - Failure Message: "Must purchase at least one adult ticket when buying child or infant tickets."
- Infant-to-Adult Ratio:
  - The number of infant tickets cannot exceed the number of adult tickets (assuming one infant per adult).
  - Failure Message: "Number of infant tickets cannot exceed number of adult tickets."

### Assumptions

- Sufficient Account Balance: The system assumes that the user's account has sufficient balance to cover the cost of the tickets. No balance checks are performed during the purchase process.
- External Services Availability: It is assumed that both the TicketPaymentService and SeatReservationService are reliable and available when processing requests.
- Single Instance Deployment: The current implementation is designed for a single-instance deployment. Handling concurrency across multiple instances would require additional mechanisms.

### Exception Handling

The system uses a custom exception class, InvalidPurchaseException, to handle validation and processing errors. Each validation rule violation results in an InvalidPurchaseException with a specific error message, aiding in debugging and user feedback.
#### Examples:
##### Invalid account ID:
throw new InvalidPurchaseException("Invalid account ID: " + accountId);

##### Exceeding maximum tickets:
throw new InvalidPurchaseException("Cannot purchase more than 25 tickets at a time.");

##### Missing adult tickets:
throw new InvalidPurchaseException("Must purchase at least one adult ticket when buying child or infant tickets.");

### Atomicity and Idempotency

#### Atomicity

Atomicity ensures that a series of operations either all succeed or all fail, maintaining data consistency. In this system:
Payment and Seat Reservation as an Atomic Unit: The processes of making a payment and reserving seats are treated as a single atomic transaction. If either operation fails, the entire transaction is rolled back to prevent partial processing.

#####  Implementation:
The purchaseTickets method is synchronized, ensuring that only one thread can execute it at a time. This synchronization helps in maintaining atomicity by preventing concurrent modifications and ensuring that payment and reservation steps are completed together.

#### Idempotency

Idempotency ensures that multiple identical requests have the same effect as a single request, preventing duplicate transactions.

##### Implementation:

- Processed Requests Tracking: The system maintains a thread-safe Set called processedRequests using a ConcurrentHashMap to track unique purchase requests based on a combination of accountId and ticket types.
- Unique Request Key: Each purchase request is encapsulated in a RequestKey object, which combines the accountId and a sorted list of TicketTypeRequest objects. This key is used to check if a request has already been processed.
- Duplicate Request Handling: If a request with the same RequestKey is detected, the system throws an InvalidPurchaseException indicating that the request has already been processed.

#### Recommendation for Database Implementation:

To achieve true atomicity and idempotency in a production environment, especially across application restarts or multiple instances, consider the following enhancements using a database:
- Database Transactions:
  - Begin Transaction: Start a transaction before processing the purchase.
  - Commit Transaction: Commit the transaction after successful payment and seat reservation.
  - Rollback Transaction: Rollback the transaction if any step fails, ensuring that no partial data is persisted.

- Idempotency Keys in Database:
  - Unique Constraint: Store each RequestKey in a database table with a unique constraint to prevent duplicate entries.
  - Atomic Insert: Attempt to insert the RequestKey as part of the transaction. If the insert fails due to a duplicate key, recognize it as a duplicate request and handle accordingly.

- Consistent State Management:
  - Ensure that both payment and seat reservation updates are part of the same transactional scope to maintain consistency.

### Usage

#### Prerequisites
- Java Development Kit (JDK): Ensure that JDK 11 or higher is installed.
- Build Tool: Maven (pom.xml file is added).
- External Services: Implementations of TicketPaymentService and SeatReservationService are required. Mock implementations are provided for testing purposes.

##### Running the Application
###### Clone the Repository:
    git clone https://github.com/tarangb/cinema-tickets.git

###### Navigate to the Project Directory
    cd cinema-tickets/cinema-tickets-java

###### Compile the Code:Using Maven:
    mvn clean compile

### Testing
#### Unit Tests
The project includes comprehensive unit tests to verify the functionality of the TicketServiceImpl class. The tests cover various scenarios, including:
- Successful Purchase: Ensures that valid purchase requests are processed correctly.
- Payment Failure: Simulates failures in the payment service and verifies appropriate exception handling.
- Seat Reservation Failure: Simulates failures in seat reservation and ensures that payments are refunded.
- Idempotency: Checks that duplicate purchase requests are not processed multiple times.
- Validation Failures: Tests each validation rule to ensure that appropriate exceptions are thrown for invalid requests.

##### Running Unit Tests
###### Using Maven:
    mvn test

###### Test Coverage
The unit tests aim to achieve high coverage of all functional paths within the TicketServiceImpl class, ensuring robustness and reliability.

### Future Enhancements
#### Account Balance Validation:
- Integrate with an external service to verify that users have sufficient funds before processing payments.

#### Persistent Idempotency Tracking:
- Use a database or distributed cache to persist processed requests, ensuring idempotency across application restarts and multiple instances.

#### Logging and Monitoring:
- Integrate a logging framework (e.g., SLF4J with Logback) and monitoring tools to track system performance and diagnose issues.

#### Security Enhancements:
- Implement authentication and authorization mechanisms to secure the ticket purchasing process.

#### User Interface:
- Develop a web-based or desktop user interface to facilitate easier interactions with the ticket booking system.


