package com.example.amap.util;
import com.example.amap.exception.BusinessException;
import com.example.amap.model.GridCell;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryFixer;
import java.util.*;
public final class GeometryUtils {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 0);
    private GeometryUtils() { }
    public static Geometry parsePolyline(String polyline) {
        if (polyline == null || polyline.isBlank()) throw new BusinessException("行政区 polyline 为空");
        try {
            List<Polygon> polygons = new ArrayList<>();
            for (String part : polyline.split("\\|", -1)) {
                if (part.isBlank()) throw new BusinessException("行政区 Polygon 为空");
                List<Coordinate> points = new ArrayList<>();
                for (String point : part.split(";", -1)) {
                    String[] xy = point.trim().split(",", -1);
                    if (xy.length != 2) throw new BusinessException("Polygon 坐标格式错误");
                    double x = Double.parseDouble(xy[0].trim()), y = Double.parseDouble(xy[1].trim());
                    if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 180 || Math.abs(y) > 90)
                        throw new BusinessException("Polygon 坐标超出范围");
                    Coordinate c = new Coordinate(x, y);
                    if (points.isEmpty() || !points.get(points.size() - 1).equals2D(c)) points.add(c);
                }
                if (points.size() < 3) throw new BusinessException("Polygon 坐标不足");
                if (!points.get(0).equals2D(points.get(points.size() - 1))) points.add(new Coordinate(points.get(0)));
                if (points.size() < 4) throw new BusinessException("Polygon 闭环坐标不足");
                Polygon p = FACTORY.createPolygon(points.toArray(Coordinate[]::new));
                if (p.getArea() <= 0) throw new BusinessException("Polygon 面积无效");
                polygons.add(p);
            }
            Geometry geometry = polygons.size() == 1 ? polygons.get(0) : FACTORY.createMultiPolygon(polygons.toArray(Polygon[]::new));
            if (!geometry.isValid()) geometry = GeometryFixer.fix(geometry);
            if (geometry.isEmpty() || !geometry.isValid() || !(geometry instanceof Polygon || geometry instanceof MultiPolygon))
                throw new BusinessException("行政区 Geometry 无效");
            return geometry;
        } catch (BusinessException e) { throw e; }
        catch (RuntimeException e) { throw new BusinessException("行政区 JTS Geometry 构建失败"); }
    }
    public static Polygon polygon(GridCell g) {
        return FACTORY.createPolygon(new Coordinate[] {
            new Coordinate(g.minLng(), g.minLat()), new Coordinate(g.maxLng(), g.minLat()),
            new Coordinate(g.maxLng(), g.maxLat()), new Coordinate(g.minLng(), g.maxLat()),
            new Coordinate(g.minLng(), g.minLat())});
    }
    /** 0: OUTSIDE, 1: BOUNDARY, 2: INSIDE. */
    public static int relationType(Geometry city, GridCell g) {
        Polygon p = polygon(g);
        if (!city.intersects(p)) return 0;
        return city.contains(p) ? 2 : 1;
    }
}
