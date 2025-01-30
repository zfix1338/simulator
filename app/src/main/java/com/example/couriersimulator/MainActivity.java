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

    /** Генерация списка карточек */
    private List<CollectibleCard> generateCardList() {
        List<CollectibleCard> cards = new ArrayList<>();
        cards.add(new CollectibleCard("bike_ice", "Ледяной велосипед", "Создан из вечного льда", R.mipmap.bike_ice));
        cards.add(new CollectibleCard("bike_gold", "Золотой велосипед", "Покрыт 24-каратным золотом", R.mipmap.bike_gold));
        return cards;
    }
}
