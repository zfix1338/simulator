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
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Демонстрация:
 * - OSMDroid карта
 * - BottomSheet список заказов
 * - Material Design кнопки
 * - Сохранение состояния при перевороте
 */
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

    // Радиус спавна (&plusmn;0.03 ~ 3 км)
    private static final double RANDOM_OFFSET = 0.03;
    private static final float DELIVERY_RADIUS_METERS = 20f;

    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // OSMDroid настройка (UserAgent)
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        Configuration.getInstance().load(
            getApplicationContext(),
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );

        setContentView(R.layout.activity_main);

        // Инициализация UI
        mapView = findViewById(R.id.map);
        btnCenter = findViewById(R.id.btnCenter);
        btnOrders = findViewById(R.id.btnOrders);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnDeliver = findViewById(R.id.btnDeliver);

        // Настраиваем карту
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        // "Доставить" по умолчанию неактивна
        btnDeliver.setEnabled(false);

        // Слой синей точки
        myLocationOverlay = new MyLocationNewOverlay(mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Настраиваем GPS
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Сохраняем последние координаты
                userLat = location.getLatitude();
                userLng = location.getLongitude();

                // Если у нас есть заказ, проверяем расстояние
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

        // Если восстанавливаем состояние
        if (savedInstanceState != null) {
            restoreStateFromBundle(savedInstanceState);
        } else {
            // Если первый запуск - инициализируем список
            loadInitialOrders();
        }

        // Если есть заказ, перерисуем маркер
        if (currentOrderGeoPoint != null) {
            drawOrderMarker(currentOrderGeoPoint, "Текущий заказ");
        }

        // Кнопка "Центр" (FAB)
        btnCenter.setOnClickListener(v -> centerMapOnUser());

        // Кнопка "Заказы"
        btnOrders.setOnClickListener(v -> showOrdersBottomSheet());

        // Кнопка "Обновить"
        btnRefresh.setOnClickListener(v -> refreshOrders());

        // Кнопка "Доставить"
        btnDeliver.setOnClickListener(v -> deliverOrder());
    }

    /** Начальный список заказов */
    private void loadInitialOrders() {
        orderList.clear();
        orderList.add("Доставка пиццы");
        orderList.add("Доставка документов");
        orderList.add("Продукты из магазина");
        orderList.add("Заказ из аптеки");
    }

    /** Добавим новые заказы (симуляция "обновления") */
    private void refreshOrders() {
        orderList.add("Новая посылка");
        orderList.add("Цветы на праздник");
        Toast.makeText(this, "Список заказов обновлён!", Toast.LENGTH_SHORT).show();
    }

    /** Показываем BottomSheetDialog со списком заказов */
    private void showOrdersBottomSheet() {
        if (orderList.isEmpty()) {
            Toast.makeText(this, "Нет доступных заказов", Toast.LENGTH_SHORT).show();
            return;
        }

        // Инфлейтим макет bottom_sheet_orders.xml
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_orders, null);
        ListView lvOrders = sheetView.findViewById(R.id.lvOrders);

        // Простейший ArrayAdapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                orderList
        );
        lvOrders.setAdapter(adapter);

        // BottomSheetDialog
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheetView);

        // Обработка выбора
        lvOrders.setOnItemClickListener((parent, view, position, id) -> {
            String selectedOrder = orderList.get(position);
            // Удаляем заказ из списка, принимаем
            orderList.remove(position);
            adapter.notifyDataSetChanged();

            acceptOrderAndSetMarker(selectedOrder);
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Принять заказ: генерируем случайные координаты рядом с userLocation */
    private void acceptOrderAndSetMarker(String orderName) {
        GeoPoint userLoc = myLocationOverlay.getMyLocation();
        if (userLoc == null) {
            // fallback, если myLocationOverlay ещё не знает позицию
            if (userLat == 0 && userLng == 0) {
                Toast.makeText(this, "Неизвестно текущее положение!", Toast.LENGTH_SHORT).show();
                return;
            }
            userLoc = new GeoPoint(userLat, userLng);
        }

        // Генерация +/- RANDOM_OFFSET (по умолчанию ~3км)
        double randomLatOffset = (new Random().nextDouble() - 0.5) * (2 * RANDOM_OFFSET);
        double randomLngOffset = (new Random().nextDouble() - 0.5) * (2 * RANDOM_OFFSET);

        double lat = userLoc.getLatitude() + randomLatOffset;
        double lng = userLoc.getLongitude() + randomLngOffset;
        currentOrderGeoPoint = new GeoPoint(lat, lng);

        drawOrderMarker(currentOrderGeoPoint, "Заказ: " + orderName);
        Toast.makeText(this, "Вы приняли заказ: " + orderName, Toast.LENGTH_SHORT).show();
    }

    /** Рисуем/обновляем маркер заказа на карте */
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

    /** Доставляем заказ (кнопка "Доставить") */
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

    /** Центрируем карту на (userLat, userLng) - FAB btnCenter */
    private void centerMapOnUser() {
        if (userLat == 0 && userLng == 0) {
            Toast.makeText(this, "Позиция пользователя неизвестна!", Toast.LENGTH_SHORT).show();
        } else {
            GeoPoint userGeo = new GeoPoint(userLat, userLng);
            mapView.getController().animateTo(userGeo);
            mapView.getController().setZoom(15.0);
        }
    }

    /** Запрашиваем разрешения локации */
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            startLocationUpdates();
        }
    }

    /** Запуск GPS/сети */
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

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
            if (grantResults.length > 0 
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Нет разрешений на локацию", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Сохраняем состояние при повороте экрана */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Сохраняем список заказов
        outState.putStringArrayList("orderList", new ArrayList<>(orderList));
        // Сохраняем заказ (если есть)
        if (currentOrderGeoPoint != null) {
            outState.putBoolean("hasOrder", true);
            outState.putDouble("orderLat", currentOrderGeoPoint.getLatitude());
            outState.putDouble("orderLng", currentOrderGeoPoint.getLongitude());
        } else {
            outState.putBoolean("hasOrder", false);
        }
        // Координаты пользователя
        outState.putDouble("userLat", userLat);
        outState.putDouble("userLng", userLng);
    }

    /** Восстанавливаем */
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

    /** Утилита для расчёта дистанции */
    private float distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
}