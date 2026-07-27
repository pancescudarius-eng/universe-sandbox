package com.cosmos.sandbox;

import android.graphics.Color;

public final class Body {
    public long id;
    public String name;
    public double x;
    public double y;
    public double vx;
    public double vy;
    public double mass;
    public double radius;
    public int color;
    public boolean fixed;
    public final float[] trailX = new float[120];
    public final float[] trailY = new float[120];
    public int trailCount;
    public int trailIndex;

    public Body(long id, String name, double x, double y, double vx, double vy,
                double mass, double radius, int color, boolean fixed) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.mass = mass;
        this.radius = radius;
        this.color = color;
        this.fixed = fixed;
    }

    public void pushTrail() {
        trailX[trailIndex] = (float) x;
        trailY[trailIndex] = (float) y;
        trailIndex = (trailIndex + 1) % trailX.length;
        if (trailCount < trailX.length) trailCount++;
    }

    public static int blendColor(int a, int b, double weightB) {
        double w = Math.max(0.0, Math.min(1.0, weightB));
        int r = (int) (Color.red(a) * (1.0 - w) + Color.red(b) * w);
        int g = (int) (Color.green(a) * (1.0 - w) + Color.green(b) * w);
        int bl = (int) (Color.blue(a) * (1.0 - w) + Color.blue(b) * w);
        return Color.rgb(r, g, bl);
    }
}
