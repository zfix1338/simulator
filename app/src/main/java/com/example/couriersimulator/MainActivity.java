package com.example.couriersimulator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle; 
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint; 
import org.osmdroid.views.MapView; 
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private Button btnOrders;
    private Button btnDeliver;

    private List<String> orderList = new ArrayList<>();
    private GeoPoint currentOrderGeoPoint = null;
    private Marker currentOrderMarker = null;

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final float DELIVERY_RADIUS_METERS = 20f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Рекомендуемая инициализация OSMDroid:
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        // Или (чтобы подгрузить настройки):
        // Configuration.getInstance().load(
        //     getApplicationContext(),
        //     PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        // );

        // Обязательно тот же ресурсный файл:
        setContentView(R.layout.activity_main);

        // Находим MapView по id
        mapView = findViewById(R.id.map);
        if (mapView == null) {
            throw new RuntimeException("MapView is null. Make sure <org.osmdroid.views.MapView> exists in activity_main.xml!");
        }

        // Настройки карты
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        // Кнопки
        btnOrders = findViewById(R.id.btnOrders);
        btnDeliver = findViewById(R.id.btnDeliver);
        btnDeliver.setEnabled(false);

        // Слой отображения местоположения пользователя (синяя точка)
        myLocationOverlay = new MyLocationNewOverlay(mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Локация
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Проверяем дистанцию до принятого заказа (если он есть)
                if (currentOrderGeoPoint != null) {
                    float distance = distanceBetween(
                            location.getLatitude(), location.getLongitude(),
                            currentOrderGeoPoint.getLatitude(), currentOrderGeoPoint.getLongitude()
                    );
                    btnDeliver.setEnabled(distance <= DELIVERY_RADIUS_METERS);
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        // Проверяем / запрашиваем разрешения
        checkLocationPermission();

        // Загружаем тестовые заказы
        loadOrders();

        // Обработчики кнопок
        btnOrders.setOnClickListener(view -> showOrdersDialog());
        btnDeliver.setOnClickListener(view -> deliverOrder());
    }

    /** Создаём "заказы" для демонстрации */
    private void loadOrders() {
        orderList.clear();
        orderList.add("Доставка пиццы");
        orderList.add("Доставка документов");
        orderList.add("Продукты из магазина");
        orderList.add("Заказ из аптеки");
    }

    /** Отображаем диалог со списком заказов. При выборе — генерируем координату, ставим метку. */
    private void showOrdersDialog() {
        if (orderList.isEmpty()) {
            Toast.makeText(this, "Нет доступных заказов", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] ordersArray = orderList.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Доступные заказы")
                .setItems(ordersArray, (dialog, which) -> {
                    // При выборе заказа...
                    String selectedOrder = ordersArray[which];
                    orderList.remove(which); // Удаляем из доступных
                    acceptOrderAndSetMarker(selectedOrder);
                })
                .show();
    }

    /** Принять заказ: генерируем случайную точку недалеко от текущего местоположения */
    private void acceptOrderAndSetMarker(String orderName) {
        GeoPoint userLocation = myLocationOverlay.getMyLocation();
        if (userLocation == null) {
            Toast.makeText(this, "Неизвестно текущее положение!", Toast.LENGTH_SHORT).show();
            return;
        }
        // Случайная точка в радиусе ~1км
        double randomLatOffset = (new Random().nextDouble() - 0.5) * 0.02;
        double randomLngOffset = (new Random().nextDouble() - 0.5) * 0.02;

        double lat = userLocation.getLatitude() + randomLatOffset;
        double lng = userLocation.getLongitude() + randomLngOffset;
        currentOrderGeoPoint = new GeoPoint(lat, lng);

        // Убираем старый маркер, если был
        if (currentOrderMarker != null) {
            mapView.getOverlays().remove(currentOrderMarker);
        }
        // Ставим новый
        currentOrderMarker = new Marker(mapView);
        currentOrderMarker.setPosition(currentOrderGeoPoint);
        currentOrderMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentOrderMarker.setTitle("Заказ: " + orderName);
        mapView.getOverlays().add(currentOrderMarker);

        // Двигаем камеру
        mapView.getController().setCenter(currentOrderGeoPoint);
        mapView.getController().setZoom(15.0);
        mapView.invalidate();

        Toast.makeText(this, "Вы приняли заказ: " + orderName, Toast.LENGTH_SHORT).show();
    }

    /** Завершить заказ (кнопка "Доставить") */
    private void deliverOrder() {
        if (currentOrderGeoPoint != null) {
            Toast.makeText(this, "Заказ успешно доставлен!", Toast.LENGTH_SHORT).show();
            mapView.getOverlays().remove(currentOrderMarker);
            currentOrderMarker = null;
            currentOrderGeoPoint = null;
            btnDeliver.setEnabled(false);
            mapView.invalidate();
        }
    }

    /** Проверяем/запрашиваем разрешения локации */
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            startLocationUpdates();
        }
    }

    /** Запускаем обновления GPS/сети */
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        3000, // интервал (мс)
                        1,    // расстояние (м)
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
            if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Нет разрешений на локацию", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    /** Утилита: вычисляем расстояние в метрах */
    private float distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
}