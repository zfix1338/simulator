package com.example.couriersimulator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
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

import com.example.couriersimulator.cards.CardManager;            // <-- Наш менеджер карточек
import com.example.couriersimulator.cards.CollectibleCard;       // <-- Модель карточки (при необходимости)
import com.example.couriersimulator.R;                           // <-- Ссылка на ресурсы c
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
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

    private FloatingActionButton btnCenter;
    private MaterialButton btnOrders;
    private MaterialButton btnRefresh;
    private MaterialButton btnDeliver;
    private MaterialButton btnCollection; // <- Новая кнопка "Коллекция"

    private List<String> orderList = new ArrayList<>();
    private GeoPoint currentOrderGeoPoint = null;
    private Marker currentOrderMarker = null;

    private double userLat = 0.0;
    private double userLng = 0.0;

    // Генерация псевдо-случайного заказа ~3 км
    private static final double RANDOM_OFFSET = 0.03;
    // Радиус, в котором кнопка "Доставить" активируется
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

        // Инициализируем элементы UI
        mapView = findViewById(R.id.map);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        btnCenter = findViewById(R.id.btnCenter);
        btnOrders = findViewById(R.id.btnOrders);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnDeliver = findViewById(R.id.btnDeliver);
        btnDeliver.setEnabled(false);

        // Новая кнопка для коллекции
        btnCollection = findViewById(R.id.btnCollection);
        btnCollection.setOnClickListener(v -> {
            // Переход в экран "CollectionCardsActivity"
            Intent intent = new Intent(MainActivity.this, CollectionCardsActivity.class);
            startActivity(intent);
        });

        // Слой с "синей точкой" локации
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
                // Если есть текущий заказ, проверяем расстояние
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

        // Восстанавливаем состояние (если переворот экрана)
        if (savedInstanceState != null) {
            restoreStateFromBundle(savedInstanceState);
        } else {
            // Иначе загружаем начальный список заказов
            loadInitialOrders();
        }

        // Если уже есть заказ, отрисуем метку заново
        if (currentOrderGeoPoint != null) {
            drawOrderMarker(currentOrderGeoPoint, "Текущий заказ");
        }

        // Обработчики кликов
        btnCenter.setOnClickListener(v -> centerMapOnUser());
        btnOrders.setOnClickListener(v -> showOrdersBottomSheet());
        btnRefresh.setOnClickListener(v -> refreshOrders());
        btnDeliver.setOnClickListener(v -> deliverOrder());
    }

    /** Начальные заказы */
    private void loadInitialOrders() {
        orderList.clear();
        orderList.add("Доставка пиццы");
        orderList.add("Доставка документов");
        orderList.add("Продукты из магазина");
        orderList.add("Заказ из аптеки");
    }

    /** "Обновить" — добавляем новые заказы для примера */
    private void refreshOrders() {
        orderList.add("Новая посылка");
        orderList.add("Цветы на праздник");
        Toast.makeText(this, "Список заказов обновлён!", Toast.LENGTH_SHORT).show();
    }

    /** Показать BottomSheetDialog со списком заказов */
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

        // При выборе заказа
        lvOrders.setOnItemClickListener((parent, view, position, id) -> {
            String selected = orderList.get(position);
            // Удаляем из списка, принимаем заказ
            orderList.remove(position);
            adapter.notifyDataSetChanged();

            acceptOrderAndSetMarker(selected);
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Принять заказ, сгенерировать случайную точку рядом с пользователем */
    private void acceptOrderAndSetMarker(String orderName) {
        GeoPoint userLoc = myLocationOverlay.getMyLocation();
        if (userLoc == null) {
            // Если overlay не знает координаты, fallback на userLat/userLng
            if (userLat == 0 && userLng == 0) {
                Toast.makeText(this, "Неизвестно текущее положение!", Toast.LENGTH_SHORT).show();
                return;
            }
            userLoc = new GeoPoint(userLat, userLng);
        }

        // Генерация смещения ~3 км
        double latOffset = (new Random().nextDouble() - 0.5) * (2 * RANDOM_OFFSET);
        double lngOffset = (new Random().nextDouble() - 0.5) * (2 * RANDOM_OFFSET);
        double lat = userLoc.getLatitude() + latOffset;
        double lng = userLoc.getLongitude() + lngOffset;

        currentOrderGeoPoint = new GeoPoint(lat, lng);
        drawOrderMarker(currentOrderGeoPoint, "Заказ: " + orderName);
        Toast.makeText(this, "Вы приняли заказ: " + orderName, Toast.LENGTH_SHORT).show();
    }

    /** Поставить/обновить метку заказа на карте */
    private void drawOrderMarker(GeoPoint geoPoint, String title) {
        if (currentOrderMarker != null) {
            mapView.getOverlays().remove(currentOrderMarker);
        }
        currentOrderMarker = new Marker(mapView);
        currentOrderMarker.setPosition(geoPoint);
        currentOrderMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentOrderMarker.setTitle(title);
        mapView.getOverlays().add(currentOrderMarker);

        // Центрируем камеру
        mapView.getController().setCenter(geoPoint);
        mapView.getController().setZoom(15.0);
        mapView.invalidate();
    }

    /** Доставка заказа */
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

            // Пример: Случайная выдача карточки (если хотите геймификацию)
            maybeAwardCard();
        }
    }

    /**
     * Пример метода: случайным образом "выдаём" карточку из нашего списка при доставке.
     * Вы можете настроить вероятность и т.д. по своему желанию.
     */
    private void maybeAwardCard() {
        double chance = 0.4; // 40% шанс, что вообще что-то выпадет
        if (Math.random() > chance) {
            return; // Ничего не выпало
        }
        // Получаем все карточки (которые уже сохранены в CardManager)
        List<CollectibleCard> allCards = CardManager.getAllCards(this);
        // Фильтруем те, которыми пользователь не владеет
        List<CollectibleCard> notOwned = new ArrayList<>();
        for (CollectibleCard c : allCards) {
            if (!c.isOwned()) {
                notOwned.add(c);
            }
        }
        if (notOwned.isEmpty()) {
            // Все уже собраны
            Toast.makeText(this, "Все карточки уже собраны! Ничего не выпадает.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Случайно выбираем одну из несобранных
        CollectibleCard randomCard = notOwned.get(new Random().nextInt(notOwned.size()));
        // Помечаем её как собранную
        CardManager.setCardOwned(this, randomCard.getId(), true);
        // Уведомляем пользователя
        Toast.makeText(this, "Вы получили новую карточку: " + randomCard.getName(), Toast.LENGTH_LONG).show();
    }

    /** Центрируем карту на пользователя */
    private void centerMapOnUser() {
        if (userLat == 0 && userLng == 0) {
            Toast.makeText(this, "Позиция пользователя неизвестна!", Toast.LENGTH_SHORT).show();
        } else {
            GeoPoint userGeo = new GeoPoint(userLat, userLng);
            mapView.getController().animateTo(userGeo);
            mapView.getController().setZoom(15.0);
        }
    }

    /** Проверка GPS-разрешений */
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

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

    /** Запуск GPS */
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

    /** Сохранение состояния (при перевороте экрана) */
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

    /** Восстановление состояния */
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

    /** Утилита: вычислить дистанцию между двумя точками (метры) */
    private float distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
}