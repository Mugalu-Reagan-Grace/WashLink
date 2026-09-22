package com.example.washlink;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.washlink.models.Provider;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NearbyLaundryMapActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final int LOCATION_REQUEST = 410;
    private GoogleMap map;
    private FusedLocationProviderClient locationClient;
    private LatLng userLocation;
    private int radiusKm = 5;
    private SeekBar radiusBar;
    private TextView radiusLabel;
    private final List<Provider> providers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_nearby_laundry_map);
        radiusBar = findViewById(R.id.map_radius);
        radiusLabel = findViewById(R.id.tv_map_radius);
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        updateRadiusLabel();

        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                radiusKm = progress + 1;
                updateRadiusLabel();
                redrawMarkers();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        findViewById(R.id.btn_my_location).setOnClickListener(v -> requestLocation());

        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.laundry_map);
        if (fragment != null) fragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(true);
        requestLocation();
        loadProviders();
    }

    private void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        map.setMyLocationEnabled(true);
        locationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        setUserLocation(new LatLng(location.getLatitude(), location.getLongitude()));
                    } else {
                        Toast.makeText(this, R.string.map_location_unavailable, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setUserLocation(LatLng location) {
        userLocation = location;
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 13f));
        redrawMarkers();
    }

    private void loadProviders() {
        FirebaseFirestore.getInstance().collection("providers").get()
                .addOnSuccessListener(snapshot -> {
                    providers.clear();
                    snapshot.getDocuments().forEach(document -> {
                        Provider provider = document.toObject(Provider.class);
                        if (provider != null) providers.add(provider);
                    });
                    redrawMarkers();
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Nearby businesses could not be loaded", Toast.LENGTH_SHORT).show());
    }

    private void redrawMarkers() {
        if (map == null || userLocation == null) return;
        map.clear();
        map.addCircle(new CircleOptions().center(userLocation)
                .radius(radiusKm * 1000d)
                .strokeColor(0x663F51B5)
                .fillColor(0x143F51B5));

        for (Provider provider : providers) {
            if (provider.getLatitude() == 0 && provider.getLongitude() == 0) continue;
            float[] distance = new float[1];
            Location.distanceBetween(userLocation.latitude, userLocation.longitude,
                    provider.getLatitude(), provider.getLongitude(), distance);
            if (distance[0] <= radiusKm * 1000d) {
                String name = provider.getBusinessName() == null
                        ? "Laundry business" : provider.getBusinessName();
                map.addMarker(new MarkerOptions()
                        .position(new LatLng(provider.getLatitude(), provider.getLongitude()))
                        .title(name)
                        .snippet(String.format(Locale.US, "%.1f km away", distance[0] / 1000)));
            }
        }
    }

    private void updateRadiusLabel() {
        if (radiusLabel != null) {
            radiusLabel.setText(getString(R.string.map_radius_label, radiusKm));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }
        if (requestCode == LOCATION_REQUEST && granted) {
            requestLocation();
        } else {
            Toast.makeText(this, R.string.map_location_permission, Toast.LENGTH_LONG).show();
        }
    }
}
