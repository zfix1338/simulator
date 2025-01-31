package com.example.couriersimulator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
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
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // Существующие поля
    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private FloatingActionButton btnCenter;
    private MaterialButton btnOrders;
    private MaterialButton btnRefresh;
    private MaterialButton btnDeliver;
    private MaterialButton btnCards; // Новая кнопка коллекции
    private List<String> orderList = new ArrayList<>();
    private GeoPoint currentOrderGeoPoint = null;
    private Marker currentOrderMarker = null;
    private double userLat = 0.0;
    private double userLng = 0.0;
    private static final double RANDOM_OFFSET = 0.03;
    private static final float DELIVERY_RADIUS_METERS = 20f;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // Новые поля для карточек
    private CardManager cardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Конфигурация OSMDroid
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        Configuration.getInstance().load(
            getApplicationContext(),
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );

        setContentView(R.layout.activity_main);

        // Инициализация элементов
        mapView = findViewById(R.id.map);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        btnCenter = findViewById(R.id.btnCenter);
        btnOrders = findViewById(R.id.btnOrders);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnDeliver = findViewById(R.id.btnDeliver);
        btnCards = findViewById(R.id.btnCards); // Новая кнопка
        btnDeliver.setEnabled(false);

        // Инициализация карт и GPS
        myLocationOverlay = new MyLocationNewOverlay(mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Инициализация системы карточек
        cardManager = CardManager.getInstance(this);

        // Обработчики кликов
        btnCenter.setOnClickListener(v -> centerMapOnUser());
        btnOrders.setOnClickListener(v -> showOrdersBottomSheet());
        btnRefresh.setOnClickListener(v -> refreshOrders());
        btnDeliver.setOnClickListener(v -> deliverOrder());
        btnCards.setOnClickListener(v -> showCardCollection()); // Новый обработчик

        checkLocationPermission();

        // Восстановление состояния
        if (savedInstanceState != null) {
            restoreStateFromBundle(savedInstanceState);
        } else {
            loadInitialOrders();
        }

        if (currentOrderGeoPoint != null) {
            drawOrderMarker(currentOrderGeoPoint, "Текущий заказ");
        }
    }

    // ===========================================
    // НОВЫЕ МЕТОДЫ ДЛЯ КОЛЛЕКЦИОННЫХ КАРТОЧЕК
    // ===========================================

    /** Открыть коллекцию карточек */
    private void showCardCollection() {
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_collection, null);
        RecyclerView rvCards = sheetView.findViewById(R.id.rvCards);

        List<CollectibleCard> allCards = generateCardList();
        CardAdapter adapter = new CardAdapter(allCards, cardManager, this);
        rvCards.setLayoutManager(new GridLayoutManager(this, 3));
        rvCards.setAdapter(adapter);

        new BottomSheetDialog(this).setContentView(sheetView).show();
    }

    /** Генерация списка карточек */
    private List<CollectibleCard> generateCardList() {
        List<CollectibleCard> cards = new ArrayList<>();
        cards.add(new CollectibleCard(
            "bike_ice", 
            "Ледяной велосипед",
            "Создан из вечного льда арктических пустошей",
            R.drawable.bike_ice
        ));
        cards.add(new CollectibleCard(
            "bike_gold",
            "Золотой велосипед", 
            "Покрыт 24-каратным золотом",
            R.drawable.bike_gold
        ));
        return cards;
    }

    /** Проверка выпадения карточки */
    private void checkCardDrop() {
        if (new Random().nextDouble() <= 0.25) { // 25% шанс
            List<CollectibleCard> allCards = generateCardList();
            CollectibleCard randomCard = allCards.get(new Random().nextInt(allCards.size()));
            cardManager.unlockCard(randomCard.getId());
            showCardUnlockDialog(randomCard);
        }
    }

    /** Показать уведомление о новой карте */
    private void showCardUnlockDialog(CollectibleCard card) {
        new AlertDialog.Builder(this)
            .setTitle("Новая карта!")
            .setMessage("Вы получили: " + card.getTitle())
            .setPositiveButton("OK", null)
            .show();
    }

    // ===========================================
    // СУЩЕСТВУЮЩИЕ МЕТОДЫ С ИЗМЕНЕНИЯМИ
    // ===========================================

    /** Доставка заказа (обновлено) */
    private void deliverOrder() {
        if (currentOrderGeoPoint != null) {
            Toast.makeText(this, "Заказ успешно доставлен!", Toast.LENGTH_SHORT).show();
            
            // Новый код: шанс получить карточку
            checkCardDrop();
            
            // Существующий код
            if (currentOrderMarker != null) {
                mapView.getOverlays().remove(currentOrderMarker);
                currentOrderMarker = null;
            }
            currentOrderGeoPoint = null;
            btnDeliver.setEnabled(false);
            mapView.invalidate();
        }
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

        currentOrderGeoPoint = new GeoPoint(
                userLoc.getLatitude() + RANDOM_OFFSET * (new Random().nextDouble() - 0.5),
                userLoc.getLongitude() + RANDOM_OFFSET * (new Random().nextDouble() - 0.5)
        );
        drawOrderMarker(currentOrderGeoPoint, orderName);
        btnDeliver.setEnabled(true);
    }

    /** Отобразить маркер с заказом */
    private void drawOrderMarker(GeoPoint geoPoint, String title) {
        currentOrderMarker = new Marker(mapView);
        currentOrderMarker.setPosition(geoPoint);
        currentOrderMarker.setTitle(title);
        mapView.getOverlays().add(currentOrderMarker);
        mapView.invalidate();
    }

    /** Центрировать карту на текущем местоположении пользователя */
    private void centerMapOnUser() {
        if (myLocationOverlay.getMyLocation() != null) {
            mapView.getController().animateTo(myLocationOverlay.getMyLocation());
        }
    }

    // ===========================================
    // МЕТОДЫ ОБРАБОТКИ РАЗРЕШЕНИЙ НА GPS
    // ===========================================

    /** Проверка разрешений на использование GPS */
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE
            );
        } else {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    0f,
                    locationListener
            );
        }
    }

    // ===========================================
    // СОХРАНЕНИЕ СТАНА И ВОССТАНОВЛЕНИЕ
    // ===========================================

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Сохраняем состояние карты
        outState.putDouble("userLat", userLat);
        outState.putDouble("userLng", userLng);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            userLat = savedInstanceState.getDouble("userLat", 0.0);
            userLng = savedInstanceState.getDouble("userLng", 0.0);
        }
    }
}
