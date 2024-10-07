package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketServiceImplTest {

    private TicketPaymentService paymentService;
    private SeatReservationService seatReservationService;
    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        paymentService = mock(TicketPaymentService.class);
        seatReservationService = mock(SeatReservationService.class);
        ticketService = new TicketServiceImpl(paymentService, seatReservationService);
    }

    @Test
    void testSuccessfulPurchase() {
        TicketTypeRequest adult = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
        TicketTypeRequest child = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1);
        TicketTypeRequest infant = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1);

        doNothing().when(paymentService).makePayment(1001L, 65);
        doNothing().when(seatReservationService).reserveSeat(1001L, 3);

        assertDoesNotThrow(() -> {
            ticketService.purchaseTickets(1001L, adult, child, infant);
        });

        // Verify payment and reservation were called
        verify(paymentService, times(1)).makePayment(1001L, 65);
        verify(seatReservationService, times(1)).reserveSeat(1001L, 3);
    }

    @Test
    void testPaymentFailure() {
        TicketTypeRequest adult = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
        TicketTypeRequest child = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1);

        doThrow(new RuntimeException("Payment gateway error")).when(paymentService).makePayment(1002L, 65);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1002L, adult, child);
        });

        assertEquals("An unexpected error occurred: Payment failed: Payment gateway error", exception.getMessage());

        // Verify payment was attempted and reservation was not
        verify(paymentService, times(1)).makePayment(1002L, 65);
        verify(seatReservationService, never()).reserveSeat(anyLong(), anyInt());
    }

    @Test
    void testSeatReservationFailureWithRefund() {
        TicketTypeRequest adult = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

        doNothing().when(paymentService).makePayment(1003L, 25);
        //Note: I very well have noted that the seats will always be booked if the api is called, but just to simulate say crashing of my implemented app between Payment and seatReservation I am writing this test to throw runtime exception.
        doThrow(new RuntimeException("No seats available")).when(seatReservationService).reserveSeat(1003L, 1);
        doNothing().when(paymentService).makePayment(1003L, -25);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1003L, adult);
        });

        assertEquals("An unexpected error occurred: Seat reservation failed: No seats available", exception.getMessage());

        // Verify payment was made, reservation was attempted, and refund was made
        verify(paymentService, times(1)).makePayment(1003L, 25);
        verify(seatReservationService, times(1)).reserveSeat(1003L, 1);
        verify(paymentService, times(1)).makePayment(1003L, -25);
    }

    @Test
    void testSeatReservationFailureWithRefundFailure() {
        TicketTypeRequest adult = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

        doNothing().when(paymentService).makePayment(1004L, 25);
        doThrow(new RuntimeException("No seats available")).when(seatReservationService).reserveSeat(1004L, 1);
        doThrow(new RuntimeException("Refund service unavailable")).when(paymentService).makePayment(1004L, -25);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1004L, adult);
        });

        assertEquals("An unexpected error occurred: Refund failed for accountId: 1004. Reason: Refund service unavailable. Contact Help center for manual refund.", exception.getMessage());

        // Verify payment was made, reservation was attempted, and refund was attempted
        verify(paymentService, times(1)).makePayment(1004L, 25);
        verify(seatReservationService, times(1)).reserveSeat(1004L, 1);
        verify(paymentService, times(1)).makePayment(1004L, -25);
    }

    @Test
    void testIdempotentPurchase() {
        TicketTypeRequest adult = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

        doNothing().when(paymentService).makePayment(1005L, 25);
        doNothing().when(seatReservationService).reserveSeat(1005L, 1);

        // First purchase attempt
        assertDoesNotThrow(() -> {
            ticketService.purchaseTickets(1005L, adult);
        });

        // Second purchase attempt (duplicate)
        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1005L, adult);
        });

        assertEquals("Request has already been processed by accountId: 1005", exception.getMessage());

        // Verify payment and reservation were called only once
        verify(paymentService, times(1)).makePayment(1005L, 25);
        verify(seatReservationService, times(1)).reserveSeat(1005L, 1);
    }

    @Test
    void testInvalidAccountId() {
        TicketTypeRequest adult = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(0L, adult);
        });

        assertEquals("Invalid account ID: 0", exception.getMessage());

        // Verify payment and reservation were not called
        verify(paymentService, never()).makePayment(anyLong(), anyInt());
        verify(seatReservationService, never()).reserveSeat(anyLong(), anyInt());
    }

    @Test
    void testExceedingMaxTickets() {
        TicketTypeRequest adult = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 26);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1006L, adult);
        });

        assertEquals("Cannot purchase more than 25 tickets at a time.", exception.getMessage());

        // Verify payment and reservation were not called
        verify(paymentService, never()).makePayment(anyLong(), anyInt());
        verify(seatReservationService, never()).reserveSeat(anyLong(), anyInt());
    }

    @Test
    void testChildWithoutAdult() {
        TicketTypeRequest child = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1007L, child);
        });

        assertEquals("Must purchase at least one adult ticket when buying child or infant tickets.", exception.getMessage());

        // Verify payment and reservation were not called
        verify(paymentService, never()).makePayment(anyLong(), anyInt());
        verify(seatReservationService, never()).reserveSeat(anyLong(), anyInt());
    }

    @Test
    void testInfantExceedingAdults() {
        TicketTypeRequest adult = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest infant = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 2);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1008L, adult, infant);
        });

        assertEquals("Number of infant tickets cannot exceed number of adult tickets.", exception.getMessage());

        // Verify payment and reservation were not called
        verify(paymentService, never()).makePayment(anyLong(), anyInt());
        verify(seatReservationService, never()).reserveSeat(anyLong(), anyInt());
    }
}
