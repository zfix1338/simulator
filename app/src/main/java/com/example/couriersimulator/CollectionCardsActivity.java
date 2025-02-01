package com.example.couriersimulator;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.couriersimulator.cards.CardManager;
import com.example.couriersimulator.cards.CollectibleCard;
import com.example.couriersimulator.cards.CollectibleCardAdapter;
import com.example.couriersimulator.R;

import java.util.List;

/**
 * Активити, которое показывает коллекционные карточки (велосипеды):
 * - RecyclerView со списком
 * - Нажатие на карточку открывает диалог с большим изображением и описанием
 */
public class CollectionCardsActivity extends AppCompatActivity {

    private RecyclerView rvCards;
    private CollectibleCardAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection_cards);

        rvCards = findViewById(R.id.rvCards);
        rvCards.setLayoutManager(new LinearLayoutManager(this));

        // Загружаем все карточки (учитывая их статус из SharedPreferences)
        List<CollectibleCard> allCards = CardManager.getAllCards(this);

        adapter = new CollectibleCardAdapter(this, allCards);
        rvCards.setAdapter(adapter);
    }
}