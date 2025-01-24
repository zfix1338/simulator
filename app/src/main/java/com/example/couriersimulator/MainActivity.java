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
import android.view.View;
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

/**
 * Главная активити: при запуске показывается карта.
 * Снизу две кнопки:
 * 1) "Заказы" - открывает диалог с списком заказов,
 * 2) "Доставить" - по умолчанию неактивна, становится активной, когда игрок в радиусе 20м от заказа.
 */
public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay; // Показывает синим точку пользователя на OSMDroid-карте

    private Button btnOrders;    // кнопка "Заказы"
    private Button btnDeliver;   // кнопка "Доставить"

    // Менеджер и слушатель геолокации
    private LocationManager locationManager;
    private LocationListener locationListener;

    // Разрешения на геолокацию
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // Заказы (список). В реальном приложении можно подгружать с сервера.
    private List<String> orderList = new ArrayList<>();

    // Текущая координата принятого заказа (может быть null, если заказ не принят)
    private GeoPoint currentOrderGeoPoint = null;
    private Marker currentOrderMarker = null;

    // Радиус, внутри которого можно "доставить" заказ
    private static final float DELIVERY_RADIUS_METERS = 20f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Устанавливаем layout с картой и кнопками
        setContentView(R.layout.activity_main);

        // Инициализируем OSMDroid (обязательно указываем UserAgent)
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        mapView = findViewById(R.id.map);
        btnOrders = findViewById(R.id.btnOrders);
        btnDeliver = findViewById(R.id.btnDeliver);

        // Настраиваем карту
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);

        // Инициализация LocationManager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Создадим MyLocationNewOverlay, чтобы показывать текущее положение "синим точкой"
        myLocationOverlay = new MyLocationNewOverlay(mapView);
        myLocationOverlay.enableMyLocation(); 
        // Можно добавить компас или автопросмотр, если нужно:
        // myLocationOverlay.enableFollowLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Слушатель изменений GPS / сети
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Если есть активный заказ, проверяем расстояние
                if (currentOrderGeoPoint != null) {
                    float distance = distanceBetween(
                            location.getLatitude(), location.getLongitude(),
                            currentOrderGeoPoint.getLatitude(), currentOrderGeoPoint.getLongitude()
                    );
                    // Активируем кнопку "Доставить", если меньше DELIVERY_RADIUS_METERS
                    btnDeliver.setEnabled(distance <= DELIVERY_RADIUS_METERS);
                } else {
                    // Если заказа нет - кнопку отключаем
                    btnDeliver.setEnabled(false);
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        // Запросим/проверим разрешения
        checkLocationPermission();

        // Заполним список заказов (для примера)
        loadOrdersData();

        // Клик "Заказы" — показываем диалог, где можно выбрать заказ
        btnOrders.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showOrdersDialog();
            }
        });

        // Клик "Доставить" — завершаем доставку
        btnDeliver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentOrderGeoPoint != null) {
                    // Заказ доставлен
                    Toast.makeText(MainActivity.this, "Заказ успешно доставлен!", Toast.LENGTH_SHORT).show();
                    // Удалим метку с карты
                    if (currentOrderMarker != null) {
                        mapView.getOverlays().remove(currentOrderMarker);
                        currentOrderMarker = null;
                    }
                    // Сбросим текущий заказ
                    currentOrderGeoPoint = null;
                    btnDeliver.setEnabled(false);
                    mapView.invalidate();
                }
            }
        });
    }

    /**
     * Загружаем (или формируем) список заказов.
     * В реальном приложении — запрос с сервера, БД и т.п.
     */
    private void loadOrdersData() {
        orderList.clear();
        orderList.add("Доставка пиццы");
        orderList.add("Доставка документов");
        orderList.add("Продукты из магазина");
        orderList.add("Заказ из аптеки");
    }

    /**
     * Показываем AlertDialog со списком заказов.
     * При выборе удаляем заказ из списка и генерируем его координаты на карте.
     */
    private void showOrdersDialog() {
        if (orderList.isEmpty()) {
            Toast.makeText(this, "Нет доступных заказов", Toast.LENGTH_SHORT).show();
            return;
        }

        // Превратим список во временный массив
        final String[] ordersArray = orderList.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Доступные заказы");
        builder.setItems(ordersArray, (dialog, which) -> {
            // Пользователь кликнул на элемент which
            String selectedOrder = ordersArray[which];
            // Удалим его из списка, чтобы нельзя было выбрать повторно
            orderList.remove(which);

            // Приняли заказ => генерируем случайную точку рядом с тек. местоположением
            acceptOrderAndSetMarker(selectedOrder);
        });
        builder.show();
    }

    /**
     * Принимаем заказ:
     * 1) Находим текущее местоположение пользователя (через MyLocationOverlay или через последний Location).
     * 2) Генерируем случайное смещение (например, в радиусе 1 км).
     * 3) Ставим маркер на карте.
     */
    private void acceptOrderAndSetMarker(String orderName) {
        // Попробуем взять текущее местоположение из MyLocationOverlay
        GeoPoint userGeoPoint = myLocationOverlay.getMyLocation();
        if (userGeoPoint == null) {
            // Если по каким-то причинам ещё нет локации
            Toast.makeText(this, "Не удалось определить текущее положение", Toast.LENGTH_SHORT).show();
            return;
        }

        // Сгенерируем случайное смещение (в градусах ~ 0.01 = ~1.11 км на широте)
        // Можете поменять коэффициент для другого радиуса
        double randomLatOffset = (new Random().nextDouble() - 0.5) * 0.02;
        double randomLngOffset = (new Random().nextDouble() - 0.5) * 0.02;

        double targetLat = userGeoPoint.getLatitude() + randomLatOffset;
        double targetLng = userGeoPoint.getLongitude() + randomLngOffset;

        // Запоминаем координату заказа
        currentOrderGeoPoint = new GeoPoint(targetLat, targetLng);

        // Если уже был маркер на карте — убираем
        if (currentOrderMarker != null) {
            mapView.getOverlays().remove(currentOrderMarker);
            currentOrderMarker = null;
        }

        // Создаём новый маркер
        currentOrderMarker = new Marker(mapView);
        currentOrderMarker.setPosition(currentOrderGeoPoint);
        currentOrderMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentOrderMarker.setTitle("Заказ: " + orderName);
        mapView.getOverlays().add(currentOrderMarker);

        // Переместим камеру, чтобы было видно новую точку
        mapView.getController().setCenter(currentOrderGeoPoint);
        // Опционально можно установить зум поближе:
        mapView.getController().setZoom(15.0);

        // Обновим карту
        mapView.invalidate();

        Toast.makeText(this, "Вы приняли заказ: " + orderName, Toast.LENGTH_SHORT).show();
    }

    /**
     * Запрашивает разрешения, если они ещё не даны
     */
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE);
        } else {
            // Разрешения уже есть
            startLocationUpdates();
        }
    }

    /**
     * Запускаем запрос локации через LocationManager
     */
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        3000, // время (мс) между обновлениями
                        1,    // расстояние (м) между обновлениями
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
        // Отключаемся от прослушки локации
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    /**
     * Утилита для вычисления расстояния между двумя точками в метрах
     */
    private float distanceBetween(double lat1, double lng1, double lat2, double lng2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, results);
        return results[0];
    }
}