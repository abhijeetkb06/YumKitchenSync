package com.kitchensync.ui.waiter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kitchensync.R;
import com.kitchensync.data.model.OrderItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shopping cart adapter for the kiosk self-order screen.
 * Supports adding items (with quantity merging for duplicates), removing items,
 * and computing the running total.
 */
public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    public interface OnCartChangeListener {
        void onItemRemoved(int position);
    }

    private final List<OrderItem> items = new ArrayList<>();
    private final OnCartChangeListener listener;

    public CartAdapter(OnCartChangeListener listener) {
        this.listener = listener;
    }

    public void addItem(OrderItem item) {
        // Check if item already in cart, increment quantity
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getMenuItemId().equals(item.getMenuItemId())) {
                OrderItem existing = items.get(i);
                existing.setQuantity(existing.getQuantity() + 1);
                notifyItemChanged(i);
                return;
            }
        }
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public List<OrderItem> getItems() {
        return new ArrayList<>(items);
    }

    public void clear() {
        int size = items.size();
        items.clear();
        notifyItemRangeRemoved(0, size);
    }

    public double getTotal() {
        double total = 0;
        for (OrderItem item : items) {
            total += item.getLineTotal();
        }
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrderItem item = items.get(position);
        holder.quantity.setText(String.valueOf(item.getQuantity()) + "x");
        holder.name.setText(item.getName());
        holder.price.setText(String.format(Locale.US, "$%.2f", item.getLineTotal()));
        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                removeItem(pos);
                listener.onItemRemoved(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView quantity, name, price;
        ImageView btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            quantity = itemView.findViewById(R.id.text_quantity);
            name = itemView.findViewById(R.id.text_name);
            price = itemView.findViewById(R.id.text_price);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}
