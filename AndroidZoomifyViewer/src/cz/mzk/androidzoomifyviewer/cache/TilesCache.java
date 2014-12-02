package cz.mzk.androidzoomifyviewer.cache;

import android.graphics.Bitmap;
import cz.mzk.androidzoomifyviewer.tiles.TileId;

/**
 * @author Martin Řehánek
 * 
 */
public interface TilesCache {

	/**
	 * Returns tile's bitmap from memory cache. If it's not found there, returns bitmap loaded from disc cache (blocking). Returns
	 * null if bitmap was not found in either cache.
	 * 
	 * @param zoomifyBaseUrl
	 * @param tileId
	 * @return
	 */
	public Bitmap getTile(String zoomifyBaseUrl, TileId tileId);

	/**
	 * Returns tile's bitmap if it has been found in memory cache. Or if the bitmap is not in memory but in disk cache, tries to
	 * execute async task that fetches bitmap from disk and stores into memory cache. Or informs that bitmap was not found in
	 * either cache.
	 * 
	 * @param zoomifyBaseUrl
	 * @param tileId
	 * @param handler
	 * @return TileBitmap object that contains bitmap it self (or null) and tile's bitmap state.
	 */
	public TileBitmap getTileAsync(String zoomifyBaseUrl, TileId tileId, FetchingBitmapFromDiskHandler handler);

	public boolean containsTile(String zoomifyBaseUrl, TileId tileId);

	public boolean containsTileInMemory(String zoomifyBaseUrl, TileId tileId);

	public void storeTile(Bitmap tile, String zoomifyBaseUrl, TileId tileId);

	public void cancelAllTasks();

	public static interface FetchingBitmapFromDiskHandler {
		public void onFetched();
	}

	public void updateMemoryCacheSizeInItems(int size);

	// public void updateMemoryCacheSizeInItems(int minSize, int maxSize);

}