package com.example.couriersimulator;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CardManager {
    private static CardManager instance;
    private final SharedPreferences preferences;

    // Название файла настроек и ключи
    private static final String PREF_NAME = "CardPrefs";
    private static final String KEY_COLLECTED = "collected_cards";

    // Закрытый конструктор под Singleton
    private CardManager(Context context) {
        // Лучше использовать getApplicationContext(), чтобы избежать
        // утечек памяти, если CardManager живёт дольше активности.
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // Синхронизированная "точка входа" в Singleton
    public static synchronized CardManager getInstance(Context context) {
        if (instance == null) {
            instance = new CardManager(context);
        }
        return instance;
    }

    // Добавляем новую карточку в список собранных
    public void unlockCard(String cardId) {
        Set<String> collected = preferences.getStringSet(KEY_COLLECTED, new HashSet<>());
        // SharedPreferences возвращает Set<String>, который обычно неизменяемый,
        // поэтому лучше скопировать его в новый Set, прежде чем модифицировать.
        Set<String> updated = new HashSet<>(collected);
        updated.add(cardId);
        preferences.edit().putStringSet(KEY_COLLECTED, updated).apply();
    }

    // Получаем список всех собранных карточек
    public List<String> getCollectedCards() {
        Set<String> collected = preferences.getStringSet(KEY_COLLECTED, new HashSet<>());
        // Преобразуем Set в List
        return new ArrayList<>(collected);
    }

    // Проверяем, собрана ли карточка
    public boolean isCardCollected(String cardId) {
        return getCollectedCards().contains(cardId);
    }
}