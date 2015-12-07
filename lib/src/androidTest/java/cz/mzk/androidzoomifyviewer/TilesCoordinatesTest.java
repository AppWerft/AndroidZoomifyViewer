package cz.mzk.androidzoomifyviewer;

import android.support.test.runner.AndroidJUnit4;
import android.test.AndroidTestCase;
import android.test.mock.MockContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import cz.mzk.androidzoomifyviewer.tiles.ImageManager;
import cz.mzk.androidzoomifyviewer.tiles.MetadataInitializationHandler;
import cz.mzk.androidzoomifyviewer.tiles.InitImageManagerTask;
import cz.mzk.androidzoomifyviewer.tiles.zoomify.Layer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Martin Řehánek
 */

@RunWith(AndroidJUnit4.class)
public class TilesCoordinatesTest extends AndroidTestCase {

    private static final Logger logger = new Logger(TilesCoordinatesTest.class);

    @Before
    public void init() throws Exception {
        logger.d("initImageMetadata");
        if (getContext() == null) {
            logger.d("initializing mock context");
            setContext(new MockContext());
        }
        assertNotNull(getContext());

        if (!CacheManager.isInitialized()) {
            logger.d("initializing " + CacheManager.class.getSimpleName());
            CacheManager.initialize(getContext(), false, false, 0);
        }
    }

    private ImageManager initTilesDownloader(String baseUrl) {
        double pxRatio = 0.5;
        final TilesDownloaderInitializationResult result = new TilesDownloaderInitializationResult();
        new InitImageManagerTask(baseUrl, pxRatio, new MetadataInitializationHandler() {

            @Override
            public void onUnhandableResponseCode(String imagePropertiesUrl, int responseCode) {
                logger.e("unexpected response code: " + responseCode);
                result.finished = true;
            }

            @Override
            public void onSuccess(ImageManager downloader) {
                result.finished = true;
                result.downloader = downloader;
                logger.d(downloader.getClass().getSimpleName() + " initialized");
            }

            @Override
            public void onRedirectionLoop(String imagePropertiesUrl, int redirections) {
                logger.e("redirection loop for " + imagePropertiesUrl);
                result.finished = true;
            }

            @Override
            public void onInvalidData(String imagePropertiesUrl, String errorMessage) {
                logger.e(String.format("invalid data for %s: %s", imagePropertiesUrl, errorMessage));
                result.finished = true;
            }

            @Override
            public void onDataTransferError(String imagePropertiesUrl, String errorMessage) {
                logger.e(String.format("data transfer error for %s: %s", imagePropertiesUrl, errorMessage));
                result.finished = true;
            }
        }).execute();

        while (!result.finished) {
            try {
                Thread.sleep(100);
                //Log.d(TAG, "waiting");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result.downloader;
    }

    @Test
    public void testZero() {
        assertTrue("problem with Junit configuration", true);
        assertThat(1, is(1));
    }

    @Test
    public void testCornerTilesCoordsMapyMzk1() {
        testCornerTilesCoords("http://mapy.mzk.cz/AA22/0103/");
    }

    @Test
    public void testCornerTilesCoordsMapyMzk2() {
        testCornerTilesCoords("http://mapy.mzk.cz/AA22/0104/");
    }

    @Test
    public void testCornerTilesCoordsMapyMzk3() {
        testCornerTilesCoords("http://mapy.mzk.cz/AA22/0105/");
    }

    @Test
    public void testCornerTilesCoordsTricedesigns1() {
        testCornerTilesCoords("http://www.tricedesigns.com/panoramas/Pemberton-Park-4/Pemberton-Park-4/");
    }

    @Test
    public void testCornerTilesCoordsTricedesigns2() {
        testCornerTilesCoords("http://www.tricedesigns.com/panoramas/office-outside/office-outside/");
    }

    public void testCornerTilesCoords(String baseUrl) {
        logger.d("testing corner tiles coords for: " + baseUrl);
        ImageManager imageManager = initTilesDownloader(baseUrl);
        assertNotNull("Tiles downloader not initialized. Probably image no longer available on base url: " + baseUrl, imageManager);
        int width = imageManager.getImageWidth();
        int height = imageManager.getImageHeight();
        List<Layer> layers = imageManager.getLayers();
        int[] topLeftCorner = {0, 0};
        int[] topRightCorner = {width - 1, 0};
        int[] bottomLeftCorner = {0, height - 1};
        int[] bottomRightCorner = {width - 1, height - 1};

        for (int layer = 0; layer < layers.size(); layer++) {
            logger.d("layer: " + layer);
            int horizontal = layers.get(layer).getTilesHorizontal();
            int vertical = layers.get(layer).getTilesVertical();
            assertTileCoords(imageManager, layer, topLeftCorner, new int[]{0, 0});
            assertTileCoords(imageManager, layer, topRightCorner, new int[]{horizontal - 1, 0});
            assertTileCoords(imageManager, layer, bottomLeftCorner, new int[]{0, vertical - 1});
            assertTileCoords(imageManager, layer, bottomRightCorner, new int[]{horizontal - 1, vertical - 1});
        }
    }

    //url no longer available
    /*@Test
    public void testCornerTilesCoordsTricedesigns3() {
        testCornerTilesCoords("http://www.tricedesigns.com/panoramas/Pemberton-Park-4/Pemberton-Park-3/");
    }*/


    //url no longer available
    /*@Test
    public void testCornerTilesCoordsFookes1() {
        testCornerTilesCoords("http://www.fookes.com/ezimager/zoomify/105_0532/");
    }*/

    private void assertTileCoords(ImageManager mImageManager, int layerId, int[] pixel, int[] expectedCoords) {
        int[] actualCoords = mImageManager.calculateTileCoordsFromPointInImageCoords(layerId, pixel[0], pixel[1]);
        assertEquals(expectedCoords[0], actualCoords[0]);
        assertEquals(expectedCoords[1], actualCoords[1]);
    }

    class TilesDownloaderInitializationResult {
        public boolean finished = false;
        public ImageManager downloader;
    }


}
