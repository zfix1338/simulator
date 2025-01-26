package com.example.couriersimulator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint; 
import org.osmdroid.views.MapView; 
import org.osmdroid.views.CustomZoomButtonsController;
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

    private FloatingActionButton btnCenter;
    private MaterialButton btnOrders;
    private MaterialButton btnRefresh;
    private MaterialButton btnDeliver;

    // Список заказов
    private List<String> orderList = new ArrayList<>();
    // Текущие координаты заказа
    private GeoPoint currentOrderGeoPoint = null;
    private Marker currentOrderMarker = null;

    // Последняя локация пользователя (для "Центр")
    private double userLat = 0.0;
    private double userLng = 0.0;

    // Радиус спавна (+/- 0.03 ~ 3 км)
    private static final double RANDOM_OFFSET = 0.03;
    private static final float DELIVERY_RADIUS_METERS = 20f;

    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Инициализация OSMDroid
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        Configuration.getInstance().load(
            getApplicationContext(),
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );

        setContentView(R.layout.activity_main);

        // Инициализация UI
        mapView = findViewById(R.id.map);

        // Убираем встроенные кнопки +/-
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        btnCenter = findViewById(R.id.btnCenter);
        btnOrders = findViewById(R.id.btnOrders);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnDeliver = findViewById(R.id.btnDeliver);
        btnDeliver.setEnabled(false);

        // Слой "синей точки"
        myLocationOverlay = new MyLocationNewOverlay(mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Локация (GPS)
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                userLat = location.getLatitude();
                userLng = location.getLongitude();

                // Если заказ принят, проверяем дистанцию
                if (currentOrderGeoPoint != null) {
                    float dist = distanceBetween(
                            userLat, userLng,
                            currentOrderGeoPoint.getLatitude(), currentOrderGeoPoint.getLongitude()
                    );
                    btnDeliver.setEnabled(dist <= DELIVERY_RADIUS_METERS);
                }
            }
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        checkLocationPermission();

        // Восстанавливаем состояние, если нужно
        if (savedInstanceState != null) {
            restoreStateFromBundle(savedInstanceState);
        } else {
            loadInitialOrders();
        }

        if (currentOrderGeoPoint != null) {
            drawOrderMarker(currentOrderGeoPoint, "Текущий заказ");
        }

        // Обработчики
        btnCenter.setOnClickListener(v -> centerMapOnUser());
        btnOrders.setOnClickListener(v -> showOrdersBottomSheet());
        btnRefresh.setOnClickListener(v -> refreshOrders());
        btnDeliver.setOnClickListener(v -> deliverOrder());
    }

    private void loadInitialOrders() {
        orderList.clear();
        orderList.add("Доставка пиццы");
        orderList.add("Доставка документов");
        orderList.add("Продукты из магазина");
        orderList.add("Заказ из аптеки");
    }

    private void refreshOrders() {
        orderList.add("Новая посылка");
        orderList.add("Цветы на праздник");
        Toast.makeText(this, "Список заказов обновлён!", Toast.LENGTH_SHORT).show();
    }

    private void showOrdersBottomSheet() {
        if (orderList.isEmpty()) {
            Toast.makeText(this, "Нет доступных заказов", Toast.LENGTH_SHORT).show();
            return;
        }
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_orders, null);
        ListView lvOrders = sheetView.findViewById(R.id.lvOrders);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                orderList
        );
        lvOrders.setAdapter(adapter);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheetView);

        lvOrders.setOnItemClickListener((parent, view, position, id) -> {
            String selected = orderList.get(position);
            orderList.remove(position);
            adapter.notifyDataSetChanged();

            acceptOrderAndSetMarker(selected);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void acceptOrderAndSetMarker(String orderName) {
        GeoPoint userLoc = myLocationOverlay.getMyLocation();
        if (userLoc == null) {
            // fallback, если ещё нет точной локации
            if (userLat == 0 && userLng == 0) {
                Toast.makeText(this, "Неизвестно текущее положение!", Toast.LENGTH_SHORT).show();
                return;
            }
            userLoc = new GeoPoint(userLat, userLng);
        }

        // Генерация случайной точки в радиусе ~3 км
        double rLat = (new Random().nextDouble() - 0.5) * (2 * RANDOM_OFFSET);
        double rLng = (new Random().nextDouble() - 0.5) * (2 * RANDOM_OFFSET);
        double lat = userLoc.getLatitude() + rLat;
        double lng = userLoc.getLongitude() + rLng;

        currentOrderGeoPoint = new GeoPoint(lat, lng);
        drawOrderMarker(currentOrderGeoPoint, "Заказ: " + orderName);
        Toast.makeText(this, "Вы приняли заказ: " + orderName, Toast.LENGTH_SHORT).show();
    }

    private void drawOrderMarker(GeoPoint geoPoint, String title) {
        if (currentOrderMarker != null) {
            mapView.getOverlays().remove(currentOrderMarker);
        }
        currentOrderMarker = new Marker(mapView);
        currentOrderMarker.setPosition(geoPoint);
        currentOrderMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentOrderMarker.setTitle(title);
        mapView.getOverlays().add(currentOrderMarker);

        mapView.getController().setCenter(geoPoint);
        mapView.getController().setZoom(15.0);
        mapView.invalidate();
    }

    private void deliverOrder() {
        if (currentOrderGeoPoint != null) {
            Toast.makeText(this, "Заказ успешно доставлен!", Toast.LENGTH_SHORT).show();
            if (currentOrderMarker != null) {
                mapView.getOverlays().remove(currentOrderMarker);
                currentOrderMarker = null;
            }
            currentOrderGeoPoint = null;
            btnDeliver.setEnabled(false);
            mapView.invalidate();
        }
    }

    private void centerMapOnUser() {
        if (userLat == 0 && userLng == 0) {
            Toast.makeText(this, "Позиция пользователя неизвестна!", Toast.LENGTH_SHORT).show();
        } else {
            GeoPoint userGeo = new GeoPoint(userLat, userLng);
            mapView.getController().animateTo(userGeo);
            mapView.getController().setZoom(15.0);
        }
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION,
                                  Manifest.permission.ACCESS_COARSE_LOCATION },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        3000,
                        1,
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
            if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Нет разрешений на локацию", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putStringArrayList("orderList", new ArrayList<>(orderList));
        if (currentOrderGeoPoint != null) {
            outState.putBoolean("hasOrder", true);
            outState.putDouble("orderLat", currentOrderGeoPoint.getLatitude());
            outState.putDouble("orderLng", currentOrderGeoPoint.getLongitude());
        } else {
            outState.putBoolean("hasOrder", false);
        }
        outState.putDouble("userLat", userLat);
        outState.putDouble("userLng", userLng);
    }

    private void restoreStateFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey("orderList")) {
            List<String> restored = savedInstanceState.getStringArrayList("orderList");
            if (restored != null) {
                orderList.clear();
                orderList.addAll(restored);
            }
        }
        boolean hasOrder = savedInstanceState.getBoolean("hasOrder", false);
        if (hasOrder) {
            double lat = savedInstanceState.getDouble("orderLat", 0.0);
            double lng = savedInstanceState.getDouble("orderLng", 0.0);
            currentOrderGeoPoint = new GeoPoint(lat, lng);
        } else {
            currentOrderGeoPoint = null;
        }
        userLat = savedInstanceState.getDouble("userLat", 0.0);
        userLng = savedInstanceState.getDouble("userLng", 0.0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    private float distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
}