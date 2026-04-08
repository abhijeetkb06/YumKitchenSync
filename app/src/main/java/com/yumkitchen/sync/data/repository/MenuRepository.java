package com.yumkitchen.sync.data.repository;

import com.couchbase.lite.Collection;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.MutableDocument;

import com.yumkitchen.sync.data.CouchbaseManager;
import com.yumkitchen.sync.data.model.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class MenuRepository {

    private static final MenuItem[] SEED_ITEMS = {
        new MenuItem("menu::burger_classic", "Classic Burger", "Mains", 12.99, "\uD83C\uDF54"),
        new MenuItem("menu::street_tacos", "Street Tacos (3)", "Mains", 10.99, "\uD83C\uDF2E"),
        new MenuItem("menu::crispy_wings", "Crispy Wings (8)", "Mains", 11.99, "\uD83C\uDF57"),
        new MenuItem("menu::garden_salad", "Garden Salad", "Mains", 9.99, "\uD83E\uDD57"),
        new MenuItem("menu::margherita_pizza", "Margherita Pizza", "Mains", 14.99, "\uD83C\uDF55"),
        new MenuItem("menu::grilled_salmon", "Grilled Salmon", "Mains", 16.99, "\uD83C\uDF63"),
        new MenuItem("menu::loaded_fries", "Loaded Fries", "Sides", 6.99, "\uD83C\uDF5F"),
        new MenuItem("menu::onion_rings", "Onion Rings", "Sides", 5.99, "\uD83E\uDDC5"),
        new MenuItem("menu::coleslaw", "Coleslaw", "Sides", 3.99, "\uD83E\uDD57"),
        new MenuItem("menu::lemonade", "Fresh Lemonade", "Drinks", 3.99, "\uD83E\uDD64"),
        new MenuItem("menu::craft_beer", "Craft Beer", "Drinks", 7.99, "\uD83C\uDF7A"),
        new MenuItem("menu::iced_coffee", "Iced Coffee", "Drinks", 4.99, "\u2615"),
        new MenuItem("menu::cheesecake", "Cheesecake", "Desserts", 8.99, "\uD83C\uDF70"),
        new MenuItem("menu::brownie_sundae", "Brownie Sundae", "Desserts", 7.99, "\uD83C\uDF68"),
    };

    public void seedMenuIfNeeded() throws CouchbaseLiteException {
        Collection collection = CouchbaseManager.getInstance().getCollection();
        if (collection == null) return;

        // Check if menu items already exist
        if (collection.getDocument("menu::burger_classic") != null) return;

        for (MenuItem item : SEED_ITEMS) {
            MutableDocument doc = new MutableDocument(item.getMenuItemId());
            for (java.util.Map.Entry<String, Object> entry : item.toMap().entrySet()) {
                if (entry.getValue() instanceof String) {
                    doc.setString(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() instanceof Double) {
                    doc.setDouble(entry.getKey(), (Double) entry.getValue());
                } else if (entry.getValue() instanceof Boolean) {
                    doc.setBoolean(entry.getKey(), (Boolean) entry.getValue());
                }
            }
            collection.save(doc);
        }
    }

    public List<MenuItem> getAllMenuItems() throws CouchbaseLiteException {
        List<MenuItem> items = new ArrayList<>();
        Collection collection = CouchbaseManager.getInstance().getCollection();
        if (collection == null) return items;

        for (MenuItem seed : SEED_ITEMS) {
            com.couchbase.lite.Document doc = collection.getDocument(seed.getMenuItemId());
            if (doc != null) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("menuItemId", doc.getString("menuItemId"));
                map.put("name", doc.getString("name"));
                map.put("category", doc.getString("category"));
                map.put("price", doc.getDouble("price"));
                map.put("emoji", doc.getString("emoji"));
                map.put("available", doc.getBoolean("available"));
                items.add(MenuItem.fromMap(map));
            }
        }
        return items;
    }

    public List<MenuItem> getMenuItemsByCategory(String category) throws CouchbaseLiteException {
        List<MenuItem> all = getAllMenuItems();
        if (category == null || category.equals("All")) return all;
        List<MenuItem> filtered = new ArrayList<>();
        for (MenuItem item : all) {
            if (category.equals(item.getCategory())) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
