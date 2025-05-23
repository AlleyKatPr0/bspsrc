/*
 ** 2013 June 28
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.bspsrc.decompiler.util;

import info.ata4.bspsrc.lib.app.SourceAppId;
import info.ata4.bspsrc.lib.struct.*;
import info.ata4.bspsrc.lib.vector.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory methods for winding objects.
 * 
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class WindingFactory {

    private final Map<DFace, Winding> faceCache = new HashMap<>();
    private final Map<Map.Entry<DBrush, DBrushSide>, Winding> brushSideCache = new HashMap<>();
    private final Map<DAreaportal, Winding> areaportalCache = new HashMap<>();
    private final Map<DOccluderPolyData, Winding> occluderCache = new HashMap<>();
    private final Map<DPlane, Winding> planeCache = new HashMap<>();

    public final int maxLen;
    public final int maxCoord;

    public WindingFactory(int coordSize) {
        maxCoord = coordSize;
        maxLen = (int)Math.ceil(Math.sqrt(3) * coordSize);
    }

    public static WindingFactory forAppId(int appId) {
        var coordSize = switch (appId) {
            case SourceAppId.STRATA_SOURCE -> 131072;
            default -> 32768;
        };
        return new WindingFactory(coordSize);
    }

    /**
     * Constructs a winding from face vertices
     *
     * @param bsp BSP data
     * @param face Face
     * @return Winding for the face
     */
    public Winding fromFace(BspData bsp, DFace face) {
        if (faceCache.containsKey(face)) {
            return faceCache.get(face);
        }

        List<Vector3f> verts = new ArrayList<>();

        for (int i = 0; i < face.numedge; i++) {
            int v;
            int sedge = bsp.surfEdges.get(face.fstedge + i);

            if (sedge < 0) {
                // backwards wound edge
                v = bsp.edges.get(-sedge).v[1];
            } else {
                // forwards wound edge
                v = bsp.edges.get(sedge).v[0];
            }

            verts.add(bsp.verts.get(v).point);
        }

        Winding w = new Winding(verts);

        faceCache.put(face, w);

        return w;
    }

    /**
     * Constructs a winding from a brush, for a brush side
     * 
     * Equals the brush side part of CreateBrushWindings() in brushbsp.cpp
     *
     * @param bsp BSP data
     * @param brush Brush
     * @param bside Brush side
     * @return Winding for the brush side
     */
    public Winding fromSide(BspData bsp, DBrush brush, DBrushSide bside) {
        var key = Map.entry(brush, bside);
        if (brushSideCache.containsKey(key)) {
            return brushSideCache.get(key);
        }

        int iplane = bside.pnum;
        boolean hasSide = false;

        Winding w = fromPlane(bsp.planes.get(iplane));

        // clip to all other planes
        for (int i = 0; i < brush.numside; i++) {
            int ibside2 = brush.fstside + i;
            DBrushSide bside2 = bsp.brushSides.get(ibside2);

            // don't clip plane to itself
            if (bside2 == bside) {
                hasSide = true;
                continue;
            }

            // don't clip to bevel planes
            if (bside2.bevel) {
                continue;
            }

            // remove everything behind the plane
            int iplane2 = bside2.pnum;
            DPlane plane = bsp.planes.get(iplane2);
            DPlane flipPlane = new DPlane();
            flipPlane.normal = plane.normal.scalar(-1);
            flipPlane.dist = -plane.dist;
            w = w.clipPlane(flipPlane, false);
        }

        if (!hasSide) {
            throw new IllegalArgumentException("Brush side is not part of brush!");
        }

        brushSideCache.put(key, w);

        // return the clipped winding
        return w;
    }

    /**
     * Constructs a winding from a brush, for a brush side
     * 
     * Equals the brush side part of CreateBrushWindings() in brushbsp.cpp
     *
     * @param bsp BSP data
     * @param brush Brush
     * @param side Brush side ID
     * @return Winding for the brush side
     */
    public Winding fromSide(BspData bsp, DBrush brush, int side) {
        int ibside = brush.fstside + side;
        DBrushSide bside = bsp.brushSides.get(ibside);
        return fromSide(bsp, brush, bside);
    }

    public Winding fromAreaportal(BspData bsp, DAreaportal ap) {
        if (areaportalCache.containsKey(ap)) {
            return areaportalCache.get(ap);
        }

        Winding w = bsp.clipPortalVerts.subList(ap.firstClipPortalVert, ap.firstClipPortalVert + ap.clipPortalVerts).stream()
                .map(dVertex -> dVertex.point)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Winding::new));

        areaportalCache.put(ap, w);

        return w;
    }

    /**
     * Constructs a winding from occluder vertices
     *
     * @param bsp BSP data
     * @param opd Occluder polygon data
     * @return Winding for the occluder
     */
    public Winding fromOccluder(BspData bsp, DOccluderPolyData opd) {
        if (occluderCache.containsKey(opd)) {
            return occluderCache.get(opd);
        }

        List<Vector3f> verts = new ArrayList<>();

        for (int k = 0; k < opd.vertexcount; k++) {
            int pvi = bsp.occluderVerts.get(opd.firstvertexindex + k);
            verts.add(bsp.verts.get(pvi).point);
        }

        Winding w = new Winding(verts);

        occluderCache.put(opd, w);

        return w;
    }

    /**
     * Constructs a huge square winding from a plane
     * 
     * Equals BaseWindingForPlane() in polylib.cpp
     * 
     * @param pl plane
     */
    public Winding fromPlane(DPlane pl) {
        if (planeCache.containsKey(pl)) {
            return planeCache.get(pl);
        }

        // find the dominant axis of plane normal
        float dmax = -1.0F;
        int idir = -1;

        // for each axis
        for (int i = 0; i < pl.normal.size; i++) {
            float dc = Math.abs(pl.normal.get(i));
            // find the biggest component
            if (dc <= dmax) {
                continue;
            }
            dmax = dc;
            idir = i;
        }

        // didn't find one (null or NaN'ed vector)
        if (idir == -1) {
            throw new RuntimeException("Plane " + pl + ": bad normal");
        }

        // this will be the "upwards" pointing vector
        Vector3f vup = Vector3f.NULL;

        switch (idir) {
            case Winding.SIDE_FRONT:
            case Winding.SIDE_BACK:
                // use z unit vector
                vup = new Vector3f(0, 0, 1);
                break;
            case Winding.SIDE_ON:
                // use x unit vector
                vup = new Vector3f(1, 0, 0);
        }

        // remove the component of this vector along the normal
        float vdot = vup.dot(pl.normal);
        vup = vup.add(pl.normal.scalar(-vdot));

        // make it a unit (perpendicular)
        vup = vup.normalize();

        // the vector from origin perpendicularly touching plane
        Vector3f org = pl.normal.scalar(pl.dist);

        // this is the "rightwards" pointing vector
        Vector3f vrt = vup.cross(pl.normal);

        vup = vup.scalar(maxLen);
        vrt = vrt.scalar(maxLen);

        List<Vector3f> verts = new ArrayList<>();

        // move diagonally away from org to create the corner verts
        verts.add(org.sub(vrt).add(vup)); // left up
        verts.add(org.add(vrt).add(vup)); // right up
        verts.add(org.add(vrt).sub(vup)); // right down
        verts.add(org.sub(vrt).sub(vup)); // left down

        Winding w = new Winding(verts);

        planeCache.put(pl, w);

        return w;
    }

    /**
     * Returns true if the winding still has one of the points
     * from basewinding for plane.
     * <p>
     * Equals WindingIsHuge() from brushbsp.cpp
     * <p>
     * This method should probably not be in this class, but it depends on the "context"
     * in which windings exist.
     * ({@link #maxCoord})
     *
     * @return true if winding is huge
     */
    public boolean isHuge(Winding winding) {
        for (Vector3f point : winding) {
            for (float value : point) {
                if (Math.abs(value) > maxCoord) {
                    return true;
                }
            }
        }

        return false;
    }
}
