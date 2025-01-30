package com.example.couriersimulator;

public class CollectibleCard {
    private String id;
    private String title;
    private String lore;
    private int imageResId;
    private boolean isCollected;

    public CollectibleCard(String id, String title, String lore, int imageResId) {
        this.id = id;
        this.title = title;
        this.lore = lore;
        this.imageResId = imageResId;
        this.isCollected = false;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getLore() { return lore; }
    public int getImageResId() { return imageResId; }
    public boolean isCollected() { return isCollected; }
    public void setCollected(boolean collected) { isCollected = collected; }
}