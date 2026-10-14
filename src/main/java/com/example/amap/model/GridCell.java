package com.example.amap.model;
public record GridCell(double minLng, double minLat, double maxLng, double maxLat, int rowIndex, int colIndex) {
    public GridCell {
        if (!Double.isFinite(minLng) || !Double.isFinite(minLat) || !Double.isFinite(maxLng) || !Double.isFinite(maxLat)
            || minLng >= maxLng || minLat >= maxLat || minLng < -180 || maxLng > 180 || minLat < -90 || maxLat > 90) {
            throw new IllegalArgumentException("网格坐标无效");
        }
    }
    public GeoPoint center() { return new GeoPoint((minLng + maxLng) / 2, (minLat + maxLat) / 2); }
}
