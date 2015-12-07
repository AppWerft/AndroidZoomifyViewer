package cz.mzk.androidzoomifyviewer.tiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import cz.mzk.androidzoomifyviewer.Logger;
import cz.mzk.androidzoomifyviewer.viewer.TiledImageView;

/**
 * This class registers running AsyncTasks in which tiles for single image are downloaded and saved to cache. Also AsyncTask to initialize image metadata.
 * Purpose of this class is to prevent executing multiple task to download same tile. Methods of this class are allways accessed from UI thread,
 * so there's no need for synchronization of internal data (tiles map).
 *
 * @author Martin Řehánek
 */
public class ImageManagerTaskRegistry {

    public static final int MAX_TASKS_IN_POOL = 50;

    private static final Logger LOGGER = new Logger(ImageManagerTaskRegistry.class);

    private final ImageManager mImgManager;
    private final Map<TilePositionInPyramid, DownloadAndSaveTileTask> mTileDownloadTasks = new HashMap<>();
    private InitImageManagerTask mInitMetadataTask;

    public ImageManagerTaskRegistry(ImageManager imgManager) {
        this.mImgManager = imgManager;
    }

    public void enqueueTileDownloadTask(final TilePositionInPyramid tilePosition, String tileImageUrl, TiledImageView.TileDownloadErrorListener errorListener, TiledImageView.TileDownloadSuccessListener successListener) {
        if (mTileDownloadTasks.size() < MAX_TASKS_IN_POOL) {
            if (!mTileDownloadTasks.containsKey(tilePosition)) {
                LOGGER.d(String.format("enqueuing tile-download task: %s, (total %d)", tileImageUrl, mTileDownloadTasks.size()));
                DownloadAndSaveTileTask task = new DownloadAndSaveTileTask(mImgManager, tileImageUrl, errorListener, successListener, new TaskFinishedListener() {

                    @Override
                    public void onTaskFinished() {
                        mTileDownloadTasks.remove(tilePosition);
                    }
                });
                mTileDownloadTasks.put(tilePosition, task);
                task.executeConcurrentIfPossible();
            } else {
                LOGGER.d(String.format("ignoring tile-download task for '%s' (already in queue)", tileImageUrl));
            }
        } else {
            LOGGER.d(String.format("ignoring tile-download task for '%s' (queue full - %d items)", tileImageUrl, mTileDownloadTasks.size()));
        }
    }

    public void enqueueMetadataInitializationTask(TiledImageView.MetadataInitializationHandler handler, TiledImageView.MetadataInitializationSuccessListener successListener) {
        if (mInitMetadataTask == null) {
            LOGGER.d("enqueuing metadata-initialization task");
            mInitMetadataTask = new InitImageManagerTask(mImgManager, handler, successListener, new TaskFinishedListener() {

                @Override
                public void onTaskFinished() {
                    mInitMetadataTask = null;
                }
            });
            mInitMetadataTask.executeConcurrentIfPossible();
        } else {
            LOGGER.d("ignoring metadata-initialization task - already in queue");
        }
    }

    public void cancelAllTasks() {
        LOGGER.d("canceling all tasks");
        if (mInitMetadataTask != null) {
            mInitMetadataTask.cancel(false);
            mInitMetadataTask = null;
        }
        for (DownloadAndSaveTileTask task : mTileDownloadTasks.values()) {
            task.cancel(false);
        }
    }

    public boolean cancel(TilePositionInPyramid id) {
        DownloadAndSaveTileTask task = mTileDownloadTasks.get(id);
        if (task != null) {
            LOGGER.d(String.format("canceling tile-download task for %s", id.toString()));
            task.cancel(false);
            return true;
        } else {
            return false;
        }
    }

    public Set<TilePositionInPyramid> getAllTileDownloadTaskIds() {
        return mTileDownloadTasks.keySet();
    }


    public static interface TaskFinishedListener {
        void onTaskFinished();
    }
}
