package com.yumkitchen.sync.ui.waiter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.yumkitchen.sync.R;
import com.yumkitchen.sync.data.CouchbaseManager;
import com.yumkitchen.sync.data.model.MenuItem;
import com.yumkitchen.sync.data.model.Order;
import com.yumkitchen.sync.data.model.OrderItem;
import com.yumkitchen.sync.data.repository.MenuRepository;
import com.yumkitchen.sync.data.repository.OrderRepository;

import java.util.List;
import java.util.Locale;

public class WaiterMenuFragment extends Fragment implements MenuItemAdapter.OnItemClickListener,
        CartAdapter.OnCartChangeListener {

    private MenuItemAdapter menuAdapter;
    private CartAdapter cartAdapter;
    private MaterialCardView cardCart;
    private TextView textTotal;
    private TextInputEditText editTableNumber;
    private final MenuRepository menuRepo = new MenuRepository();
    private final OrderRepository orderRepo = new OrderRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_waiter_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupMenuGrid(view);
        setupCart(view);
        setupCategoryFilter(view);
        loadMenu(null);
    }

    private void setupMenuGrid(View view) {
        RecyclerView recyclerMenu = view.findViewById(R.id.recycler_menu);
        menuAdapter = new MenuItemAdapter(this);
        recyclerMenu.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerMenu.setAdapter(menuAdapter);
    }

    private void setupCart(View view) {
        cardCart = view.findViewById(R.id.card_cart);
        textTotal = view.findViewById(R.id.text_total);
        editTableNumber = view.findViewById(R.id.edit_table_number);

        RecyclerView recyclerCart = view.findViewById(R.id.recycler_cart);
        cartAdapter = new CartAdapter(this);
        recyclerCart.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerCart.setAdapter(cartAdapter);

        MaterialButton btnSend = view.findViewById(R.id.btn_send_order);
        btnSend.setOnClickListener(v -> submitOrder());
    }

    private void setupCategoryFilter(View view) {
        ChipGroup chipGroup = view.findViewById(R.id.chip_group_categories);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            String category = null;
            if (checkedIds.contains(R.id.chip_mains)) category = "Mains";
            else if (checkedIds.contains(R.id.chip_sides)) category = "Sides";
            else if (checkedIds.contains(R.id.chip_drinks)) category = "Drinks";
            else if (checkedIds.contains(R.id.chip_desserts)) category = "Desserts";
            loadMenu(category);
        });
    }

    private void loadMenu(String category) {
        try {
            List<MenuItem> items = menuRepo.getMenuItemsByCategory(category);
            menuAdapter.setItems(items);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error loading menu", Toast.LENGTH_SHORT).show();
        }
    }

    // MenuItemAdapter.OnItemClickListener
    @Override
    public void onAddToCart(MenuItem item) {
        OrderItem orderItem = new OrderItem(
                item.getMenuItemId(), item.getName(), 1, "", item.getPrice(), item.getEmoji());
        cartAdapter.addItem(orderItem);
        updateCartVisibility();
    }

    // CartAdapter.OnCartChangeListener
    @Override
    public void onItemRemoved(int position) {
        updateCartVisibility();
    }

    private void updateCartVisibility() {
        if (cartAdapter.isEmpty()) {
            cardCart.setVisibility(View.GONE);
        } else {
            cardCart.setVisibility(View.VISIBLE);
            textTotal.setText(String.format(Locale.US, "$%.2f", cartAdapter.getTotal()));
        }
    }

    private void submitOrder() {
        if (cartAdapter.isEmpty()) {
            Toast.makeText(requireContext(), "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String tableStr = editTableNumber.getText() != null
                ? editTableNumber.getText().toString().trim() : "";
        if (tableStr.isEmpty()) {
            editTableNumber.setError("Required");
            return;
        }

        int tableNumber;
        try {
            tableNumber = Integer.parseInt(tableStr);
        } catch (NumberFormatException e) {
            editTableNumber.setError("Invalid number");
            return;
        }

        // Show confirmation dialog
        showConfirmDialog(tableNumber);
    }

    private void showConfirmDialog(int tableNumber) {
        List<OrderItem> items = cartAdapter.getItems();
        StringBuilder sb = new StringBuilder();
        sb.append("Table ").append(tableNumber).append("\n\n");
        for (OrderItem item : items) {
            sb.append(item.getQuantity()).append("x ").append(item.getName())
                    .append(" - $").append(String.format(Locale.US, "%.2f", item.getLineTotal()))
                    .append("\n");
        }
        sb.append("\nTotal: $").append(String.format(Locale.US, "%.2f", cartAdapter.getTotal()));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_order)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.send_to_kitchen, (d, w) -> {
                    placeOrder(tableNumber, items);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void placeOrder(int tableNumber, List<OrderItem> items) {
        try {
            String peerId = CouchbaseManager.getInstance().getCurrentPeerId();
            String peerName = android.os.Build.MODEL + " - Waiter";
            Order order = Order.create(tableNumber, items, peerId, peerName);
            orderRepo.saveOrder(order);

            // Clear cart
            cartAdapter.clear();
            editTableNumber.setText("");
            updateCartVisibility();

            Toast.makeText(requireContext(),
                    "Order " + order.getShortId() + " sent to kitchen!",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
