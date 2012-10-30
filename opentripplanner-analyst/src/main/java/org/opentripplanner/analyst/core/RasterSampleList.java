package org.opentripplanner.analyst.core;

import java.util.Iterator;

import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.analyst.batch.SampleList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Produces samples from a regular grid of the given size in the given spatial reference system,
 * Samples are produced in row-major order.
 * 
 * This replicates the functionality of DynamicTile but without the overhead of storing 
 * all the Samples in an array -- they are generated one by one as needed.
 */
public class RasterSampleList implements SampleList {

    private static final Logger LOG = LoggerFactory.getLogger(RasterSampleList.class);
    
    final GridGeometry2D gg; // maps grid coordinates to CRS coordinates
    final CoordinateReferenceSystem crs;
    int width;
    int height;
    MathTransform tr = null;
    @Autowired SampleSource ss;

    public RasterSampleList (GridGeometry2D gg) {
        this.gg = gg;
        this.width = gg.getGridRange2D().width;
        this.height = gg.getGridRange2D().height;
        crs = gg.getCoordinateReferenceSystem2D();
        try {
            tr = CRS.findMathTransform(crs, DefaultGeographicCRS.WGS84);
        } catch (FactoryException e) {
            LOG.debug("could not find MathTransform.");
            e.printStackTrace();
        }
    }
    
    @Override
    public Iterator<Sample> iterator() {
        return new Iterator<Sample>() {

            GridCoordinates2D gc = new GridCoordinates2D(); // x and y within the raster grid
            
            @Override
            public boolean hasNext() {
                return (gc.y < height);
            }

            @Override
            public Sample next() {
                Sample s = null;
                try {
                    // find coordinates for current raster cell in tile CRS
                    DirectPosition sourcePos = gg.gridToWorld(gc);
                    // convert coordinates in tile CRS to WGS84
                    tr.transform(sourcePos, sourcePos);
                    // axis order can vary
                    double lon = sourcePos.getOrdinate(0);
                    double lat = sourcePos.getOrdinate(1);
                    s = ss.getSample(lon, lat);
                } catch (Exception ex) { // Transform exceptions
                    ex.printStackTrace();
                }
                // advance to the next grid cell
                gc.x += 1;
                if (gc.x >= width) {
                    gc.x = 0;
                    gc.y += 1; 
                }
                return s;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public int size() {
        return height * width;
    }
    
}
