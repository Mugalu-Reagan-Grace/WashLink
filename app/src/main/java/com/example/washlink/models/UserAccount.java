package com.example.washlink.models;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class UserAccount {
    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_PROVIDER = "provider";
    public static final String ROLE_ADMIN = "admin";

    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String createdAt;
    private String address;
    private String photoUri;
    private List<String> savedAddresses = new ArrayList<>();
    private boolean isSuspended;

    public UserAccount() {
        this.createdAt = currentTimestamp();
    }

    public UserAccount(String uid, String name, String email, String phone, String role) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.createdAt = currentTimestamp();
    }

    private static String currentTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhotoUri() {
        return photoUri;
    }

    public void setPhotoUri(String photoUri) {
        this.photoUri = photoUri;
    }

    public List<String> getSavedAddresses() {
        return savedAddresses;
    }

    public void setSavedAddresses(List<String> savedAddresses) {
        this.savedAddresses = savedAddresses == null
                ? new ArrayList<>() : new ArrayList<>(savedAddresses);
    }

    public boolean getIsSuspended() {
        return isSuspended;
    }

    public void setIsSuspended(boolean suspended) {
        isSuspended = suspended;
    }
}
