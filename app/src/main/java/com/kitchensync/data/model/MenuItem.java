package com.kitchensync.data.model;

import java.util.HashMap;
import java.util.Map;

public class MenuItem {
    private String menuItemId;
    private String name;
    private String category;
    private double price;
    private String emoji;
    private boolean available;

    public MenuItem() {}

    public MenuItem(String menuItemId, String name, String category, double price, String emoji) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.category = category;
        this.price = price;
        this.emoji = emoji;
        this.available = true;
    }

    public static MenuItem fromMap(Map<String, Object> map) {
        MenuItem item = new MenuItem();
        item.menuItemId = (String) map.get("menuItemId");
        item.name = (String) map.get("name");
        item.category = (String) map.get("category");
        item.price = ((Number) map.get("price")).doubleValue();
        item.emoji = (String) map.get("emoji");
        Object avail = map.get("available");
        item.available = avail == null || (Boolean) avail;
        return item;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "menu_item");
        map.put("menuItemId", menuItemId);
        map.put("name", name);
        map.put("category", category);
        map.put("price", price);
        map.put("emoji", emoji);
        map.put("available", available);
        return map;
    }

    public String getMenuItemId() { return menuItemId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public String getEmoji() { return emoji; }
    public boolean isAvailable() { return available; }
}
