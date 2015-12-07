package cz.mzk.androidzoomifyviewer.tiles.zoomify;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import cz.mzk.androidzoomifyviewer.CacheManager;
import cz.mzk.androidzoomifyviewer.Logger;
import cz.mzk.androidzoomifyviewer.cache.ImagePropertiesCache;
import cz.mzk.androidzoomifyviewer.tiles.ImageManager;
import cz.mzk.androidzoomifyviewer.tiles.MetadataInitializationHandler;
import cz.mzk.androidzoomifyviewer.tiles.TileDimensionsInImage;
import cz.mzk.androidzoomifyviewer.tiles.TileDownloadHandler;
import cz.mzk.androidzoomifyviewer.tiles.TilePositionInPyramid;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.ImageServerResponseException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.InvalidDataException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.OtherIOException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.TooManyRedirectionsException;
import cz.mzk.androidzoomifyviewer.viewer.Point;
import cz.mzk.androidzoomifyviewer.viewer.RectD;
import cz.mzk.androidzoomifyviewer.viewer.Utils;

/**
 * This class encapsulates image metadata from ImageProperties.xml and provides method for downloading tiles (bitmaps) for given
 * image.
 *
 * @author Martin Řehánek
 */
public class ZoomifyImageManager implements ImageManager {

    /**
     * @link https://github.com/moravianlibrary/AndroidZoomifyViewer/issues/25
     */
    public static final boolean COMPUTE_NUMBER_OF_LAYERS_ROUND_CALCULATION = true;
    public static final int MAX_REDIRECTIONS = 5;
    public static final int IMAGE_PROPERTIES_TIMEOUT = 3000;
    public static final int TILES_TIMEOUT = 5000;

    private static final Logger logger = new Logger(ZoomifyImageManager.class);

    private final DownloadAndSaveTileTasksRegistry taskRegistry = new DownloadAndSaveTileTasksRegistry(this);

    private final String mBaseUrl;
    private final double mPxRatio;

    private String mImagePropertiesUrl;
    private boolean initialized = false;
    private ImageProperties imageProperties;
    private List<Layer> layers;


