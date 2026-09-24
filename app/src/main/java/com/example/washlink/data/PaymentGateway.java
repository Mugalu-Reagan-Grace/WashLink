package com.example.washlink.data;

public final class PaymentGateway {
    private PaymentGateway() {}

    public static PaymentResult process(String method, int totalUgx) {
        if (method == null) {
            return new PaymentResult(false, "PENDING", "No payment method selected", totalUgx);
        }

        switch (method.toLowerCase()) {
            case "cash":
                return new PaymentResult(false, "PENDING", "Cash payment is pending confirmation.", totalUgx);
            case "airtel":
            case "mtn":
            case "google pay":
            case "visa":
            case "mastercard":
                return new PaymentResult(false, "PENDING",
                        "Gateway integration is pending provider setup; payment remains pending until backend is connected.",
                        totalUgx);
            default:
                return new PaymentResult(false, "PENDING", "Payment method unrecognized.", totalUgx);
        }
    }

    public static final class PaymentResult {
        private final boolean approved;
        private final String status;
        private final String message;
        private final int amountUgx;

        public PaymentResult(boolean approved, String status, String message, int amountUgx) {
            this.approved = approved;
            this.status = status;
            this.message = message;
            this.amountUgx = amountUgx;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public int getAmountUgx() {
            return amountUgx;
        }
    }
}
