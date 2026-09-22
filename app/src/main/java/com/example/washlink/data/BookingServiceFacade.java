package com.example.washlink.data;

/**
 * Facade to choose between real Firestore BookingService and in-memory stub.
 * Default is to use the stub so the app runs without Firebase configured.
 * Call BookingServiceFacade.setUseStub(false) after configuring Firebase.
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