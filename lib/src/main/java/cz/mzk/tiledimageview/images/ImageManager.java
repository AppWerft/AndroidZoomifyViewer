package cz.mzk.tiledimageview.images;

import android.graphics.Rect;

import java.util.List;

import cz.mzk.tiledimageview.TiledImageView;
import cz.mzk.tiledimageview.images.metadata.ImageMetadata;

/**
 * Created by Martin Řehánek on 3.12.15.
 */
public interface ImageManager {

    //TASK MANAGEMENT

    public void init(ImageMetadata imageMetadata);

    public void enqueueMetadataInitialization(TiledImageView.MetadataInitializationHandler handler, TiledImageView.MetadataInitializationSuccessListener successListener);

    public void enqueTileDownload(TilePositionInPyramid tilePositionInPyramid, TiledImageView.TileDownloadErrorListener errorListener, TiledImageView.TileDownloadSuccessListener successListener);

    public void cancelFetchingATilesForLayerExeptForThese(int layerId, List<TilePositionInPyramid> visibleTiles);

    public void cancelAllTasks();


    //STATE & IMAGE METADATA

    public boolean isInitialized();

    public String getImageBaseUrl();

    public int getImageWidth();

    public int getImageHeight();

    public int getTileTypicalSize();

    public TiledImageProtocol getTiledImageProtocol();


    //CORE - computations with tiles

    public int computeBestLayerId(Rect wholeImageInCanvasCoords);

    public List<TilePositionInPyramid> getVisibleTilesForLayer(int layerId, Rect visibleAreaInImageCoords);

    public Rect getTileAreaInImageCoords(TilePositionInPyramid tilePositionInPyramid);

    public String buildTileUrl(TilePositionInPyramid tilePositionInPyramid);


}
