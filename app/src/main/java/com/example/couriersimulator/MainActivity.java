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
import android.widget.LinearLayout;
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

/**
 * Основная Activity: показывает карту, кнопки "Заказы", "Обновить", "Доставить", "Центр".
 * Сохраняет состояние при повороте экрана (не сбрасывает заказ и список).
 */
public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;
    private LocationListener locationListener;

    // Кнопки
    private Button btnCenter;     // Центрировать карту на GPS
    private Button btnOrders;     // Открыть список заказов
    private Button btnRefresh;    // Обновить список заказов
    private Button btnDeliver;    // Доставить

    // Список заказов и текущий заказ
    private List<String> orderList = new ArrayList<>();
    private GeoPoint currentOrderGeoPoint = null;
    private Marker currentOrderMarker = null;

    // Запоминаем последние координаты пользователя (обновляются в onLocationChanged),
    // чтобы кнопка "Центр" знала, куда двигать камеру.
    private double userLat = 0.0;
    private double userLng = 0.0;

    private static final int PERMISSION_REQUEST_CODE = 1001;
    // Увеличим радиус до ~3 км (раньше было 0.01-0.02 ~ 1 км)
    private static final double RANDOM_OFFSET = 0.03; 
    private static final float DELIVERY_RADIUS_METERS = 20f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Инициализация OSMDroid
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        Configuration.getInstance().load(
            getApplicationContext(),
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );

        // Устанавливаем макет
        setContentView(R.layout.activity_main);

        // Ищем элементы
        mapView = findViewById(R.id.map);
        btnCenter = findViewById(R.id.btnCenter);
        btnOrders = findViewById(R.id.btnOrders);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnDeliver = findViewById(R.id.btnDeliver);

        // Настраиваем карту
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        // Кнопка "Доставить" неактивна по умолчанию
        btnDeliver.setEnabled(false);

        // Слой "синей точки" (местоположение пользователя)
        myLocationOverlay = new MyLocationNewOverlay(mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Настраиваем LocationManager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Сохраняем последние координаты пользователя (для кнопки "Центр")
                userLat = location.getLatitude();
                userLng = location.getLongitude();

                // Если есть текущий заказ, проверяем расстояние
                if (currentOrderGeoPoint != null) {
                    float distance = distanceBetween(
                            userLat, userLng,
                            currentOrderGeoPoint.getLatitude(), currentOrderGeoPoint.getLongitude()
                    );
                    // Если ближе 20м, можно доставлять
                    btnDeliver.setEnabled(distance <= DELIVERY_RADIUS_METERS);
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        // Запрашиваем разрешения
        checkLocationPermission();

        // Если восстанавливаем из savedInstanceState
        if (savedInstanceState != null) {
            restoreStateFromBundle(savedInstanceState);
        } else {
            // Иначе грузим новый список заказов
            loadOrdersInitially();
        }

        // Обновим карту, если был "currentOrderGeoPoint"
        if (currentOrderGeoPoint != null) {
            // Перерисовать маркер
            drawOrderMarker(currentOrderGeoPoint, "Текущий заказ");
        }

        // Обработчики кликов
        btnCenter.setOnClickListener(v -> centerMapOnUser());
        btnOrders.setOnClickListener(v -> showOrdersDialog());
        btnRefresh.setOnClickListener(v -> refreshOrders());
        btnDeliver.setOnClickListener(v -> deliverOrder());
    }

    /**
     * Загружаем начальный список заказов (только при первом запуске).
     */
    private void loadOrdersInitially() {
        orderList.clear();
        orderList.add("Доставка пиццы");
        orderList.add("Доставка документов");
        orderList.add("Продукты из магазина");
        orderList.add("Заказ из аптеки");
    }

    /**
     * Обновляем / добавляем новые заказы.
     * В реальном приложении можно подгружать с сервера.
     */
    private void refreshOrders() {
        // Пример: добавим 2-3 новых заказов
        orderList.add("Новая посылка");
        orderList.add("Цветы на 14 февраля");
        Toast.makeText(this, "Список заказов обновлён!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Показать диалог со списком заказов. В диалоге добавим кнопку "Отмена".
     */
    private void showOrdersDialog() {
        if (orderList.isEmpty()) {
            Toast.makeText(this, "Нет доступных заказов", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] ordersArray = orderList.toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Доступные заказы");
        builder.setItems(ordersArray, (dialog, which) -> {
            String selectedOrder = ordersArray[which];
            // Удаляем заказ из списка, чтобы нельзя было выбрать повторно
            orderList.remove(which);
            acceptOrderAndSetMarker(selectedOrder);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    /**
     * Пользователь принял заказ: генерируем случайную точку в радиусе ~3 км от userLocation
     * и ставим метку на карте.
     */
    private void acceptOrderAndSetMarker(String orderName) {
        // Возьмём текущее местоположение "синей точки" из OverLay (если доступно)
        GeoPoint userLoc = myLocationOverlay.getMyLocation();
        if (userLoc == null) {
            // fallback на userLat/userLng
            if (userLat == 0 && userLng == 0) {
                Toast.makeText(this, "Неизвестно текущее положение!", Toast.LENGTH_SHORT).show();
                return;
            }
            userLoc = new GeoPoint(userLat, userLng);
        }

        // Генерируем случайное смещение
        double randomLatOffset = (new Random().nextDouble() - 0.5) * (2 * RANDOM_OFFSET);
        double randomLngOffset = (new Random().nextDouble() - 0.5) * (2 * RANDOM_OFFSET);

        double lat = userLoc.getLatitude() + randomLatOffset;
        double lng = userLoc.getLongitude() + randomLngOffset;
        currentOrderGeoPoint = new GeoPoint(lat, lng);

        drawOrderMarker(currentOrderGeoPoint, "Заказ: " + orderName);

        Toast.makeText(this, "Вы приняли заказ: " + orderName, Toast.LENGTH_SHORT).show();
    }

    /**
     * Рисуем или обновляем маркер заказа.
     */
    private void drawOrderMarker(GeoPoint geoPoint, String title) {
        // Удаляем старый маркер, если есть
        if (currentOrderMarker != null) {
            mapView.getOverlays().remove(currentOrderMarker);
        }
        currentOrderMarker = new Marker(mapView);
        currentOrderMarker.setPosition(geoPoint);
        currentOrderMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentOrderMarker.setTitle(title);
        mapView.getOverlays().add(currentOrderMarker);

        // Центрируем карту на новой точке
        mapView.getController().setCenter(geoPoint);
        mapView.getController().setZoom(15.0);
        mapView.invalidate();
    }

    /**
     * Кнопка "Доставить": завершаем заказ, убираем метку.
     */
    private void deliverOrder() {
        if (currentOrderGeoPoint != null) {
            Toast.makeText(this, "Заказ успешно доставлен!", Toast.LENGTH_SHORT).show();
            // Удалить метку
            if (currentOrderMarker != null) {
                mapView.getOverlays().remove(currentOrderMarker);
                currentOrderMarker = null;
            }
            currentOrderGeoPoint = null;
            // Выключить кнопку "Доставить"
            btnDeliver.setEnabled(false);
            mapView.invalidate();
        }
    }

    /**
     * Кнопка "Центр" - переместить карту на текущие координаты пользователя (userLat, userLng).
     */
    private void centerMapOnUser() {
        if (userLat == 0 && userLng == 0) {
            Toast.makeText(this, "Позиция пользователя неизвестна!", Toast.LENGTH_SHORT).show();
        } else {
            GeoPoint userGeo = new GeoPoint(userLat, userLng);
            mapView.getController().animateTo(userGeo);
            mapView.getController().setZoom(15.0);
        }
    }

    /**
     * Запрос разрешений на локацию
     */
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

    /**
     * Начинаем получать GPS/сеть-обновления локации
     */
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // GPS_PROVIDER
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        3000, // мс между обновлениями
                        1,    // м между обновлениями
                        locationListener
                );
            }
            // NETWORK_PROVIDER
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

    /**
     * Сохраняем важные данные при повороте экрана
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Сохраняем список заказов
        outState.putStringArrayList("orderList", new ArrayList<>(orderList));

        // Сохраняем координаты текущего заказа, если есть
        if (currentOrderGeoPoint != null) {
            outState.putBoolean("hasOrder", true);
            outState.putDouble("orderLat", currentOrderGeoPoint.getLatitude());
            outState.putDouble("orderLng", currentOrderGeoPoint.getLongitude());
        } else {
            outState.putBoolean("hasOrder", false);
        }

        // Сохраняем последние координаты пользователя
        outState.putDouble("userLat", userLat);
        outState.putDouble("userLng", userLng);
    }

    /**
     * Восстанавливаем данные
     */
    private void restoreStateFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey("orderList")) {
            // Восстанавливаем список
            List<String> restored = savedInstanceState.getStringArrayList("orderList");
            if (restored != null) {
                orderList.clear();
                orderList.addAll(restored);
            }
        }

        // Восстанавливаем координаты заказа
        boolean hasOrder = savedInstanceState.getBoolean("hasOrder", false);
        if (hasOrder) {
            double lat = savedInstanceState.getDouble("orderLat", 0.0);
            double lng = savedInstanceState.getDouble("orderLng", 0.0);
            currentOrderGeoPoint = new GeoPoint(lat, lng);
        } else {
            currentOrderGeoPoint = null;
        }

        // Восстанавливаем координаты пользователя
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

    // Утилита вычисления дистанции
    private float distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
}