    /**
     * @param baseUrl Zoomify base url.
     * @param pxRatio Ratio between pixels and density-independent pixels for computing image_size_in_canvas. Must be between 0 and 1.
     *                dpRatio = (1-pxRatio)
     */
    public ZoomifyImageManager(String baseUrl, double pxRatio) {
        if (pxRatio < 0 || pxRatio > 1) {
            throw new IllegalArgumentException("pxRation not in <0;1> interval");
        } else {
            mPxRatio = pxRatio;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("baseUrl is null or empty");
        } else {
            mBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + '/';
        }
        mImagePropertiesUrl = mBaseUrl + "ImageProperties.xml";
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("not initialized (" + mBaseUrl + ")");
        }
    }

    @Override
    public int getImageWidth() {
        checkInitialized();
        return imageProperties.getWidth();
    }

    @Override
    public int getImageHeight() {
        checkInitialized();
        return imageProperties.getHeight();
    }

    @Override
    public int getTileTypicalSize() {
        checkInitialized();
        return imageProperties.getTileSize();
    }

    /**
     * Initializes ZoomifyImageManager by downloading and processing ImageProperties.xml. Instead of downloading, ImageProperties.xml
     * may be loaded from cache. Also ImageProperties.xml is saved to cache after being downloaded.
     *
     * @throws IllegalStateException        If this method had already been called
     * @throws TooManyRedirectionsException If max. number of redirections exceeded before downloading ImageProperties.xml. This probably means redirection
     *                                      loop.
     * @throws ImageServerResponseException If zoomify server response code for ImageProperties.xml cannot be handled here (everything apart from OK and
     *                                      3xx redirections).
     * @throws InvalidDataException         If ImageProperties.xml contains invalid data - empty content, not well formed xml, missing required attributes,
     *                                      etc.
     * @throws OtherIOException             In case of other error (invalid URL, error transfering data, ...)
     */
    @Override
    public void initImageMetadata() throws OtherIOException, TooManyRedirectionsException, ImageServerResponseException,
            InvalidDataException {
        if (initialized) {
            throw new IllegalStateException("already initialized (" + mImagePropertiesUrl + ")");
        } else {
            logger.d("initImageMetadata: " + mImagePropertiesUrl);
        }
        HttpURLConnection.setFollowRedirects(false);
        String propertiesXml = fetchImagePropertiesXml();
        imageProperties = ImagePropertiesParser.parse(propertiesXml, mImagePropertiesUrl);
        logger.d(imageProperties.toString());
        layers = initLayers();
        initialized = true;
    }

    @Override
    public void initImageMetadataAsync(MetadataInitializationHandler handler) {
        // TODO: 6.12.15 Uchovavat task a pripadne zabit v onDestroy
        new InitTilesDownloaderTask(this, handler).executeConcurrentIfPossible();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    private String fetchImagePropertiesXml() throws OtherIOException, TooManyRedirectionsException,
            ImageServerResponseException {
        ImagePropertiesCache cache = CacheManager.getImagePropertiesCache();
        String fromCache = cache.getXml(mBaseUrl);
        if (fromCache != null) {
            return fromCache;
        } else {
            String downloaded = downloadImagePropertiesXml(mImagePropertiesUrl, MAX_REDIRECTIONS);
            cache.storeXml(downloaded, mBaseUrl);
            return downloaded;
        }
    }

    private String downloadImagePropertiesXml(String urlString, int remainingRedirections)
            throws TooManyRedirectionsException, ImageServerResponseException, OtherIOException {
        logger.d("downloading metadata from " + urlString);
        if (remainingRedirections == 0) {
            throw new TooManyRedirectionsException(urlString, MAX_REDIRECTIONS);
        }
        HttpURLConnection urlConnection = null;
        // logger.d( urlString + " remaining redirections: " +
        // remainingRedirections);
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(IMAGE_PROPERTIES_TIMEOUT);
            int responseCode = urlConnection.getResponseCode();
            // logger.d( "http code: " + responseCode);
            String location = urlConnection.getHeaderField("Location");
            switch (responseCode) {
                case 200:
                    return stringFromUrlConnection(urlConnection);
                case 300:
                    if (location == null || location.isEmpty()) {
                        throw new ImageServerResponseException(urlString, responseCode);
                    }
                    urlConnection.disconnect();
                    return downloadImagePropertiesXml(location, remainingRedirections - 1);
                case 301:
                    if (location == null || location.isEmpty()) {
                        throw new ImageServerResponseException(urlString, responseCode);
                    }
                    mImagePropertiesUrl = location;
                    urlConnection.disconnect();
                    return downloadImagePropertiesXml(location, remainingRedirections - 1);
                case 302:
                case 303:
                case 305:
                case 307:
                    if (location == null || location.isEmpty()) {
                        throw new ImageServerResponseException(urlString, responseCode);
                    }
                    urlConnection.disconnect();
                    return downloadImagePropertiesXml(location, remainingRedirections - 1);
                default:
                    throw new ImageServerResponseException(urlString, responseCode);
            }
        } catch (IOException e) {
            throw new OtherIOException(e.getMessage(), mImagePropertiesUrl);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private String stringFromUrlConnection(HttpURLConnection urlConnection) throws IOException {
        InputStream in = null;
        ByteArrayOutputStream out = null;
        try {
            in = new BufferedInputStream(urlConnection.getInputStream());
            byte[] buffer = new byte[8 * 1024];
            out = new ByteArrayOutputStream();
            int readBytes = 0;
            while ((readBytes = in.read(buffer)) != -1) {
                out.write(buffer, 0, readBytes);
            }
            return out.toString();
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private List<Layer> initLayers() {
        int numberOfLayers = computeNumberOfLayers();
        // logger.d( "layers #: " + numberOfLayers);
        List<Layer> result = new ArrayList<Layer>(numberOfLayers);
        double width = imageProperties.getWidth();
        double height = imageProperties.getHeight();
        double tileSize = imageProperties.getTileSize();
        for (int layer = 0; layer < numberOfLayers; layer++) {
            double powerOf2 = Utils.pow(2, numberOfLayers - layer - 1);
            int tilesHorizontal = (int) Math.ceil(Math.floor(width / powerOf2) / tileSize);
            int tilesVertical = (int) Math.ceil(Math.floor(height / powerOf2) / tileSize);
            result.add(new Layer(tilesVertical, tilesHorizontal));
        }
        return result;
    }

    private int computeNumberOfLayers() {
        float tilesInLayer = -1f;
        int tilesInLayerInt = -1;
        float maxDimension = Math.max(imageProperties.getWidth(), imageProperties.getHeight());
        float tileSize = imageProperties.getTileSize();
        int i = 0;
        do {
            tilesInLayer = (maxDimension / (tileSize * Utils.pow(2, i)));
            i++;
            tilesInLayerInt = (int) Math.ceil(COMPUTE_NUMBER_OF_LAYERS_ROUND_CALCULATION ? Utils.round(tilesInLayer, 3)
                    : tilesInLayer);
        } while (tilesInLayerInt != 1);
        return i;
    }

    //@Override
    /*private DownloadAndSaveTileTasksRegistry getTaskRegistry() {
        checkInitialized();
        return taskRegistry;
    }*/

    /**
     * Downloads tile from zoomify server. TODO: InvalidDataException
     *
     * @param tilePositionInPyramid Tile id.
     * @return
     * @throws IllegalStateException        If methodi initImageMetadata had not been called yet.
     * @throws TooManyRedirectionsException If max. number of redirections exceeded before downloading tile. This probably means redirection loop.
     * @throws ImageServerResponseException If zoomify server response code for tile cannot be handled here (everything apart from OK and 3xx
     *                                      redirections).
     * @throws InvalidDataException         If tile contains invalid data.
     * @throws OtherIOException             In case of other IO error (invalid URL, error transfering data, ...)
     */
    @Override
    public Bitmap downloadTile(TilePositionInPyramid tilePositionInPyramid) throws OtherIOException, TooManyRedirectionsException,
            ImageServerResponseException {
        checkInitialized();
        int tileGroup = computeTileGroup(tilePositionInPyramid);
        String tileUrl = buildTileUrl(tileGroup, tilePositionInPyramid);
        logger.v("TILE URL: " + tileUrl);
        return downloadTile(tileUrl, MAX_REDIRECTIONS);
    }

    @Override
    public List<TilePositionInPyramid> getVisibleTilesForLayer(int layerId, RectD visibleAreaInImageCoords) {
        TilePositionInPyramid.TilePositionInLayer[] corners = getCornerVisibleTilesCoords(layerId, visibleAreaInImageCoords);
        TilePositionInPyramid.TilePositionInLayer topLeftVisibleTilePositionInLayer = corners[0];
        TilePositionInPyramid.TilePositionInLayer bottomRightVisibleTilePositionInLayer = corners[1];

        List<TilePositionInPyramid> visibleTiles = new ArrayList<>();
        for (int y = topLeftVisibleTilePositionInLayer.row; y <= bottomRightVisibleTilePositionInLayer.row; y++) {
            for (int x = topLeftVisibleTilePositionInLayer.column; x <= bottomRightVisibleTilePositionInLayer.column; x++) {
                visibleTiles.add(new TilePositionInPyramid(layerId, x, y));
            }
        }
        return visibleTiles;

    }

    private TilePositionInPyramid.TilePositionInLayer[] getCornerVisibleTilesCoords(int layerId, RectD visibleAreaInImageCoords) {
        int imageWidthMinusOne = imageProperties.getWidth() - 1;
        int imageHeightMinusOne = imageProperties.getHeight() - 1;

        int topLeftVisibleX = Utils.collapseToInterval((int) visibleAreaInImageCoords.left, 0, imageWidthMinusOne);
        int topLeftVisibleY = Utils.collapseToInterval((int) visibleAreaInImageCoords.top, 0, imageHeightMinusOne);
        int bottomRightVisibleX = Utils.collapseToInterval((int) visibleAreaInImageCoords.right, 0, imageWidthMinusOne);
        int bottomRightVisibleY = Utils.collapseToInterval((int) visibleAreaInImageCoords.bottom, 0, imageHeightMinusOne);
        Point topLeftVisibleInImageCoords = new Point(topLeftVisibleX, topLeftVisibleY);
        Point bottomRightVisibleInImageCoords = new Point(bottomRightVisibleX, bottomRightVisibleY);

        // TestTags.TILES.d( "top left: [" + topLeftVisibleX + "," + topLeftVisibleY + "]");
        // TestTags.TILES.d( "bottom right: [" + bottomRightVisibleX + "," + bottomRightVisibleY + "]");
        // TODO: 4.12.15 udelat private metodu calculateTileCoordsFromPointInImageCoords, pokud se vola jen odsud
        TilePositionInPyramid.TilePositionInLayer topLeftVisibleTile = calculateTileCoordsFromPointInImageCoords(layerId, topLeftVisibleInImageCoords);
        TilePositionInPyramid.TilePositionInLayer bottomRightVisibleTile = calculateTileCoordsFromPointInImageCoords(layerId, bottomRightVisibleInImageCoords);
        // TestTags.TILES.d( "top_left:     " + Utils.toString(topLeftVisibleTileCoords));
        // TestTags.TILES.d( "bottom_right: " + Utils.toString(bottomRightVisibleTileCoords));
        return new TilePositionInPyramid.TilePositionInLayer[]{topLeftVisibleTile, bottomRightVisibleTile};
    }

    /**
     * @link http://www.staremapy.cz/zoomify-analyza/
     */
    private int computeTileGroup(TilePositionInPyramid tilePositionInPyramid) {
        int column = tilePositionInPyramid.getPositionInLayer().column;
        int row = tilePositionInPyramid.getPositionInLayer().row;
        int level = tilePositionInPyramid.getLayer();
        double tileSize = imageProperties.getTileSize();
        double width = imageProperties.getWidth();
        double height = imageProperties.getHeight();
        double depth = layers.size();
        // logger.d( tilePositionInPyramid.toString());
        // logger.d( "column: " + column + ", row: " + row + ", d: " + depth + ", l: " + level);
        // logger.d( "width: " + width + ", height: " + height + ", tileSize: " + tileSize);

        double first = Math.ceil(Math.floor(width / Math.pow(2, depth - level - 1)) / tileSize);
        double index = column + row * first;
        for (int i = 1; i <= level; i++) {
            index += Math.ceil(Math.floor(width / Math.pow(2, depth - i)) / tileSize)
                    * Math.ceil(Math.floor(height / Math.pow(2, depth - i)) / tileSize);
        }
        // logger.d( "index: " + index);
        int result = (int) (index / tileSize);
        // logger.d( "tile group: " + result);
        return result;
    }

    private String buildTileUrl(int tileGroup, TilePositionInPyramid tilePositionInPyramid) {
        StringBuilder builder = new StringBuilder();
        builder.append(mBaseUrl).append("TileGroup").append(tileGroup).append('/');
        builder.append(tilePositionInPyramid.getLayer()).append('-')
                .append(tilePositionInPyramid.getPositionInLayer().column).append('-')
                .append(tilePositionInPyramid.getPositionInLayer().row)
                .append(".jpg");
        return builder.toString();
    }

    private Bitmap downloadTile(String tileUrl, int remainingRedirections) throws TooManyRedirectionsException,
            ImageServerResponseException, OtherIOException {
        logger.d("downloading tile from " + tileUrl);
        if (remainingRedirections == 0) {
            throw new TooManyRedirectionsException(tileUrl, MAX_REDIRECTIONS);
        }
        // logger.d( tileUrl + " remaining redirections: " +
        // remainingRedirections);
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(tileUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(TILES_TIMEOUT);
            int responseCode = urlConnection.getResponseCode();
            switch (responseCode) {
                case 200:
                    return bitmapFromUrlConnection(urlConnection);
                case 300:
                case 301:
                case 302:
                case 303:
                case 305:
                case 307:
                    String location = urlConnection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        throw new ImageServerResponseException(tileUrl, responseCode);
                    } else {
                        urlConnection.disconnect();
                        return downloadTile(location, remainingRedirections - 1);
                    }
                default:
                    throw new ImageServerResponseException(tileUrl, responseCode);
            }
        } catch (IOException e) {
            throw new OtherIOException(e.getMessage(), tileUrl);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private Bitmap bitmapFromUrlConnection(HttpURLConnection urlConnection) throws IOException {
        InputStream in = null;
        try {
            in = new BufferedInputStream(urlConnection.getInputStream());
            return BitmapFactory.decodeStream(in);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }


    @Override
    public int[] calculateTileCoordsFromPointInImageCoords(int layerId, int pixelX, int pixelY) {
        int[] result = new int[2];
        TilePositionInPyramid.TilePositionInLayer tilePositionInLayer = calculateTileCoordsFromPointInImageCoords(layerId, new Point(pixelX, pixelY));
        result[0] = tilePositionInLayer.column;
        result[1] = tilePositionInLayer.row;
        return result;
    }

    //@Override
    private TilePositionInPyramid.TilePositionInLayer calculateTileCoordsFromPointInImageCoords(int layerId, Point pointInMageCoords) {
        checkInitialized();
        if (layerId < 0 || layerId >= layers.size()) {
            throw new IllegalArgumentException("layer out of range: " + layerId);
        }

        if (pointInMageCoords.x < 0 || pointInMageCoords.x >= imageProperties.getWidth()) {
            throw new IllegalArgumentException("x coord out of range: " + pointInMageCoords.x);
        }
        if (pointInMageCoords.y < 0 || pointInMageCoords.y >= imageProperties.getHeight()) {
            throw new IllegalArgumentException("y coord out of range: " + pointInMageCoords.y);
        }

        // optimization, zero layer is whole image with coords 0,0
        if (layerId == 0) {
            return new TilePositionInPyramid.TilePositionInLayer(0, 0);
        }
        // logger.d( "getting picture for layer=" + layerId + ", x=" + pixelX +
        // ", y=" + pixelY);
        // Log.d(TestTags.TILES, "layers: " + layers.size() + ", layer: " + layerId);
        double step = imageProperties.getTileSize() * Math.pow(2, layers.size() - layerId - 1);
        // Log.d(TestTags.TILES, "step: " + step);
        // x
        double cx_step = pointInMageCoords.x / step;
        // Log.d(TestTags.TILES, (cx_step - 1) + " < x <= " + cx_step);
        int x = (int) Math.floor(cx_step);
        // y
        double cy_step = pointInMageCoords.y / step;
        // Log.d(TestTags.TILES, (cy_step - 1) + " < y <= " + cy_step);
        int y = (int) Math.floor(cy_step);
        TilePositionInPyramid.TilePositionInLayer result = new TilePositionInPyramid.TilePositionInLayer(x, y);
        // Log.d(TestTags.TILES, "px: [" + pixelX + "," + pixelY + "] -> " + Utils.toString(result));
        return result;

    }

    /**
     * Selects highest layer, tiles of which would all fit into the image area in canvas (with exception of border tiles
     * partially overflowing).
     * <p/>
     * For determining this, canvas width/height can be taken into account either in pixels or density independent pixels or
     * combination of both (weighted arithemtic mean). Parameter mPxRatio is used for this. For example height of image area in
     * canvas is being computed this way:
     * <p/>
     * height = mPxRatio heightPx + (1-mPxRatio) * heightDp
     * <p/>
     * So to use px only, mPxRatio should be 1.0. To use dp, mPxRatio should be 0.0.
     * <p/>
     * Be aware that for devices with big displays and high display density putting big weight to px might caus extensive number
     * of tiles needed. That would lead to lots of parallel tasks for fetching tiles and hence decreased ui responsivness due to
     * network/disk (cache) access and thread synchronization. Also possible app crashes.
     * <p/>
     * On the other hand to much weight to dp could cause demanding not-deep-enought tile layer and as a consequence image would
     * seem blurry. Also devices with small displays and very high resolution would with great weight on px require unneccessary
     * number of tiles which most of people would not appreciate anyway because of limitations of human eyes.
     *
     * @param wholeImageInCanvasCoords
     * @return id of layer, that would best fill image are in canvas with only border tiles overflowing that area
     */
    @Override
    public int computeBestLayerId(Rect wholeImageInCanvasCoords) {
        checkInitialized();
        double dpRatio = 1.0 - mPxRatio;
        if (mPxRatio < 0.0) {
            throw new IllegalArgumentException("px ratio must be >= 0");
        } else if (mPxRatio > 1.0) {
            throw new IllegalArgumentException("px ratio must be <= 1");
        }

        int imageInCanvasWidthDp = 0;
        int imageInCanvasHeightDp = 0;
        if (mPxRatio < 1.0) {// optimization: initialize only if will be used
            imageInCanvasWidthDp = Utils.pxToDp(wholeImageInCanvasCoords.width());
            imageInCanvasHeightDp = Utils.pxToDp(wholeImageInCanvasCoords.height());
        }
        int imgInCanvasWidth = (int) (imageInCanvasWidthDp * dpRatio + wholeImageInCanvasCoords.width() * mPxRatio);
        int imgInCanvasHeight = (int) (imageInCanvasHeightDp * dpRatio + wholeImageInCanvasCoords.height() * mPxRatio);
        // int layersNum = layers.size();
        // if (true) {
        // // if (layersNum>=3){
        // // return 2;
        // // }else
        // if (layersNum >= 2) {
        // return 1;
        // } else {
        // return 0;
        // }
        // }
        if (true) {
            return bestLayerAtLeastAsBigAs(imgInCanvasWidth, imgInCanvasHeight);
        }

        int topLayer = layers.size() - 1;
        // Log.d(TestTags.TEST, "imgInCanvas: width: " + imgInCanvasWidth + ", height: " + imgInCanvasHeight);
        for (int layerId = topLayer; layerId >= 0; layerId--) {
            int horizontalTiles = layers.get(layerId).getTilesHorizontal();
            int layerWidthWithoutLastTile = imageProperties.getTileSize() * (horizontalTiles - 1);
            // int testWidth = imageProperties.getTileSize() * horizontalTiles;

            int verticalTiles = layers.get(layerId).getTilesVertical();
            int layerHeightWithoutLastTile = imageProperties.getTileSize() * (verticalTiles - 1);
            // int testHeight = imageProperties.getTileSize() * verticalTiles;
            double layerWidth = getLayerWidth(layerId);
            // double result = imageProperties.getWidth() / Utils.pow(2, layers.size() - layerId - 1);
            double layerHeight = getLayerHeight(layerId);
            // Log.d(TestTags.TEST, "layer " + layerId + ": width: " + layerWidth + ", height: " + layerHeight);
            if (layerWidth <= imgInCanvasWidth && layerHeight <= imgInCanvasHeight) {
                // Log.d(TestTags.TEST, "selected layer: " + layerId);
                return layerId;
                // return layerId == topLayer ? topLayer : layerId + 1;
            }

            // if (testWidth <= imgInCanvasWidth && testHeight <= imgInCanvasHeight) {
            // if (layerWidthWithoutLastTile <= imgInCanvasWidth && layerHeightWithoutLastTile <= imgInCanvasHeight) {
            // return layerId;
            // }
        }
        int layerId = 0;
        // int layerId = layers.size() - 1;
        // Log.d(TestTags.TEST, "selected layer: " + layerId);
        // return layers.size() - 1;
        return layerId;
    }

    private int bestLayerAtLeastAsBigAs(int imgInCanvasWidth, int imageInCanvasHeight) {
        // Log.d(TestTags.TEST, "imgInCanvas: width: " + imgInCanvasWidth + ", height: " + imageInCanvasHeight);
        for (int layerId = 0; layerId < layers.size(); layerId++) {
            double layerWidth = getLayerWidth(layerId);
            double layerHeight = getLayerHeight(layerId);
            if (layerWidth >= imgInCanvasWidth && layerHeight >= imageInCanvasHeight) {
                return layerId;
            }
        }
        return layers.size() - 1;
    }


    @Override
    public Rect getTileAreaInImageCoords(TilePositionInPyramid tilePositionInPyramid) {
        TileDimensionsInImage tileSizesInImage = calculateTileDimensionsInImageCoords(tilePositionInPyramid);
        int left = tileSizesInImage.basicSize * tilePositionInPyramid.getPositionInLayer().column;
        int right = left + tileSizesInImage.actualWidth;
        int top = tileSizesInImage.basicSize * tilePositionInPyramid.getPositionInLayer().row;
        int bottom = top + tileSizesInImage.actualHeight;
        return new Rect(left, top, right, bottom);
    }

    private TileDimensionsInImage calculateTileDimensionsInImageCoords(TilePositionInPyramid tilePositionInPyramid) {
        checkInitialized();
        int basicSize = getTilesBasicSizeInImageCoordsForGivenLayer(tilePositionInPyramid.getLayer());
        int width = getTileWidthInImageCoords(tilePositionInPyramid.getLayer(), tilePositionInPyramid.getPositionInLayer().column, basicSize);
        int height = getTileHeightInImageCoords(tilePositionInPyramid.getLayer(), tilePositionInPyramid.getPositionInLayer().row, basicSize);
        return new TileDimensionsInImage(basicSize, width, height);
    }

    private int getTilesBasicSizeInImageCoordsForGivenLayer(int layerId) {
        // TODO: 4.12.15 cachovat
        return imageProperties.getTileSize() * (int) (Math.pow(2, layers.size() - layerId - 1));
    }

    // TODO: sjednotit slovnik, tomuhle obcas rikam 'step'
    private int getTileWidthInImageCoords(int layerId, int tileHorizontalIndex, int basicSize) {
        if (tileHorizontalIndex == layers.get(layerId).getTilesHorizontal() - 1) {
            int result = imageProperties.getWidth() - basicSize * (layers.get(layerId).getTilesHorizontal() - 1);
            // logger.d( "TILE FAR RIGHT WIDTH: " + result);
            return result;
        } else {
            return basicSize;
        }
    }

    private int getTileHeightInImageCoords(int layerId, int tileVerticalIndex, int basicSize) {
        // Log.d(TestTags.TILES, "tileVerticalIndex:" + tileVerticalIndex);
        int verticalTilesForLayer = layers.get(layerId).getTilesVertical();
        // logger.d( "vertical tiles for layer " + layerId + ": " + verticalTilesForLayer);
        int lastTilesIndex = verticalTilesForLayer - 1;
        // Log.d(TestTags.TILES, "tiles vertical for layer: " + layerId + ": " + tilesVerticalForLayer);
        // Log.d(TestTags.TILES, "last tile's index: " + layerId + ": " + lastTilesIndex);
        // Log.d(TestTags.TEST, "tileVerticalI: " + tileVerticalIndex + ", lastTilesI: " + lastTilesIndex);
        if (tileVerticalIndex == lastTilesIndex) {
            return imageProperties.getHeight() - basicSize * (lastTilesIndex);
        } else {
            return basicSize;
        }
    }

    private double getLayerWidth(int layerId) {
        checkInitialized();
        // TODO: 4.12.15 possibly cache this if it's being called frequently
        double result = imageProperties.getWidth() / Utils.pow(2, layers.size() - layerId - 1);
        // logger.d( "layer " + layerId + ", width=" + result + " px");
        return result;
    }

    private double getLayerHeight(int layerId) {
        checkInitialized();
        // TODO: 4.12.15 possibly cache this if it's being called frequently
        return imageProperties.getHeight() / Utils.pow(2, layers.size() - layerId - 1);
    }

    @Override
    public List<Layer> getLayers() {
        checkInitialized();
        return layers;
    }


    @Override
    public void cancelAllTasks() {
//        checkInitialized();
        taskRegistry.cancelAllTasks();
    }

    @Override
    public void destroy() {
        //TODO: zabit vsechny procesy, pripadne i ten, co inicializuje metadata
    }

    @Override
    public void enqueTileDownload(TilePositionInPyramid tilePositionInPyramid, TileDownloadHandler handler) {
        taskRegistry.registerTask(tilePositionInPyramid, mBaseUrl, handler);
    }

    @Override
    public void cancelFetchingATilesForLayerExeptForThese(int layerId, List<TilePositionInPyramid> visibleTiles) {
        checkInitialized();
        for (TilePositionInPyramid runningTilePositionInPyramid : taskRegistry.getAllTaskTileIds()) {
            if (runningTilePositionInPyramid.getLayer() == layerId) {
                if (!visibleTiles.contains(runningTilePositionInPyramid)) {
                    boolean wasCanceled = taskRegistry.cancel(runningTilePositionInPyramid);
                    // if (wasCanceled) {
                    // canceled++;
                    // }
                }
            }
        }
    }


    @Override
    public void unregisterFinishedOrCanceledTask(TilePositionInPyramid tilePositionInPyramid) {
        checkInitialized();
        taskRegistry.unregisterTask(tilePositionInPyramid);
    }


}
