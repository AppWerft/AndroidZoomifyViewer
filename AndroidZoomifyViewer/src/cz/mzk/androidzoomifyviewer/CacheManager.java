package cz.mzk.androidzoomifyviewer;

import android.content.Context;
import cz.mzk.androidzoomifyviewer.cache.ImagePropertiesCache;
import cz.mzk.androidzoomifyviewer.cache.MemoryAndDiskImagePropertiesCache;
import cz.mzk.androidzoomifyviewer.cache.MemoryAndDiskTilesCache;
import cz.mzk.androidzoomifyviewer.cache.TilesCache;

/**
 * @author Martin Řehánek
 * 
 */
public class CacheManager {
	// TODO: podobne dalsi konfiguraci, jako timouty (properties, dlazdice),
	// devMode,

	private static TilesCache tilesCache;
	private static ImagePropertiesCache imagePropertiesCache;
	private static boolean initialized = false;

	public static void initialize(Context context) {
		if (initialized) {
			throw new IllegalStateException(CacheManager.class.getSimpleName() + " has been already initialized");
		}
		tilesCache = new MemoryAndDiskTilesCache(context);
		// tilesCache = new MemoryTilesCache();
		imagePropertiesCache = new MemoryAndDiskImagePropertiesCache(context);
		initialized = true;
	}

	public static TilesCache getTilesCache() {
		if (!initialized) {
			throw new IllegalStateException(CacheManager.class.getSimpleName() + " has not been initialized");
		}
		return tilesCache;
	}

	public static ImagePropertiesCache getImagePropertiesCache() {
		if (!initialized) {
			throw new IllegalStateException(CacheManager.class.getSimpleName() + " has not been initialized");
		}
		return imagePropertiesCache;
	}

}
