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
    private static final String PREF_NAME = "CardPrefs";
    private static final String KEY_COLLECTED = "collected_cards";

    private CardManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized CardManager getInstance(Context context) {
        if (instance == null) {
            instance = new CardManager(context);
        }
        return instance;
    }

    public void unlockCard(String cardId) {
        Set<String> collected = preferences.getStringSet(KEY_COLLECTED, new HashSet<>());
        collected.add(cardId);
        preferences.edit().putStringSet(KEY_COLLECTED, collected).apply();
    }

    public List<String> getCollectedCards() {
        return new ArrayList<>(preferences.getStringSet(KEY_COLLECTED, new HashSet<>()));
    }

    public boolean isCardCollected(String cardId) {
        return getCollectedCards().contains(cardId);
    }
}