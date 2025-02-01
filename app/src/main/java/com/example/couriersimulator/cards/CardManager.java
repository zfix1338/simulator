package com.example.couriersimulator.cards;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.couriersimulator.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер коллекционных карточек.
 * 1) Содержит статический список всех "уникальных велосипедов".
 * 2) Сохраняет / загружает флаг isOwned для каждой карточки.
 */
public class CardManager {
    private static final String PREFS_NAME = "CourierSimulatorCardsPrefs";
    private static final String KEY_OWNED_PREFIX = "CARD_OWNED_";

    // Статический список всех доступных карт (можно расширять)
    private static final List<CollectibleCard> ALL_CARDS = new ArrayList<>();

    static {
        // Добавляем несколько примерных велосипедов
        ALL_CARDS.add(new CollectibleCard(
                "icy",
                "Ледяной велосипед",
                "Велосипед, выкованный из вечных льдов. Говорят, на нём ездили сами духи зимы...",
                R.drawable.icy_bike
        ));
        ALL_CARDS.add(new CollectibleCard(
                "gold",
                "Золотой велосипед",
                "Легендарный велосипед, покрытый сусальным золотом. Найден в древнем храме...",
                R.drawable.gold_bike
        ));
        ALL_CARDS.add(new CollectibleCard(
                "wood",
                "Деревянный велосипед",
                "Простой, но надёжный велосипед, полностью выполненный из древесины дуба.",
                R.drawable.wood_bike
        ));
        // Можно продолжать добавлять новые...
    }

    /**
     * Возвращает копию всех карточек, при этом подгружая флаг isOwned из SharedPreferences.
     */
    public static List<CollectibleCard> getAllCards(Context context) {
        List<CollectibleCard> result = new ArrayList<>();
        for (CollectibleCard base : ALL_CARDS) {
            CollectibleCard copy = new CollectibleCard(
                    base.getId(),
                    base.getName(),
                    base.getDescription(),
                    base.getImageResId()
            );
            // Проставляем isOwned, исходя из сохранённых данных
            copy.setOwned(isCardOwned(context, base.getId()));
            result.add(copy);
        }
        return result;
    }

    /**
     * Отметить, что пользователь теперь владеет (или не владеет) данной карточкой.
     */
    public static void setCardOwned(Context context, String cardId, boolean owned) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_OWNED_PREFIX + cardId, owned).apply();
    }

    /**
     * Проверить, владеет ли пользователь конкретной карточкой.
     */
    public static boolean isCardOwned(Context context, String cardId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_OWNED_PREFIX + cardId, false);
    }
}