package com.example.couriersimulator.cards;

/**
 * Модель для коллекционной карточки велосипеда.
 * Хранит:
 * - уникальный id
 * - название (например, "Ледяной велосипед")
 * - описание (лор)
 * - int-ресурс изображения (R.drawable.icy_bike)
 * - признак владения (isOwned)
 */
public class CollectibleCard {

    private final String id;
    private final String name;
    private final String description;
    private final int imageResId;   // ID ресурса изображения, например R.drawable.icy_bike
    private boolean isOwned;

    public CollectibleCard(String id, String name, String description, int imageResId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageResId = imageResId;
        this.isOwned = false; // По умолчанию карточка не собрана
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getImageResId() {
        return imageResId;
    }

    public boolean isOwned() {
        return isOwned;
    }

    public void setOwned(boolean owned) {
        isOwned = owned;
    }
}