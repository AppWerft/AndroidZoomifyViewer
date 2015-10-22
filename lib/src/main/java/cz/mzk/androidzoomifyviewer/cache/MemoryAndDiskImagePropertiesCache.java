package cz.mzk.androidzoomifyviewer.cache;

import java.io.File;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import cz.mzk.androidzoomifyviewer.Logger;
import cz.mzk.androidzoomifyviewer.cache.DiskLruCache.DiskLruCacheException;
import cz.mzk.androidzoomifyviewer.cache.DiskLruCache.Snapshot;

/**
 * @author Martin Řehánek
 * 
 */
public class MemoryAndDiskImagePropertiesCache extends AbstractImagePropertiesCache implements ImagePropertiesCache {

	public static final String DISK_CACHE_SUBDIR = "imageProperties";
	public static final int DISK_CACHE_SIZE_B = 1024 * 1024 * 10; // 10MB
	public static final int MEMORY_CACHE_SIZE_ITEMS = 10;

	private static final Logger logger = new Logger(MemoryAndDiskImagePropertiesCache.class);

	private final LruCache<String, String> mMemoryCache;
	private final Object mDiskCacheInitializationLock = new Object();
	private DiskLruCache mDiskCache = null;
	private boolean mDiskCacheEnabled;

	public MemoryAndDiskImagePropertiesCache(Context context, boolean diskCacheEnabled, boolean clearCache) {
		mDiskCacheEnabled = diskCacheEnabled;
		mMemoryCache = initMemoryCache();
		if (mDiskCacheEnabled) {
			initDiskCacheAsync(context, clearCache);
		}
	}

	private LruCache<String, String> initMemoryCache() {
		LruCache<String, String> cache = new LruCache<String, String>(MEMORY_CACHE_SIZE_ITEMS);
		logger.d("in-memory lru cache allocated for " + MEMORY_CACHE_SIZE_ITEMS + " items");
		return cache;
	}

