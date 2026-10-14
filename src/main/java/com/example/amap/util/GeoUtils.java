package com.example.amap.util;
public final class GeoUtils {
    private static final double METERS_PER_DEGREE = 111320.0;
    private GeoUtils() { }
    public static double meterToLatDegree(double meter) { requireMeter(meter); return meter / METERS_PER_DEGREE; }
    public static double meterToLngDegree(double meter, double latitude) {
        requireMeter(meter);
        if (!Double.isFinite(latitude) || Math.abs(latitude) >= 89) throw new IllegalArgumentException("纬度不适合经纬度网格换算");
        return meter / (METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude)));
    }
    private static void requireMeter(double meter) {
        if (!Double.isFinite(meter) || meter <= 0) throw new IllegalArgumentException("米数必须为正数");
    }
}
