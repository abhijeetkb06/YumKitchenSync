package com.kitchensync.ui.manager;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.kitchensync.R;
import com.kitchensync.data.model.Order;
import com.kitchensync.util.Constants;
import com.kitchensync.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the manager dashboard's recent orders list.
 * Shows order label, item count, elapsed time, and a color-coded status badge.
 */
public class OrderSummaryAdapter extends RecyclerView.Adapter<OrderSummaryAdapter.ViewHolder> {

    private List<Order> orders = new ArrayList<>();

    public void setOrders(List<Order> orders) {
        this.orders = new ArrayList<>(orders);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_summary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);

        holder.textTable.setText(order.getDisplayLabel());
        holder.textItems.setText(order.getItemCount() + " items");
        holder.textTime.setText(TimeUtils.getElapsedTime(order.getCreatedAt()));

        // Status badge
        holder.textStatus.setText(order.getStatus().toUpperCase());
        int statusColor;
        switch (order.getStatus()) {
            case Constants.ORDER_STATUS_NEW:
                statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_new);
                break;
            case Constants.ORDER_STATUS_PREPARING:
                statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_preparing);
                break;
            case Constants.ORDER_STATUS_READY:
                statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_ready);
                break;
            default:
                statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_picked_up);
                break;
        }

        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.RECTANGLE);
        badge.setCornerRadius(12);
        badge.setColor(statusColor);
        holder.textStatus.setBackground(badge);
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textTable, textStatus, textItems, textTime;

        ViewHolder(View itemView) {
            super(itemView);
            textTable = itemView.findViewById(R.id.text_table);
            textStatus = itemView.findViewById(R.id.text_status_badge);
            textItems = itemView.findViewById(R.id.text_items);
            textTime = itemView.findViewById(R.id.text_time);
        }
    }
}
