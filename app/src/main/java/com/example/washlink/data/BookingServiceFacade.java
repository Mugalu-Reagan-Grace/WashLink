package com.example.washlink.data;

/**
 * Access to the live Firestore booking service, with a legacy in-memory stub
 * retained for development-only screens that explicitly opt into it.
 */
public class BookingServiceFacade {

    private static boolean useStub = false;

    public static void setUseStub(boolean stub) {
        useStub = stub;
    }

    public static BookingService getBookingService() {
        return BookingService.getInstance();
    }

    public static BookingServiceStub getBookingServiceStub() {
        return BookingServiceStub.getInstance();
    }

    public static boolean isUsingStub() {
        return useStub;
    }
}