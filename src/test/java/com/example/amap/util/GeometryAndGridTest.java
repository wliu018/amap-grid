package com.example.amap.util;
import com.example.amap.entity.AmapGrid;
import com.example.amap.config.AmapProperties;
import com.example.amap.model.*;
import com.example.amap.service.impl.AmapGridServiceImpl;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeometryAndGridTest {
    @Test void meterToLatitude() { assertEquals(0.00898311175, GeoUtils.meterToLatDegree(1000), 1e-11); }
    @Test void meterToLongitude() {
        assertEquals(1000.0 / (111320 * Math.cos(Math.toRadians(31))), GeoUtils.meterToLngDegree(1000, 31), 1e-15);
        assertTrue(GeoUtils.meterToLngDegree(1000, 60) > GeoUtils.meterToLngDegree(1000, 30));
        assertThrows(IllegalArgumentException.class, () -> GeoUtils.meterToLngDegree(1000, 90));
    }
    @Test void quartersCoverParentWithoutGapsOrAreaOverlap() {
        GridCell parent = new GridCell(121.1, 31.1, 121.3, 31.3, 1, 1);
        List<GridCell> quarters = GridUtils.splitFour(parent);
        assertEquals(4, quarters.size());
        List<Geometry> polygons = quarters.stream().map(g -> (Geometry)GeometryUtils.polygon(g)).toList();
        Geometry union = UnaryUnionOp.union(polygons);
        assertEquals(0, GeometryUtils.polygon(parent).symDifference(union).getArea(), 1e-14);
        for (int i = 0; i < polygons.size(); i++)
            for (int j = i + 1; j < polygons.size(); j++) assertEquals(0, polygons.get(i).intersection(polygons.get(j)).getArea(), 1e-15);
        assertEquals(quarters.get(0).maxLng(), quarters.get(1).minLng(), 0);
        assertEquals(quarters.get(0).minLat(), quarters.get(2).maxLat(), 0);
    }
    @Test void polygonAutomaticallyCloses() {
        Polygon p = (Polygon)GeometryUtils.parsePolyline("0,0;2,0;2,2;0,2");
        assertEquals(5, p.getExteriorRing().getNumPoints());
        assertEquals(p.getCoordinates()[0], p.getCoordinates()[4]);
        assertTrue(p.isValid());
    }
    @Test void multipartPolygonsRemainSeparate() {
        Geometry g = GeometryUtils.parsePolyline("0,0;1,0;1,1;0,1|3,3;4,3;4,4;3,4");
        assertInstanceOf(MultiPolygon.class, g); assertEquals(2, g.getNumGeometries());
        assertEquals(0, GeometryUtils.relationType(g, new GridCell(1.5, 1.5, 2.5, 2.5, 1, 1)));
    }
    @Test void boundaryAndInsideRelations() {
        Geometry city = GeometryUtils.parsePolyline("0,0;2,0;2,2;0,2");
        assertEquals(2, GeometryUtils.relationType(city, new GridCell(.1, .1, 1.9, 1.9, 1, 1)));
        assertEquals(1, GeometryUtils.relationType(city, new GridCell(1.5, 1.5, 2.5, 2.5, 1, 1)));
        assertEquals(0, GeometryUtils.relationType(city, new GridCell(3, 3, 4, 4, 1, 1)));
        assertTrue(city.intersects(GeometryUtils.polygon(new GridCell(2, 0, 3, 1, 1, 1))));
    }
    @Test void stableGridNumbersAndRandomOrder() {
        var service = new AmapGridServiceImpl(new AmapProperties());
        Geometry city = GeometryUtils.parsePolyline("121,31;121.02,31;121.02,31.02;121,31.02");
        List<AmapGrid> grids = service.generateCityGrids("310000", "050000", city, 500);
        var second = service.generateCityGrids("310000", "050000", city, 500);
        Map<String, AmapGrid> snapshot = new HashMap<>();
        for (AmapGrid grid : grids) snapshot.put(grid.getGridNo(), grid);
        assertEquals(grids.stream().map(AmapGrid::getGridNo).toList(), second.stream().map(AmapGrid::getGridNo).toList());
        assertEquals("310000_050000_R00001_C00002", GridUtils.stableGridNo("310000", "050000", 1, 2));
        service.randomizeOrder(grids);
        assertEquals(grids.size(), grids.stream().map(AmapGrid::getRandomOrder).distinct().count());
        assertEquals(1L, grids.stream().mapToLong(AmapGrid::getRandomOrder).min().orElseThrow());
        assertEquals(grids.size(), grids.stream().mapToLong(AmapGrid::getRandomOrder).max().orElseThrow());
        for (AmapGrid g : grids) {
            assertSame(snapshot.get(g.getGridNo()), g);
            assertEquals("WAITING", g.getStatus());
            assertEquals(500, g.getGridSizeMeter());
        }
    }
    @Test void coverUsesFullMeterCellsEvenAtBoundingBoxEdge() {
        Envelope box = new Envelope(121, 121.021, 31, 31.018);
        var grids = GridUtils.cover(box, 625);
        assertEquals(GeoUtils.meterToLatDegree(625), grids.get(0).maxLat() - grids.get(0).minLat(), 1e-13);
        Geometry union = UnaryUnionOp.union(grids.stream().map(g -> (Geometry)GeometryUtils.polygon(g)).toList());
        assertTrue(union.covers(GeometryUtils.polygon(new GridCell(121, 31, 121.021, 31.018, 1, 1))));
    }
    @Test void invalidPolylinesFailExplicitly() {
        for (String invalid : List.of("", "0,0;1,1", "0,0;1,0;1,1|", "NaN,0;1,0;1,1")) {
            assertThrows(RuntimeException.class, () -> GeometryUtils.parsePolyline(invalid));
        }
    }
}
