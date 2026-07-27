package com.cosmos.sandbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class UniverseView extends View {
    public interface Listener {
        void onSelectionChanged(Body body);
        void onStatsChanged(String text);
    }

    private final SimulationEngine engine;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final float[] starsX = new float[260];
    private final float[] starsY = new float[260];
    private final float[] starsA = new float[260];
    private Listener listener;
    private long selectedId = -1;
    private boolean paused;
    private boolean trails = true;
    private double timeScale = 1.0;
    private float zoom = 0.86f;
    private float cameraX;
    private float cameraY;
    private long lastFrame;
    private long fpsWindow;
    private int fpsFrames;
    private int fps;
    private float downX;
    private float downY;
    private boolean dragging;

    public UniverseView(Context context, SimulationEngine engine) {
        super(context);
        this.engine = engine;
        setBackgroundColor(Color.rgb(5, 6, 10));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(13));
        Random random = new Random(7741);
        for (int i = 0; i < starsX.length; i++) {
            starsX[i] = random.nextFloat();
            starsY[i] = random.nextFloat();
            starsA[i] = 0.2f + random.nextFloat() * 0.8f;
        }
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float old = zoom;
                zoom = clamp(zoom * detector.getScaleFactor(), 0.08f, 6f);
                float focusX = detector.getFocusX() - getWidth() / 2f;
                float focusY = detector.getFocusY() - getHeight() / 2f;
                cameraX += focusX / old - focusX / zoom;
                cameraY += focusY / old - focusY / zoom;
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                Body body = selected();
                if (body != null) {
                    cameraX = (float) -body.x;
                    cameraY = (float) -body.y;
                    zoom = Math.max(0.35f, Math.min(2.2f, 70f / (float) body.radius));
                } else resetCamera();
                return true;
            }
        });
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean isPaused() { return paused; }
    public void setTrails(boolean enabled) { trails = enabled; }
    public boolean hasTrails() { return trails; }
    public void setTimeScale(double value) { timeScale = value; }
    public double getTimeScale() { return timeScale; }
    public long getSelectedId() { return selectedId; }
    public void clearSelection() { selectedId = -1; notifySelection(); }

    public void resetCamera() {
        cameraX = 0;
        cameraY = 0;
        zoom = 0.86f;
    }

    public void selectNewest() {
        List<Body> bodies = engine.snapshot();
        if (!bodies.isEmpty()) selectedId = bodies.get(bodies.size() - 1).id;
        notifySelection();
    }

    public double[] screenToWorld(float sx, float sy) {
        return new double[]{(sx - getWidth() / 2.0) / zoom - cameraX,
                (sy - getHeight() / 2.0) / zoom - cameraY};
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.elapsedRealtimeNanos();
        if (lastFrame == 0) lastFrame = now;
        double dt = (now - lastFrame) / 1_000_000_000.0;
        lastFrame = now;
        if (!paused) engine.step(dt, timeScale);
        updateFps(now);
        drawStars(canvas);
        List<Body> bodies = engine.snapshot();
        if (trails) drawTrails(canvas, bodies);
        for (Body body : bodies) drawBody(canvas, body);
        drawHud(canvas, bodies.size());
        if (listener != null && fpsFrames % 15 == 0) {
            listener.onStatsChanged(String.format(Locale.US, "%d bodies  •  %dx  •  %d FPS",
                    bodies.size(), Math.round(timeScale), fps));
        }
        postInvalidateOnAnimation();
    }

    private void drawStars(Canvas canvas) {
        paint.setShader(null);
        for (int i = 0; i < starsX.length; i++) {
            float x = starsX[i] * getWidth();
            float y = starsY[i] * getHeight();
            int alpha = (int) (starsA[i] * 210);
            paint.setColor(Color.argb(alpha, 210, 225, 255));
            canvas.drawCircle(x, y, starsA[i] > .72f ? 1.5f : 0.8f, paint);
        }
    }

    private void drawTrails(Canvas canvas, List<Body> bodies) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.25f);
        for (Body body : bodies) {
            if (body.trailCount < 2) continue;
            Path path = new Path();
            for (int k = 0; k < body.trailCount; k++) {
                int idx = (body.trailIndex - body.trailCount + k + body.trailX.length) % body.trailX.length;
                float sx = worldToScreenX(body.trailX[idx]);
                float sy = worldToScreenY(body.trailY[idx]);
                if (k == 0) path.moveTo(sx, sy); else path.lineTo(sx, sy);
            }
            paint.setColor((body.color & 0x00FFFFFF) | 0x55000000);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBody(Canvas canvas, Body body) {
        float sx = worldToScreenX(body.x);
        float sy = worldToScreenY(body.y);
        float r = Math.max(3.5f, (float) body.radius * zoom);
        if (sx + r < -40 || sx - r > getWidth() + 40 || sy + r < -40 || sy - r > getHeight() + 40) return;

        if (body.mass > 15000) {
            paint.setShader(new RadialGradient(sx, sy, r * 2.5f,
                    new int[]{withAlpha(body.color, 110), withAlpha(body.color, 20), Color.TRANSPARENT},
                    new float[]{0f, .38f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(sx, sy, r * 2.5f, paint);
            paint.setShader(null);
        }

        paint.setColor(body.color);
        canvas.drawCircle(sx, sy, r, paint);
        paint.setColor(withAlpha(Color.WHITE, 78));
        canvas.drawCircle(sx - r * .28f, sy - r * .3f, Math.max(1f, r * .18f), paint);

        if (body.id == selectedId) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(107, 203, 255));
            canvas.drawCircle(sx, sy, r + dp(7), paint);
            paint.setStyle(Paint.Style.FILL);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(body.name, sx, sy - r - dp(11), textPaint);
        }
    }

    private void drawHud(Canvas canvas, int count) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.argb(220, 225, 235, 255));
        canvas.drawText(paused ? "PAUSED" : "RUNNING", dp(14), dp(22), textPaint);
        textPaint.setColor(Color.argb(165, 210, 220, 245));
        canvas.drawText(String.format(Locale.US, "%d objects   %.2fx zoom", count, zoom), dp(14), dp(42), textPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); dragging = false; return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.hypot(dx, dy) > dp(3)) dragging = true;
                    cameraX += dx / zoom;
                    cameraY += dy / zoom;
                    downX = event.getX(); downY = event.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && !scaleDetector.isInProgress()) selectAt(event.getX(), event.getY());
                return true;
        }
        return true;
    }

    private void selectAt(float x, float y) {
        List<Body> bodies = engine.snapshot();
        Body best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Body body : bodies) {
            double dx = worldToScreenX(body.x) - x;
            double dy = worldToScreenY(body.y) - y;
            double d = Math.hypot(dx, dy);
            double tolerance = Math.max(dp(16), body.radius * zoom + dp(8));
            if (d < tolerance && d < bestDistance) { best = body; bestDistance = d; }
        }
        selectedId = best == null ? -1 : best.id;
        notifySelection();
    }

    private Body selected() { return selectedId < 0 ? null : engine.find(selectedId); }
    private void notifySelection() { if (listener != null) listener.onSelectionChanged(selected()); }
    private float worldToScreenX(double x) { return getWidth() / 2f + (float) (x + cameraX) * zoom; }
    private float worldToScreenY(double y) { return getHeight() / 2f + (float) (y + cameraY) * zoom; }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }

    private void updateFps(long nowNanos) {
        if (fpsWindow == 0) fpsWindow = nowNanos;
        fpsFrames++;
        if (nowNanos - fpsWindow >= 1_000_000_000L) {
            fps = fpsFrames;
            fpsFrames = 0;
            fpsWindow = nowNanos;
        }
    }
}
