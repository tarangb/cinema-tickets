package uk.gov.dwp.uc.pairtest.domain;

/*
 * Immutable Object
 */

import java.util.Objects;

public final class TicketTypeRequest {

    private final int noOfTickets;
    private final Type type;

    public TicketTypeRequest(Type ticketType, int noOfTickets) {
        if (noOfTickets < 0) {
            throw new IllegalArgumentException("Ticket count cannot be negative.");
        }
        this.type = Objects.requireNonNull(ticketType, "Ticket type cannot be null.");
        this.noOfTickets = noOfTickets;
    }

    public Type getTicketType() {
        return type;
    }

    public int getNoOfTickets() {
        return noOfTickets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TicketTypeRequest that = (TicketTypeRequest) o;

        if (noOfTickets != that.noOfTickets) return false;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + noOfTickets;
        return result;
    }

    @Override
    public String toString() {
        return "TicketTypeRequest{" +
                "type=" + type +
                ", noOfTickets=" + noOfTickets +
                '}';
    }

    public enum Type {
        ADULT, CHILD , INFANT
    }
}

