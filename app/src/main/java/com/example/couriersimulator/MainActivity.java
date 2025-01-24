package com.example.couriersimulator;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle; 
import android.widget.Button;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint; 
import org.osmdroid.views.MapView; 
import org.osmdroid.views.overlay.Marker;

import java.util.Random;

public class MapActivity extends FragmentActivity {

    private MapView mapView;
    private Button btnDeliver;

    private LocationManager locationManager;
    private LocationListener locationListener;

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private final float DELIVERY_RADIUS_METERS = 20f;

    // Цель доставки (генерируем случайно)
    private GeoPoint deliveryGeoPoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Инициализация OSMDroid
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        mapView = findViewById(R.id.map);
        btnDeliver = findViewById(R.id.btnDeliver);

        // Настраиваем карту
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Сгенерируем случайную точку (пускай в районе Москвы, для примера)
        double baseLat = 55.751244; // Москва
        double baseLng = 37.618423; // Москва

        // +/- ~ 0.01 градуса ~ 1 км
        double randomLatOffset = (new Random().nextDouble() - 0.5) * 0.02;
        double randomLngOffset = (new Random().nextDouble() - 0.5) * 0.02;

        double targetLat = baseLat + randomLatOffset;
        double targetLng = baseLng + randomLngOffset;

        deliveryGeoPoint = new GeoPoint(targetLat, targetLng);

        // Добавим метку (Marker) на карту
        Marker deliveryMarker = new Marker(mapView);
        deliveryMarker.setPosition(deliveryGeoPoint);
        deliveryMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        deliveryMarker.setTitle("Точка доставки");
        mapView.getOverlays().add(deliveryMarker);

        // Переместим камеру на точку доставки
        mapView.getController().setZoom(14.0);
        mapView.getController().setCenter(deliveryGeoPoint);

        // Инициализируем LocationManager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Слушатель обновлений локации
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Проверяем, находимся ли мы в радиусе 20 м
                float[] results = new float[1];
                Location.distanceBetween(
                        location.getLatitude(),
                        location.getLongitude(),
                        deliveryGeoPoint.getLatitude(),
                        deliveryGeoPoint.getLongitude(),
                        results
                );
                float distance = results[0];

                // Если ближе 20 метров, активируем кнопку
                btnDeliver.setEnabled(distance <= DELIVERY_RADIUS_METERS);

                // Можете при желании плавно перемещать камеру к текущей позиции,
                // но тогда нужен Marker игрока или пользовательский overlay.
                // Здесь упрощённо не перемещаем карту, только при инициализации.
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override
            public void onProviderEnabled(@NonNull String provider) { }
            @Override
            public void onProviderDisabled(@NonNull String provider) { }
        };

        // Запросим разрешения, если не даны
        checkLocationPermission();

        // Обработчик нажатия "Доставить"
        btnDeliver.setOnClickListener(v -> {
            Toast.makeText(MapActivity.this, "Заказ доставлен!", Toast.LENGTH_SHORT).show();
            // Здесь можно завершить активити или обновить информацию на сервере
            finish();
        });
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            // Разрешения уже есть, можем запрашивать обновления
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Можно использовать разные провайдеры: GPS, NETWORK
            // Для упрощения используем NETWORK_PROVIDER и/или GPS_PROVIDER
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        3000, // Минимальное время (мс) между обновлениями
                        1,    // Минимальное расстояние (м) между обновлениями
                        locationListener
                );
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        3000,
                        1,
                        locationListener
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Необходимо разрешение на локацию", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Останавливаем слушатель локации, чтобы не утекали ресурсы
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }
}