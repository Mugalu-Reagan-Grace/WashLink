package com.example.washlink.models;

/**
 * A single order, from the moment a customer books it through delivery.
 * Stored in Firestore at: bookings/{bookingId}
 *
 * This is the record that ties customer and provider together - both the
 * customer's Order Tracking/History screens and the provider's Booking
 * Requests/Dashboard/Update Status screens read and write the SAME documents
 * in this collection, filtered by customerId or providerId respectively.
 */
public class Booking {

    public static final String SERVICE_TYPE_PICKUP = "pickup_delivery";
    public static final String SERVICE_TYPE_DROPOFF = "dropoff";

    private String id;
    private String customerId;
    private String customerName;
    private String providerId;
    private String providerName;
    private String serviceType;       // SERVICE_TYPE_PICKUP or SERVICE_TYPE_DROPOFF
    private String serviceName;       // e.g. "Wash & Fold"
    private String status;            // stores OrderStatus.name(), e.g. "WASHING"
    private String address;
    private String scheduledDateTime; // display string, e.g. "Friday, March 15, 2024 - 10:00 AM"
    private int itemCount;
    private String estimatedWeight;   // e.g. "10-15 lbs"
    private String specialInstructions;
    private double subtotal;
    private double tax;
    private double total;
    private String paymentMethod;     // e.g. "Visa •••• 4242"
    private String paymentStatus;     // PENDING, PAID, or FAILED
    private double pickupFee;
    private double deliveryFee;
    private double serviceFee;
    private long createdAt;
    private long updatedAt;

    public Booking() {
    }

    public Booking(String customerId, String customerName, String providerId, String providerName,
                    String serviceType, String serviceName, String address) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.providerId = providerId;
        this.providerName = providerName;
        this.serviceType = serviceType;
        this.serviceName = serviceName;
        this.address = address;
        this.status = OrderStatus.BOOKED.name();
        this.paymentStatus = "PENDING";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getScheduledDateTime() {
        return scheduledDateTime;
    }

    public void setScheduledDateTime(String scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public String getEstimatedWeight() {
        return estimatedWeight;
    }

    public void setEstimatedWeight(String estimatedWeight) {
        this.estimatedWeight = estimatedWeight;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }

    public double getTax() {
        return tax;
    }

    public void setTax(double tax) {
        this.tax = tax;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public double getPickupFee() {
        return pickupFee;
    }

    public void setPickupFee(double pickupFee) {
        this.pickupFee = pickupFee;
    }

    public double getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(double deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public double getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(double serviceFee) {
        this.serviceFee = serviceFee;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}