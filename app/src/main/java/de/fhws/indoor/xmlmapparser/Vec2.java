package de.fhws.indoor.xmlmapparser;

public class Vec2 {
    public float x = 0;
    public float y = 0;

    public Vec2() {

    }

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vec2 add(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    public Vec2 sub(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    public Vec2 mul(float s) {
        return new Vec2(x * s, y * s);
    }

    public Vec2 normalized() {
        float length = (float) length();
        return this.mul(1.0f/length);
    }

    public double length() {
        return Math.sqrt(x*x + y*y);
    }

    public Vec2 getPerpendicular() {
        return new Vec2(-y, x);
    }

    public Vec2 rotated(double r) {
        float cr = (float) Math.cos(r);
        float sr = (float) Math.sin(r);
        return new Vec2(cr*x - sr*y, sr*x + cr*y);
    }
}
