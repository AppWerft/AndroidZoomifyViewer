package cz.mzk.androidzoomifyviewer.tiles;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.util.Log;

/**
 * Registers running AsynctTasks in which tiles for single page are downloaded
 * and saved to cache. Purpose of this class is to prevent executing multiple
 * tasks to download same tile. Methods of this class are allways accessed from
 * UI thread, so there's no need for synchronization of the map.
 * 
 * @author Martin Řehánek
 * 
 */
public class DownloadAndSaveTileTasksRegistry {

	private static final String TAG = DownloadAndSaveTileTasksRegistry.class.getSimpleName();

	private final Map<String, DownloadAndSaveTileTask> tilesBeingDownloaded = new HashMap<String, DownloadAndSaveTileTask>();

	public void registerTask(DownloadAndSaveTileTask task, TileId tileId) {
		String key = tileId.toString();
		tilesBeingDownloaded.put(key, task);
		Log.d(TAG, "  registered task for " + key + ": (total " + tilesBeingDownloaded.size() + ")");
	}

	public void unregisterTask(TileId tileId) {
		String key = tileId.toString();
		tilesBeingDownloaded.remove(key);
		Log.d(TAG, "unregistered task for " + key + ": (total " + tilesBeingDownloaded.size() + ")");
	}

	public DownloadAndSaveTileTask getTask(TileId tileId) {
		String key = tileId.toString();
		return tilesBeingDownloaded.get(key);
	}

	public boolean isRunning(TileId tileId) {
		String key = tileId.toString();
		return tilesBeingDownloaded.containsKey(key);
	}

	public Set<String> getAllTaskTileIds() {
		return tilesBeingDownloaded.keySet();
	}

	public Collection<DownloadAndSaveTileTask> getAllTasks() {
		return tilesBeingDownloaded.values();
	}
}
