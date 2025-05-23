/*
** 2011 April 5
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
*/

package info.ata4.bspsrc.decompiler.modules.geom;

import info.ata4.bspsrc.decompiler.BspSourceConfig;
import info.ata4.bspsrc.decompiler.VmfWriter;
import info.ata4.bspsrc.decompiler.modules.BspDecompiler;
import info.ata4.bspsrc.decompiler.modules.BspProtection;
import info.ata4.bspsrc.decompiler.modules.ModuleDecompile;
import info.ata4.bspsrc.decompiler.modules.VmfMeta;
import info.ata4.bspsrc.decompiler.modules.texture.Texture;
import info.ata4.bspsrc.decompiler.modules.texture.TextureBuilder;
import info.ata4.bspsrc.decompiler.modules.texture.TextureSource;
import info.ata4.bspsrc.decompiler.util.BspTreeStats;
import info.ata4.bspsrc.decompiler.util.OccluderMapper;
import info.ata4.bspsrc.decompiler.util.Winding;
import info.ata4.bspsrc.decompiler.util.WindingFactory;
import info.ata4.bspsrc.lib.BspFileReader;
import info.ata4.bspsrc.lib.struct.DBrush;
import info.ata4.bspsrc.lib.struct.DBrushSide;
import info.ata4.bspsrc.lib.struct.DModel;
import info.ata4.bspsrc.lib.vector.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Decompiling module to rebuild brushes from the LUMP_BRUSHES and LUMP_BRUSHSIDES lumps.
 *
 * Based on Vmex.vmfbrushes() and Vmex.writebrush()
 * 
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class BrushSource extends ModuleDecompile {

    // logger
    private static final Logger L = LogManager.getLogger();

    private final WindingFactory windingFactory;
    private final OccluderMapper.ReallocationData occReallocationData;

    // sub-modules
    private final BspSourceConfig config;
    private final TextureSource texsrc;
    private final BspProtection bspprot;
    private final VmfMeta vmfmeta;
    private final BrushSideFaceMapper brushSideFaceMapper;

    // additional model data
    private List<DBrushModel> models = new ArrayList<>();

    // amount of world brushes
    private int worldbrushes = 0;

    // brush side ID mappings
    private Map<Integer, Integer> brushSideToID = new HashMap<>();
    private Map<Integer, Integer> brushIndexToID = new HashMap<>();

    public BrushSource(
            BspFileReader reader, VmfWriter writer,
            BspSourceConfig config,
            TextureSource texsrc,
            BspProtection bspprot,
            VmfMeta vmfmeta,
            BrushSideFaceMapper brushSideFaceMapper,
            WindingFactory windingFactory,
            OccluderMapper.ReallocationData occReallocationData
    ) {
        super(reader, writer);

        this.config = requireNonNull(config);
        this.texsrc = requireNonNull(texsrc);
        this.bspprot = requireNonNull(bspprot);
        this.vmfmeta = requireNonNull(vmfmeta);
        this.brushSideFaceMapper = requireNonNull(brushSideFaceMapper);
        this.windingFactory = requireNonNull(windingFactory);
        this.occReallocationData = requireNonNull(occReallocationData);

        assignBrushes();
    }

    /**
     * @return {@code true}, if the specified brush was a func_detail entity
     */
    public boolean isFuncDetail(DBrush dBrush) {
        boolean potentialNonObjectBrushLadderDetail = BspDecompiler.usesNonObjectBrushLadders(bspFile.getAppId()) && dBrush.isLadder();
        return (potentialNonObjectBrushLadderDetail || dBrush.isSolid() || dBrush.isTranslucent()) && dBrush.isDetail();
    }

    /**
     * Returns the brush side VMF ID for the corresponding brush side index.
     * The brush side must have been previously written via
     * {@link #writeSide writeSide}.
     * 
     * @param ibrushside brush side index
     * @return brush side ID or -1 if the index isn't mapped yet
     */
    public int getBrushSideIDForIndex(int ibrushside) {
        if (brushSideToID.containsKey(ibrushside)) {
            return brushSideToID.get(ibrushside);
        }

        // not found
        return -1;
    }

    /**
     * Returns the brush VMF ID for the corresponding brush index.
     * The brush must have been previously written via
     * {@link #writeBrush writeBrush}.
     * 
     * @param ibrush brush index
     * @return brush ID or -1 if the index isn't mapped yet
     */
    public int getBrushIDForIndex(int ibrush) {
        if (brushIndexToID.containsKey(ibrush)) {
            return brushIndexToID.get(ibrush);
        }

        // not found
        return -1;
    }

    /**
     * Walks the map's BSP tree to associate brushes with entities and to find
     * the index of the last worldbrush.
     */
    private void assignBrushes() {
        // walk the BSP tree
        // to from the headnode of each model
        // to calculate the minimum and maximum brush in the tree
        // much simpler than the guessing method
        // plus this recovers null-faced brushes

        BspTreeStats tl = new BspTreeStats(bsp);

        // walk model 0 (worldspawn model)
        tl.walk(0);

        L.debug("Walked worldspawn tree");

        worldbrushes = tl.getMaxBrushLeaf() + 1;

        for (DModel model : bsp.models) {
            tl.reset();
            tl.walk(model.headnode);

            DBrushModel bmodel = new DBrushModel();
            bmodel.fstbrush = tl.getMinBrushLeaf();
            bmodel.numbrush = tl.getMaxBrushLeaf() - tl.getMinBrushLeaf() + 1;
            models.add(bmodel);
        }

        L.debug("Largest worldbrush: {}", worldbrushes);
    }

    /**
     * Writes all world brushes. Depending on the settings, some brushes may be
     * skipped so the entity decompiler can use them.
     */
    public void writeBrushes() {
        L.info("Writing brushes and planes");

        for (int i = 0; i < worldbrushes; i++) {
            DBrush brush = bsp.brushes.get(i);

            // skip details
            if (config.writeDetails && isFuncDetail(brush)) {
                continue;
            }

            // skip areaportals
            if (config.writeAreaportals && brush.isAreaportal()) {
                continue;
            }

            // only skip ladders if game uses object brush based ladders
            // see https://developer.valvesoftware.com/wiki/Working_Ladders
            if (config.writeLadders && brush.isLadder()
                    && !BspDecompiler.usesNonObjectBrushLadders(bspFile.getAppId())) {
                continue;
            }

            // NOTE: occluder brushes aren't worldbrushes, so they don't need to
            // be handled here

            writeBrush(i);
        }
    }

    public boolean writeBrush(int ibrush, Vector3f origin, Vector3f angles) {
        DBrush brush = bsp.brushes.get(ibrush);

        int brushID = vmfmeta.getUID();

        // map brush index to ID
        brushIndexToID.put(ibrush, brushID);

        Map<Integer, Winding> validBrushSides = new HashMap<>();

        // check and preprocess the brush sides before writing the brush
        for (int i = 0; i < brush.numside; i++) {
            int ibrushside = brush.fstside + i;
            DBrushSide brushSide = bsp.brushSides.get(ibrushside);

            // don't output surplus bevel faces - they lead to bad brushes
            if (brushSide.bevel) {
                continue;
            }

            try {
                Winding wind = windingFactory.fromSide(bsp, brush, brushSide).removeDegenerated();

                // skip sides with no vertices
                if (wind.isEmpty()) {
                    throw new BrushSideException("no vertices");
                }

                // skip sides with too few vertices
                if (wind.size() < 3) {
                    throw new BrushSideException("less than 3 vertices");
                }

                // skip sides that are way too big
                if (windingFactory.isHuge(wind)) {
                    throw new BrushSideException("too big");
                }

                Vector3f[] plane = wind.buildPlane();

                Vector3f e1 = plane[0];
                Vector3f e2 = plane[1];
                Vector3f e3 = plane[2];

                if (!e1.isValid() || !e2.isValid() || !e3.isValid()) {
                    throw new BrushSideException("invalid plane");
                }

                // Check for duplicate plane points. All three plane points must
                // be unique or it isn't a valid plane.
                for (int p1 = 0; p1 < plane.length; p1++) {
                    for (int p2 = 0; p2 < plane.length; p2++) {
                        if (p1 == p2) {
                            continue;
                        }

                        Vector3f v1 = plane[p1];
                        Vector3f v2 = plane[p2];

                        if (v1.equals(v2)) {
                            throw new BrushSideException("duplicate plane point " + v1);
                        }
                    }
                }

                // rotate
                if (angles != null) {
                    wind = wind.rotate(angles);
                }

                // translate to origin
                if (origin != null) {
                    wind = wind.translate(origin);
                }

                // the brush side should be safe to write
                validBrushSides.put(ibrushside, wind);
            } catch (BrushSideException ex) {
	            if (config.debug) {
                    L.warn("Skipped side {} of brush {}: {}", i, ibrush, ex.getMessage());

                }
            }
        }

        // all brush sides invalid = invalid brush
        if (validBrushSides.isEmpty()) {
            L.warn("Skipped empty brush {}", ibrush);
            return false;
        } 

        // skip brushes with less than three sides, they can't be compiled and
        // may crash older Hammer builds
        if (validBrushSides.size() < 3) {
            L.warn("Skipped brush {} with less than 3 sides", ibrush);
            return false;
        }

        // now write the brush
        writer.start("solid");
        writer.put("id", brushID);

        // write metadata for debugging
	    if (config.debug) {
            writer.start("bspsrc_debug");
            writer.put("brush_index", ibrush);
            writer.put("brush_contents", brush.contents.toString());
            writer.end("bspsrc_debug");
        }

        // write valid sides only
        for (Map.Entry<Integer, Winding> entry : validBrushSides.entrySet()) {
            int ibrushside = entry.getKey();
            Winding wind = entry.getValue();
            writeSide(ibrushside, ibrush, wind, origin, angles);
        }

        // add visgroup metadata if this is a protector detail brush
        if (!brush.isDetail() && bspprot.isProtectedBrush(brush)) {
            vmfmeta.writeMetaVisgroup("VMEX protector brushes");
        }

        writer.end("solid");

        return true;
    }

    public boolean writeBrush(int ibrush) {
        return writeBrush(ibrush, null, null);
    }

    private boolean writeSide(int ibrushside, int ibrush, Winding wind, Vector3f origin, Vector3f angles) {
        DBrushSide brushSide = bsp.brushSides.get(ibrushside);

        // calculate plane vectors
        Vector3f[] plane = wind.buildPlane();

        Vector3f e1 = plane[0];
        Vector3f e2 = plane[1];
        Vector3f e3 = plane[2];

        // calculate plane normal
        // NOTE: the plane normal from the BSP could be invalid if the brush was
        //       rotated! better re-calculate it every time.
        Vector3f ev12 = e2.sub(e1);
        Vector3f ev13 = e3.sub(e1);
        Vector3f normal = ev12.cross(ev13).normalize();

        // build texture
        var tb = new TextureBuilder(bsp, texsrc, occReallocationData);

        tb.setOrigin(origin);
        tb.setAngles(angles);
        tb.setNormal(normal);

        tb.setTexinfoIndex(brushSide.texinfo);
        tb.setBrushIndex(ibrush);
        tb.setBrushSideIndex(ibrushside);

        boolean potentialCompactedTexinf = !brushSideFaceMapper.getOrigFaceIndex(ibrushside).isPresent();
        tb.setEnableTextureFixing(potentialCompactedTexinf);

        Texture texture = tb.build();

        // set custom face texture string
        if (!config.faceTexture.isEmpty()) {
            texture.setOverrideTexture(config.faceTexture);
        }

        int sideID = vmfmeta.getUID();

        // add side id to cubemap side list
        if (texture.getData() != null) {
            texsrc.addBrushSideID(texture.getData().texname, sideID);
        }

        // map brush side index to brush side ID
        brushSideToID.put(ibrushside, sideID);

        int smoothingGroups = brushSideFaceMapper.getOrigFaceIndex(ibrushside)
                .map(bsp.origFaces::get)
                .map(dFace -> dFace.smoothingGroups)
                .orElse(0);

        writer.start("side");
        writer.put("id", sideID);

        // write metadata for debugging
	    if (config.debug) {
            writer.start("bspsrc_debug");
            writer.put("brushside_index", ibrushside);
            writer.put("normal", normal);
            writer.put("winding", wind.toString());

            if (texture.getOverrideTexture() != null) {
                writer.put("original_material", texture.getOriginalTexture());
            }

            if (brushSide.texinfo != -1) {
                writer.put("texinfo_index", brushSide.texinfo);
                writer.put("texinfo_flags", bsp.texinfos.get(brushSide.texinfo).flags.toString());
                float[][] tvec = bsp.texinfos.get(brushSide.texinfo).textureVecsTexels;
                writer.put("texturevecs_u", Arrays.toString(tvec[0]));
                writer.put("texturevecs_v", Arrays.toString(tvec[1]));
                Vector3f uaxis = new Vector3f(tvec[0]);
                Vector3f vaxis = new Vector3f(tvec[1]);
                Vector3f texNorm = uaxis.cross(vaxis);
                double angle = Math.toDegrees(Math.acos(normal.dot(texNorm) / texNorm.length()));
                writer.put("input_uv_normal", texNorm);
                writer.put("input_uv_angle", Double.isNaN(angle) ? 0 : angle);
                texNorm = texture.getUAxis().axis.cross(texture.getVAxis().axis);
                angle = Math.toDegrees(Math.acos(normal.dot(texNorm)));
                writer.put("output_uv_normal", texNorm);
                writer.put("output_uv_angle", Double.isNaN(angle) ? 0 : angle);
            }
            writer.end("bspsrc_debug");
        }

        writer.put("plane", e1, e2, e3);
        writer.put("smoothing_groups", smoothingGroups);
        writer.put(texture);

        writer.end("side");

        return true;
    }

    public boolean writeModel(int imodel, Vector3f origin, Vector3f angles) {
        DBrushModel bmodel;

        try {
            bmodel = models.get(imodel);
        } catch (IndexOutOfBoundsException ex) {
            L.warn("Invalid model index {}", imodel);
            return false;
        }

        for (int i = 0; i < bmodel.numbrush; i++) {
            writeBrush(bmodel.fstbrush + i, origin, angles);
        }

        return true;
    }

    public boolean writeModel(int imodel) {
        return writeModel(imodel);
    }

    public int getWorldbrushes() {
        return worldbrushes;
    }

    private static class DBrushModel {
        private int fstbrush;
        private int numbrush;
    }

    private static class BrushSideException extends Exception {
        BrushSideException(String message) {
            super(message);
        }
    }
}
