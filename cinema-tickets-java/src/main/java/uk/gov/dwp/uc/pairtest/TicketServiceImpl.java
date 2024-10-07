package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    private static final int INFANT_PRICE = 0;
    private static final int CHILD_PRICE = 15;
    private static final int ADULT_PRICE = 25;
    private static final int MAX_TICKETS_PER_PURCHASE = 25;

    private final TicketPaymentService paymentService;
    private final SeatReservationService seatReservationService;

    // To track processed requests for idempotency
    private final Set<RequestKey> processedRequests = ConcurrentHashMap.newKeySet();

    /**
     * Constructor to set the TicketPaymentService and SeatReservationService.
     *
     * @param paymentService        The payment service to process payments.
     * @param seatReservationService The seat reservation service to reserve seats.
     */
    public TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService seatReservationService) {
        this.paymentService = Objects.requireNonNull(paymentService, "PaymentService cannot be null.");
        this.seatReservationService = Objects.requireNonNull(seatReservationService, "SeatReservationService cannot be null.");
    }

    /**
     * Synchronized method to ensure thread safety and atomicity.
     * This method processes the ticket purchase, ensuring that payment and seat reservation
     * occur as separate atomic operations. It also ensures idempotency by tracking processed requests.
     *
     * @param accountId          The ID of the account purchasing tickets.
     * @param ticketTypeRequests The types and quantities of tickets being purchased.
     * @throws InvalidPurchaseException If the purchase request is invalid or has already been processed.
     */
    @Override
    public synchronized void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        // Validate account ID
        if (!isValidAccount(accountId)) {
            throw new InvalidPurchaseException("Invalid account ID: " + accountId);
        }

        // Validate ticket type requests with rule-based validations
        areValidTicketRequests(ticketTypeRequests);

        // Create a key to track idempotency
        RequestKey requestKey = new RequestKey(accountId, Arrays.asList(ticketTypeRequests));

        if (processedRequests.contains(requestKey)) {
            // Idempotent behavior: do not process the same request again
            throw new InvalidPurchaseException("Request has already been processed by accountId: " + accountId);
        }

        // Calculate total amount and number of seats
        int totalAmount = calculateTotalAmount(ticketTypeRequests);
        int seatsToReserve = calculateSeatsToReserve(ticketTypeRequests);

        // Perform payment and seat reservation separately with distinct error handling
        try {
            processPayment(accountId, totalAmount);

            // Seat Reservation
            processSeatReservation(accountId, seatsToReserve, totalAmount);

            // Mark request as processed
            processedRequests.add(requestKey);

            System.out.println("Purchase successful. Amount paid: £" + totalAmount + ", Seats reserved: " + seatsToReserve);

        }catch (Exception e) {
            // Catch any unforeseen exceptions
            throw new InvalidPurchaseException("An unexpected error occurred: " + e.getMessage());
        }
    }

    private void processSeatReservation(Long accountId, int seatsToReserve, int totalAmount) {
        try {
            seatReservationService.reserveSeat(accountId, seatsToReserve);
        } catch (Exception e) {

            // Attempt to refund the payment since seat reservation failed
            processRefund(accountId, totalAmount);

            throw new InvalidPurchaseException("Seat reservation failed: " + e.getMessage());
        }
    }

    private void processRefund(Long accountId, int totalAmount) {
        // Attempt to refund the payment since seat reservation failed
        try {
            paymentService.makePayment(accountId, -totalAmount);
        } catch (Exception refundException) {
            // Log the refund failure (In real-world scenarios, use a logging framework)
            throw new InvalidPurchaseException("Refund failed for accountId: " + accountId + ". Reason: " + refundException.getMessage() + ". Contact Help center for manual refund.");
        }
    }

    private void processPayment(Long accountId, int totalAmount) {
        // Payment
        try {
            paymentService.makePayment(accountId, totalAmount);
        } catch (Exception e) {
            throw new InvalidPurchaseException("Payment failed: " + e.getMessage());
        }
    }

    /**
     * Validates the account ID.
     *
     * @param accountId The account ID to validate.
     * @return True if the account ID is valid, false otherwise.
     */
    private boolean isValidAccount(Long accountId) {
        return accountId != null && accountId > 0;
    }

    /**
     * Validates the ticket type requests based on business rules.
     * Throws InvalidPurchaseException with specific messages for each failed rule.
     *
     * @param requests The array of TicketTypeRequest objects.
     * @throws InvalidPurchaseException If any validation rule fails.
     */
    private void areValidTicketRequests(TicketTypeRequest[] requests) throws InvalidPurchaseException {
        if (requests == null || requests.length == 0) {
            throw new InvalidPurchaseException("No tickets requested.");
        }

        int totalTickets = Arrays.stream(requests)
                .mapToInt(TicketTypeRequest::getNoOfTickets)
                .sum();

        if (totalTickets > MAX_TICKETS_PER_PURCHASE) {
            throw new InvalidPurchaseException("Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets at a time.");
        }

        long adultTickets = Arrays.stream(requests)
                .filter(r -> r.getTicketType() == TicketTypeRequest.Type.ADULT)
                .mapToInt(TicketTypeRequest::getNoOfTickets)
                .sum();

        long childTickets = Arrays.stream(requests)
                .filter(r -> r.getTicketType() == TicketTypeRequest.Type.CHILD)
                .mapToInt(TicketTypeRequest::getNoOfTickets)
                .sum();

        long infantTickets = Arrays.stream(requests)
                .filter(r -> r.getTicketType() == TicketTypeRequest.Type.INFANT)
                .mapToInt(TicketTypeRequest::getNoOfTickets)
                .sum();

        if ((childTickets > 0 || infantTickets > 0) && adultTickets == 0) {
            throw new InvalidPurchaseException("Must purchase at least one adult ticket when buying child or infant tickets.");
        }

        // Infants cannot exceed number of adults (assuming one infant per adult)
        if (infantTickets > adultTickets) {
            throw new InvalidPurchaseException("Number of infant tickets cannot exceed number of adult tickets.");
        }
    }

    /**
     * Calculates the total amount to be paid based on ticket types and quantities.
     *
     * @param requests The array of TicketTypeRequest objects.
     * @return The total amount in pounds.
     */
    private int calculateTotalAmount(TicketTypeRequest[] requests) {
        return Arrays.stream(requests)
                .mapToInt(r -> {
                    switch (r.getTicketType()) {
                        case INFANT:
                            return 0;
                        case CHILD:
                            return CHILD_PRICE * r.getNoOfTickets();
                        case ADULT:
                            return ADULT_PRICE * r.getNoOfTickets();
                        default:
                            throw new IllegalArgumentException("Unknown ticket type: " + r.getTicketType());
                    }
                })
                .sum();
    }

    /**
     * Calculates the number of seats to reserve based on ticket types and quantities.
     * Infants do not require seats.
     *
     * @param requests The array of TicketTypeRequest objects.
     * @return The number of seats to reserve.
     */
    private int calculateSeatsToReserve(TicketTypeRequest[] requests) {
        // Only children and adults require seats
        return Arrays.stream(requests)
                .filter(r -> r.getTicketType() == TicketTypeRequest.Type.CHILD || r.getTicketType() == TicketTypeRequest.Type.ADULT)
                .mapToInt(TicketTypeRequest::getNoOfTickets)
                .sum();
    }

    /**
     * Inner class to represent a unique request key for idempotency.
     * It combines the accountId and the list of TicketTypeRequests.
     */
    private static class RequestKey {
        private final Long accountId;
        private final List<TicketTypeRequest> requests;

        public RequestKey(Long accountId, List<TicketTypeRequest> requests) {
            this.accountId = accountId;
            // Sort requests to ensure consistent ordering
            this.requests = new ArrayList<>(requests);
            this.requests.sort(Comparator.comparing(TicketTypeRequest::getTicketType));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;  // Check if both references point to the same object
            if (!(o instanceof RequestKey)) return false;  // Check if the object is of type RequestKey
            RequestKey that = (RequestKey) o;  // Typecast the object to RequestKey
            return Objects.equals(accountId, that.accountId) &&
                    Objects.equals(requests, that.requests);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, requests);
        }
    }
}
