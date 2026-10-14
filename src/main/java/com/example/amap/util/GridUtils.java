package com.example.amap.util;
import com.example.amap.model.GridCell;
import org.locationtech.jts.geom.Envelope;
import java.util.*;
public final class GridUtils {
    private GridUtils() { }
    public static List<GridCell> splitFour(GridCell g) {
        double x = (g.minLng() + g.maxLng()) / 2;
        double y = (g.minLat() + g.maxLat()) / 2;
        return List.of(
            new GridCell(g.minLng(), y, x, g.maxLat(), 0, 0),
            new GridCell(x, y, g.maxLng(), g.maxLat(), 0, 0),
            new GridCell(g.minLng(), g.minLat(), x, y, 0, 0),
            new GridCell(x, g.minLat(), g.maxLng(), y, 0, 0));
    }
    public static List<GridCell> cover(Envelope e, int meter) {
        if (e.isNull() || e.getWidth() <= 0 || e.getHeight() <= 0) throw new IllegalArgumentException("BoundingBox 无效");
        double latStep = GeoUtils.meterToLatDegree(meter);
        int rows = checkedCount(e.getHeight() / latStep);
        List<GridCell> result = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            double minLat = e.getMinY() + row * latStep;
            double maxLat = minLat + latStep;
            // 同一行使用相同经度步长，行与行可能有不同列数，但均完整覆盖 BoundingBox。
            double lngStep = GeoUtils.meterToLngDegree(meter, (minLat + maxLat) / 2);
            int cols = checkedCount(e.getWidth() / lngStep);
            for (int col = 0; col < cols; col++) {
                double minLng = e.getMinX() + col * lngStep;
                result.add(new GridCell(minLng, minLat, minLng + lngStep, maxLat, row + 1, col + 1));
            }
        }
        return result;
    }
    private static int checkedCount(double count) {
        if (!Double.isFinite(count) || count >= Integer.MAX_VALUE) throw new IllegalArgumentException("网格数量超出范围");
        return Math.max(1, (int)Math.ceil(count));
    }
    public static String stableGridNo(String region, String types, int row, int col) {
        return String.format(Locale.ROOT, "%s_%s_R%05d_C%05d", region, types, row, col);
    }
    public static String buildPolygon(GridCell g) {
        // Fastjson 与 HTTP 坐标均不做提前四舍五入。固定左下、右下、右上、左上。
        return g.minLng() + "," + g.minLat() + ";" + g.maxLng() + "," + g.minLat() + ";"
            + g.maxLng() + "," + g.maxLat() + ";" + g.minLng() + "," + g.maxLat();
    }
}