	private void initDiskCacheAsync(Context context, boolean clearCache) {
		try {
			File cacheDir = getDiskCacheDir(context);
			int appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
			new InitDiskCacheTask(appVersion, clearCache).execute(cacheDir);
		} catch (NameNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a unique subdirectory of the designated app cache directory. Tries to use external but if not mounted, falls back
	 * on internal storage.
	 * 
	 * @param context
	 * @return
	 */
	private File getDiskCacheDir(Context context) {
		// Check if media is mounted or storage is built-in, if so, try and use
		// external cache dir (and exteranlscache dir is not null}
		// otherwise use internal cache dir
		// TODO: rozmyslet velikost cache podle zvoleneho uloziste
		// FIXME: na S3 haze nullpointerexception
		// String cacheDirPath =
		// Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
		// || !Environment.isExternalStorageRemovable() ?
		// context.getExternalCacheDir().getPath() : context
		// .getCacheDir().getPath();
		String cacheDirPath = context.getCacheDir().getPath();
		return new File(cacheDirPath + File.separator + DISK_CACHE_SUBDIR);
	}

	private class InitDiskCacheTask extends AsyncTask<File, Void, Void> {
		private final int appVersion;
		private final boolean clearCache;

		public InitDiskCacheTask(int appVersion, boolean clearCache) {
			this.appVersion = appVersion;
			this.clearCache = clearCache;
		}

		@Override
		protected Void doInBackground(File... params) {
			synchronized (mDiskCacheInitializationLock) {
				logger.v("assuming disk cache initialization lock: " + Thread.currentThread().toString());
				try {
					File cacheDir = params[0];
					if (cacheDir.exists()) {
						if (clearCache) {
							logger.i("clearing image-properties disk cache");
							boolean cleared = DiskUtils.deleteDirContent(cacheDir);
							if (!cleared) {
								logger.w("failed to delete content of " + cacheDir.getAbsolutePath());
								disableDiskCache();
								return null;
							}
						}
					} else {
						logger.i("creating cache dir " + cacheDir);
						boolean created = cacheDir.mkdir();
						if (!created) {
							logger.w("failed to create cache dir " + cacheDir.getAbsolutePath());
							disableDiskCache();
							return null;
						}
					}
					mDiskCache = DiskLruCache.open(cacheDir, appVersion, 1, DISK_CACHE_SIZE_B);
					return null;
				} catch (DiskLruCacheException e) {
					logger.w("error initializing disk cache, disabling");
					disableDiskCache();
					mDiskCacheInitializationLock.notifyAll();
					return null;
				} finally {
					mDiskCacheInitializationLock.notifyAll();
					logger.v("releasing disk cache initialization lock: " + Thread.currentThread().toString());
				}
			}
		}
	}

	private void disableDiskCache() {
		logger.i("disabling disk cache");
		mDiskCacheEnabled = false;
	}

	@Override
	public void storeXml(String xml, String zoomifyBaseUrl) {
		String key = buildKey(zoomifyBaseUrl);
		storeXmlToMemoryCache(key, xml);
		storeXmlToDiskCache(key, xml);
	}

	private void storeXmlToMemoryCache(String key, String xml) {
		synchronized (mMemoryCache) {
			logger.v("assuming mMemoryCache lock: " + Thread.currentThread().toString());
			if (mMemoryCache.get(key) == null) {
				logger.d("storing to memory cache: " + key);
				mMemoryCache.put(key, xml);
			} else {
				logger.d("already in memory cache: " + key);
			}
			logger.v("releasing mMemoryCache lock: " + Thread.currentThread().toString());
		}
	}

	private void waitUntilDiskCacheInitializedOrDisabled() {
		try {
			synchronized (mDiskCacheInitializationLock) {
				logger.v("assuming disk cache initialization lock: " + Thread.currentThread().toString());
				// Wait until disk cache is initialized or disabled
				while (mDiskCache == null && mDiskCacheEnabled) {
					try {
						mDiskCacheInitializationLock.wait();
					} catch (InterruptedException e) {
						logger.e("waiting for disk cache initialization lock was interrupted", e);
					}
				}
			}
		} finally {
			logger.v("releasing disk cache initialization lock: " + Thread.currentThread().toString());
		}
	}

	private void storeXmlToDiskCache(String key, String xml) {
		waitUntilDiskCacheInitializedOrDisabled();
		try {
			if (mDiskCacheEnabled) {
				Snapshot fromDiskCache = mDiskCache.get(key);
				if (fromDiskCache != null) {
					logger.d("already in disk cache: " + key);
				} else {
					logger.d("storing to disk cache: " + key);
					mDiskCache.storeString(0, key, xml);
				}
			}
		} catch (DiskLruCacheException e) {
			logger.e("failed to store xml to disk cache: " + key, e);
		}
	}

	@Override
	public String getXml(String zoomifyBaseUrl) {
		String key = buildKey(zoomifyBaseUrl);
		// long start = System.currentTimeMillis();
		String inMemoryCache = mMemoryCache.get(key);
		// long afterHitOrMiss = System.currentTimeMillis();
		if (inMemoryCache != null) {
			logger.d("memory cache hit: " + key);
			// logger.d("memory cache hit, delay: " + (afterHitOrMiss - start) + " ms");
			return inMemoryCache;
		} else {
			logger.d("memory cache miss: " + key);
			// logger.d("memory cache miss, delay: " + (afterHitOrMiss - start) + " ms");
			String fromDiskCache = getXmlFromDiskCache(key);
			// store also to memory cache (nonblocking)
			if (fromDiskCache != null) {
				new StoreXmlToMemoryCacheTask(key).execute(fromDiskCache);
			}
			return fromDiskCache;
		}
	}

	private String getXmlFromDiskCache(String key) {
		waitUntilDiskCacheInitializedOrDisabled();
		try {
			if (mDiskCacheEnabled) {
				Snapshot snapshot = mDiskCache.get(key);
				if (snapshot != null) {
					String result = snapshot.getString(0);
					return result;
				} else {
					return null;
				}
			} else {
				return null;
			}
		} catch (DiskLruCacheException e) {
			logger.i("error loading xml from disk cache: " + key, e);
			return null;
		}
	}

	private class StoreXmlToMemoryCacheTask extends AsyncTask<String, Void, Void> {
		private final String key;

		public StoreXmlToMemoryCacheTask(String key) {
			this.key = key;
		}

		@Override
		protected Void doInBackground(String... params) {
			storeXmlToMemoryCache(key, params[0]);
			return null;
		}
	}

}
