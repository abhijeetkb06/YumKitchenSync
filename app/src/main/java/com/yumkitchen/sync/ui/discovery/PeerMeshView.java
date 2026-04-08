package com.yumkitchen.sync.ui.discovery;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.core.content.ContextCompat;

import com.yumkitchen.sync.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeerMeshView extends View {

    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float centerX, centerY;
    private float centerRadius = 50f;
    private float peerRadius = 35f;

    private String deviceLabel = "This Device";
    private String deviceRole = "";

    // Peer data: peerId -> animated scale (0->1)
    private final Map<String, Float> peerScales = new HashMap<>();
    private final List<String> peerIds = new ArrayList<>();
    private final Map<String, String> peerLabels = new HashMap<>();
    private final Map<String, Integer> peerColors = new HashMap<>();

    // Pulse animation
    private float pulseScale = 1.0f;
    private float pulseAlpha = 0.6f;
    private ValueAnimator pulseAnimator;

    public PeerMeshView(Context context) {
        super(context);
        init();
    }

    public PeerMeshView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PeerMeshView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        centerPaint.setColor(ContextCompat.getColor(getContext(), R.color.yum_red));
        centerPaint.setStyle(Paint.Style.FILL);

        centerTextPaint.setColor(Color.WHITE);
        centerTextPaint.setTextAlign(Paint.Align.CENTER);
        centerTextPaint.setTextSize(28f);

        peerPaint.setStyle(Paint.Style.FILL);

        peerTextPaint.setColor(Color.WHITE);
        peerTextPaint.setTextAlign(Paint.Align.CENTER);
        peerTextPaint.setTextSize(22f);

        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.peer_line));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);

        pulsePaint.setColor(ContextCompat.getColor(getContext(), R.color.yum_red));
        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(4f);

        startPulseAnimation();
    }

    private void startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 2.5f);
        pulseAnimator.setDuration(2000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
        pulseAnimator.addUpdateListener(animation -> {
            pulseScale = (float) animation.getAnimatedValue();
            pulseAlpha = 1.0f - (pulseScale - 1.0f) / 1.5f;
            if (pulseAlpha < 0) pulseAlpha = 0;
            invalidate();
        });
        pulseAnimator.start();
    }

    public void setDeviceInfo(String label, String role) {
        this.deviceLabel = label;
        this.deviceRole = role;
        invalidate();
    }

    public void addPeer(String peerId, String label, int color) {
        if (!peerIds.contains(peerId)) {
            peerIds.add(peerId);
            peerLabels.put(peerId, label);
            peerColors.put(peerId, color);
            peerScales.put(peerId, 0f);

            // Animate the peer appearing
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(600);
            animator.setInterpolator(new OvershootInterpolator(1.5f));
            animator.addUpdateListener(a -> {
                peerScales.put(peerId, (float) a.getAnimatedValue());
                invalidate();
            });
            animator.start();
        }
    }

    public void removePeer(String peerId) {
        if (peerIds.contains(peerId)) {
            // Animate out
            ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
            animator.setDuration(400);
            animator.addUpdateListener(a -> {
                peerScales.put(peerId, (float) a.getAnimatedValue());
                if ((float) a.getAnimatedValue() <= 0) {
                    peerIds.remove(peerId);
                    peerScales.remove(peerId);
                    peerLabels.remove(peerId);
                    peerColors.remove(peerId);
                }
                invalidate();
            });
            animator.start();
        }
    }

    public void flashSyncLine(String peerId) {
        // Brief blue flash on the connection line
        post(() -> {
            linePaint.setColor(ContextCompat.getColor(getContext(), R.color.peer_line_active));
            linePaint.setStrokeWidth(6f);
            invalidate();
            postDelayed(() -> {
                linePaint.setColor(ContextCompat.getColor(getContext(), R.color.peer_line));
                linePaint.setStrokeWidth(3f);
                invalidate();
            }, 300);
        });
    }

    public void clearPeers() {
        peerIds.clear();
        peerScales.clear();
        peerLabels.clear();
        peerColors.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        centerX = getWidth() / 2f;
        centerY = getHeight() / 2f;
        float dp = getResources().getDisplayMetrics().density;
        centerRadius = 42 * dp;
        peerRadius = 30 * dp;
        centerTextPaint.setTextSize(14 * dp);
        peerTextPaint.setTextSize(11 * dp);

        float orbitRadius = Math.min(getWidth(), getHeight()) * 0.35f;

        // Draw pulse rings
        pulsePaint.setAlpha((int) (pulseAlpha * 100));
        canvas.drawCircle(centerX, centerY, centerRadius * pulseScale, pulsePaint);

        // Draw connection lines
        for (int i = 0; i < peerIds.size(); i++) {
            String peerId = peerIds.get(i);
            Float scale = peerScales.get(peerId);
            if (scale == null || scale <= 0) continue;

            double angle = 2 * Math.PI * i / Math.max(peerIds.size(), 1) - Math.PI / 2;
            float px = centerX + (float) (orbitRadius * Math.cos(angle));
            float py = centerY + (float) (orbitRadius * Math.sin(angle));

            canvas.drawLine(centerX, centerY, px, py, linePaint);
        }

        // Draw center node
        canvas.drawCircle(centerX, centerY, centerRadius, centerPaint);

        // Center icon (device emoji)
        canvas.drawText("\uD83D\uDCF1", centerX, centerY + 4 * dp, centerTextPaint);

        // Label below center
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.on_surface));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(11 * dp);
        canvas.drawText(deviceRole.isEmpty() ? deviceLabel : deviceRole,
                centerX, centerY + centerRadius + 16 * dp, labelPaint);

        // Draw peer nodes
        for (int i = 0; i < peerIds.size(); i++) {
            String peerId = peerIds.get(i);
            Float scale = peerScales.get(peerId);
            if (scale == null || scale <= 0) continue;

            double angle = 2 * Math.PI * i / Math.max(peerIds.size(), 1) - Math.PI / 2;
            float px = centerX + (float) (orbitRadius * Math.cos(angle));
            float py = centerY + (float) (orbitRadius * Math.sin(angle));

            Integer color = peerColors.get(peerId);
            peerPaint.setColor(color != null ? color : Color.GRAY);
            float r = peerRadius * scale;
            canvas.drawCircle(px, py, r, peerPaint);

            // Peer label
            String label = peerLabels.get(peerId);
            if (label != null && scale > 0.5f) {
                peerTextPaint.setAlpha((int) (255 * Math.min(1, (scale - 0.5f) * 2)));
                String shortLabel = label.length() > 8 ? label.substring(0, 8) : label;
                canvas.drawText(shortLabel, px, py + 4 * dp, peerTextPaint);

                // Label below peer
                labelPaint.setAlpha((int) (255 * Math.min(1, (scale - 0.5f) * 2)));
                canvas.drawText(label, px, py + r + 14 * dp, labelPaint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }
}
