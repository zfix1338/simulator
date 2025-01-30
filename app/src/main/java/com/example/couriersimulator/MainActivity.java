package com.example.couriersimulator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;
    private FloatingActionButton btnCenter;
    private MaterialButton btnOrders, btnRefresh, btnDeliver, btnCards;
    private List<String> orderList = new ArrayList<>();
    private GeoPoint currentOrderGeoPoint = null;
    private Marker currentOrderMarker = null;
    private double userLat = 0.0, userLng = 0.0;
    private static final double RANDOM_OFFSET = 0.03;
    private static final float DELIVERY_RADIUS_METERS = 20f;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String PREF_CARDS = "unlocked_cards";
    private CardManager cardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        Configuration.getInstance().load(
            getApplicationContext(),
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.map);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        btnCenter = findViewById(R.id.btnCenter);
        btnOrders = findViewById(R.id.btnOrders);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnDeliver = findViewById(R.id.btnDeliver);
        btnCards = findViewById(R.id.btnCards);
        btnDeliver.setEnabled(false);

        myLocationOverlay = new MyLocationNewOverlay(mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        cardManager = CardManager.getInstance(this);
        loadUnlockedCards();

        btnCenter.setOnClickListener(v -> centerMapOnUser());
        btnOrders.setOnClickListener(v -> showOrdersBottomSheet());
        btnRefresh.setOnClickListener(v -> refreshOrders());
        btnDeliver.setOnClickListener(v -> deliverOrder());
        btnCards.setOnClickListener(v -> showCardCollection());

        checkLocationPermission();
        if (savedInstanceState != null) restoreStateFromBundle(savedInstanceState);
        else loadInitialOrders();

        if (currentOrderGeoPoint != null) drawOrderMarker(currentOrderGeoPoint, "Текущий заказ");
    }

    private void showCardCollection() {
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_collection, null);
        RecyclerView rvCards = sheetView.findViewById(R.id.rvCards);
        List<CollectibleCard> allCards = generateCardList();
        CardAdapter adapter = new CardAdapter(allCards, cardManager, this);
        rvCards.setLayoutManager(new GridLayoutManager(this, 3));
        rvCards.setAdapter(adapter);
        new BottomSheetDialog(this).setContentView(sheetView).show();
    }

    private List<CollectibleCard> generateCardList() {
        List<CollectibleCard> cards = new ArrayList<>();
        cards.add(new CollectibleCard("bike_ice", "Ледяной велосипед", "Создан из вечного льда", R.drawable.bike_ice));
        cards.add(new CollectibleCard("bike_gold", "Золотой велосипед", "Покрыт 24-каратным золотом", R.drawable.bike_gold));
        return cards;
    }

    private void checkCardDrop() {
        if (new Random().nextDouble() <= 0.15) {
            List<CollectibleCard> allCards = generateCardList();
            CollectibleCard randomCard = allCards.get(new Random().nextInt(allCards.size()));
            if (!cardManager.isCardUnlocked(randomCard.getId())) {
                cardManager.unlockCard(randomCard.getId());
                saveUnlockedCards();
                showCardUnlockDialog(randomCard);
            }
        }
    }

    private void showCardUnlockDialog(CollectibleCard card) {
        new AlertDialog.Builder(this)
            .setTitle("Новая карта!")
            .setMessage("Вы получили: " + card.getTitle())
            .setPositiveButton("OK", null)
            .show();
    }

    private void deliverOrder() {
        if (currentOrderGeoPoint != null) {
            Toast.makeText(this, "Заказ успешно доставлен!", Toast.LENGTH_SHORT).show();
            checkCardDrop();
            if (currentOrderMarker != null) {
                mapView.getOverlays().remove(currentOrderMarker);
                currentOrderMarker = null;
            }
            currentOrderGeoPoint = null;
            btnDeliver.setEnabled(false);
            mapView.invalidate();
        }
    }

    private void saveUnlockedCards() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(PREF_CARDS, new HashSet<>(cardManager.getUnlockedCards()));
        editor.apply();
    }

    private void loadUnlockedCards() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> unlockedCards = prefs.getStringSet(PREF_CARDS, new HashSet<>());
        for (String cardId : unlockedCards) {
            cardManager.unlockCard(cardId);
        }
    }
}
