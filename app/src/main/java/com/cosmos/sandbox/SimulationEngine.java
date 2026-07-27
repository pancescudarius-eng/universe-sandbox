package com.cosmos.sandbox;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SimulationEngine {
    // Tuned gameplay constant, not SI units. This keeps mobile-scale systems stable and fun.
    private static final double G = 0.045;
    private static final double SOFTENING = 18.0;
    private static final int MAX_BODIES = 180;

    private final List<Body> bodies = new ArrayList<>();
    private long nextId = 1;
    private double accumulator;
    private int trailTick;

    public synchronized List<Body> snapshot() {
        return new ArrayList<>(bodies);
    }

    public synchronized Body find(long id) {
        for (Body body : bodies) if (body.id == id) return body;
        return null;
    }

    public synchronized Body add(String name, double x, double y, double vx, double vy,
                                 double mass, double radius, int color, boolean fixed) {
        if (bodies.size() >= MAX_BODIES) return null;
        Body body = new Body(nextId++, name, x, y, vx, vy, mass, radius, color, fixed);
        bodies.add(body);
        return body;
    }

    public synchronized void remove(long id) {
        bodies.removeIf(body -> body.id == id);
    }

    public synchronized void clear() {
        bodies.clear();
        nextId = 1;
        accumulator = 0;
    }

    public synchronized void step(double realSeconds, double timeScale) {
        accumulator += Math.min(realSeconds, 0.05) * timeScale;
        final double fixedDt = 1.0 / 120.0;
        int guard = 0;
        while (accumulator >= fixedDt && guard++ < 20) {
            integrate(fixedDt);
            accumulator -= fixedDt;
        }
    }

    private void integrate(double dt) {
        int n = bodies.size();
        double[] ax = new double[n];
        double[] ay = new double[n];

        for (int i = 0; i < n; i++) {
            Body a = bodies.get(i);
            for (int j = i + 1; j < n; j++) {
                Body b = bodies.get(j);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double distSq = dx * dx + dy * dy + SOFTENING * SOFTENING;
                double invDist = 1.0 / Math.sqrt(distSq);
                double invDist3 = invDist * invDist * invDist;
                double factor = G * invDist3;
                if (!a.fixed) {
                    ax[i] += factor * b.mass * dx;
                    ay[i] += factor * b.mass * dy;
                }
                if (!b.fixed) {
                    ax[j] -= factor * a.mass * dx;
                    ay[j] -= factor * a.mass * dy;
                }
            }
        }

        for (int i = 0; i < n; i++) {
            Body b = bodies.get(i);
            if (!b.fixed) {
                b.vx += ax[i] * dt;
                b.vy += ay[i] * dt;
                b.x += b.vx * dt;
                b.y += b.vy * dt;
            }
        }

        mergeCollisions();
        if (++trailTick % 3 == 0) for (Body body : bodies) body.pushTrail();
    }

    private void mergeCollisions() {
        for (int i = 0; i < bodies.size(); i++) {
            Body a = bodies.get(i);
            for (int j = i + 1; j < bodies.size(); j++) {
                Body b = bodies.get(j);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double hit = Math.max(5.0, (a.radius + b.radius) * 0.72);
                if (dx * dx + dy * dy > hit * hit) continue;

                Body big = a.mass >= b.mass ? a : b;
                Body small = big == a ? b : a;
                double totalMass = big.mass + small.mass;
                if (!big.fixed) {
                    big.vx = (big.vx * big.mass + small.vx * small.mass) / totalMass;
                    big.vy = (big.vy * big.mass + small.vy * small.mass) / totalMass;
                    big.x = (big.x * big.mass + small.x * small.mass) / totalMass;
                    big.y = (big.y * big.mass + small.y * small.mass) / totalMass;
                }
                big.color = Body.blendColor(big.color, small.color, small.mass / totalMass);
                big.radius = Math.cbrt(Math.pow(big.radius, 3) + Math.pow(small.radius, 3));
                big.mass = totalMass;
                big.name = totalMass > 65000 ? "Merged Star" : "Merged World";
                bodies.remove(small);
                if (small == a) {
                    i--;
                    break;
                }
                j--;
            }
        }
    }

    public synchronized void loadSolarSystem() {
        clear();
        Body sun = add("Sol", 0, 0, 0, 0, 90000, 54, Color.rgb(255, 190, 70), true);
        addOrbiter("Mercury", sun, 145, 8, 45, Color.rgb(170, 160, 150));
        addOrbiter("Venus", sun, 205, 13, 115, Color.rgb(224, 174, 92));
        Body earth = addOrbiter("Earth", sun, 285, 15, 145, Color.rgb(56, 136, 245));
        addMoon("Moon", earth, 34, 5, 2.0, Color.LTGRAY);
        addOrbiter("Mars", sun, 390, 12, 85, Color.rgb(205, 86, 58));
        addOrbiter("Jupiter", sun, 600, 30, 1600, Color.rgb(210, 165, 120));
        addOrbiter("Saturn", sun, 790, 26, 1100, Color.rgb(222, 201, 145));
    }

    public synchronized void loadBinaryStars() {
        clear();
        add("Aster", -130, 0, 0, -2.8, 52000, 46, Color.rgb(255, 176, 78), false);
        add("Lyra", 130, 0, 0, 2.8, 52000, 46, Color.rgb(142, 190, 255), false);
        Body anchor = add("Outer world", 0, -520, 3.8, 0, 220, 16, Color.rgb(72, 220, 180), false);
        anchor.pushTrail();
    }

    public synchronized void loadChaos() {
        clear();
        add("Core", 0, 0, 0, 0, 68000, 48, Color.rgb(255, 120, 65), true);
        for (int i = 0; i < 42; i++) {
            double angle = i * 2.399963229728653;
            double distance = 140 + i * 12;
            double speed = Math.sqrt(G * 68000 / distance) * (0.78 + (i % 5) * 0.055);
            double x = Math.cos(angle) * distance;
            double y = Math.sin(angle) * distance;
            double vx = -Math.sin(angle) * speed;
            double vy = Math.cos(angle) * speed;
            int c = Color.HSVToColor(new float[]{(i * 29) % 360, 0.55f, 1f});
            add("Body " + (i + 1), x, y, vx, vy, 18 + (i % 7) * 12, 4 + (i % 4), c, false);
        }
    }

    private Body addOrbiter(String name, Body parent, double distance, double radius,
                            double mass, int color) {
        double speed = Math.sqrt(G * parent.mass / distance);
        return add(name, parent.x + distance, parent.y, parent.vx, parent.vy + speed,
                mass, radius, color, false);
    }

    private void addMoon(String name, Body planet, double distance, double radius,
                         double mass, int color) {
        double speed = Math.sqrt(G * planet.mass / distance);
        add(name, planet.x + distance, planet.y, planet.vx, planet.vy + speed,
                mass, radius, color, false);
    }

    public synchronized String serialize() {
        StringBuilder out = new StringBuilder();
        for (Body b : bodies) {
            if (out.length() > 0) out.append('\n');
            out.append(String.format(Locale.US,
                    "%d|%s|%.8f|%.8f|%.8f|%.8f|%.8f|%.8f|%d|%b",
                    b.id, b.name.replace("|", " "), b.x, b.y, b.vx, b.vy,
                    b.mass, b.radius, b.color, b.fixed));
        }
        return out.toString();
    }

    public synchronized boolean deserialize(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        List<Body> loaded = new ArrayList<>();
        long max = 0;
        try {
            for (String line : text.split("\\n")) {
                String[] p = line.split("\\|", -1);
                if (p.length != 10) return false;
                long id = Long.parseLong(p[0]);
                Body body = new Body(id, p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        Double.parseDouble(p[4]), Double.parseDouble(p[5]),
                        Double.parseDouble(p[6]), Double.parseDouble(p[7]),
                        Integer.parseInt(p[8]), Boolean.parseBoolean(p[9]));
                loaded.add(body);
                max = Math.max(max, id);
            }
        } catch (RuntimeException invalid) {
            return false;
        }
        bodies.clear();
        bodies.addAll(loaded);
        nextId = max + 1;
        return true;
    }

    public synchronized int size() {
        return bodies.size();
    }
}
