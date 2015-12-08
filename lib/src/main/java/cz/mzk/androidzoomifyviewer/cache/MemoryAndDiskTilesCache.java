package cz.mzk.androidzoomifyviewer.cache;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import cz.mzk.androidzoomifyviewer.ConcurrentAsyncTask;
import cz.mzk.androidzoomifyviewer.Logger;
import cz.mzk.androidzoomifyviewer.cache.DiskLruCache.DiskLruCacheException;
import cz.mzk.androidzoomifyviewer.cache.DiskLruCache.Snapshot;
import cz.mzk.androidzoomifyviewer.cache.TileBitmap.State;
import cz.mzk.androidzoomifyviewer.viewer.Utils;

/**
 * @author Martin Řehánek
 */
public class MemoryAndDiskTilesCache extends AbstractTileCache implements TilesCache {

    // public static final int DISK_CACHE_SIZE = 1024; // 100MB
    // public static final int DISK_CACHE_SIZE = 1024 * 1024 * 100; // 100MB
    public static final String DISK_CACHE_SUBDIR = "tiles";

    private static final Logger LOGGER = new Logger(MemoryAndDiskTilesCache.class);
    private final Object mMemoryCacheLock = new Object();
    private final BitmapFetchTaskRegistry mBitmapFetchManager;
    private final Object mDiskCacheInitializationLock = new Object();
    private LruCache<String, Bitmap> mMemoryCache;
    private boolean mDiskCacheEnabled;
    private DiskLruCache mDiskCache = null;

    public MemoryAndDiskTilesCache(Context context, int memoryCacheMaxItems, boolean diskCacheEnabled, boolean clearDiskCache, long diskCacheBytes) {
        super();
        mDiskCacheEnabled = diskCacheEnabled;
        // mMemoryCache = initMemoryCacheFixedSize();
        synchronized (mMemoryCacheLock) {
            mMemoryCache = new LruCache<String, Bitmap>(memoryCacheMaxItems);
        }
        LOGGER.i("memory cache initialized; max items: " + memoryCacheMaxItems);
        mBitmapFetchManager = new BitmapFetchTaskRegistry(this);
        if (mDiskCacheEnabled) {
            initDiskCacheAsync(context, clearDiskCache, diskCacheBytes);
        }
    }

    private LruCache<String, Bitmap> initMemoryCacheFixedSize() {
        // Get max available VM memory, exceeding this amount will throw an OutOfMemory exception. Stored in kilobytes as LruCache
        // takes an int in its constructor.
        int maxMemoryKB = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // Use 1/8th of the available memory for this memory cache.
        final int cacheSizeKB = maxMemoryKB / 8;
        LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(cacheSizeKB) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than number of items.
                return getBitmapSizeInKB(bitmap);
            }
        };
        // LOGGER.d("in-memory lru cache allocated with " + cacheSizeKB + " kB");
        return cache;
    }

    // TODO: no need to be async
    private void initDiskCacheAsync(Context context, boolean clearCache, long diskCacheBytes) {
        try {
            File cacheDir = getDiskCacheDir(context);
            int appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            new InitDiskCacheTask(appVersion, clearCache, diskCacheBytes).execute(cacheDir);
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

    private void disableDiskCache() {
        LOGGER.i("disabling disk cache");
        mDiskCacheEnabled = false;
    }

    @Override
    public void storeTile(Bitmap tile, String tileUrl) {
        String key = buildKey(tileUrl);
        storeTileToMemoryCache(key, tile);
        storeTileToDiskCache(key, tile);
    }

    protected void storeTileToMemoryCache(String key, Bitmap bmp) {
        synchronized (mMemoryCacheLock) {
            // Log.v(TAG, "assuming mMemoryCache lock: " + Thread.currentThread().toString());
            if (mMemoryCache.get(key) == null) {
                // LOGGER.d("storing to memory cache: " + key);
                mMemoryCache.put(key, bmp);
            } else {
                // LOGGER.d("already in memory cache: " + key);
            }
            // Log.v(TAG, "releasing mMemoryCache lock: " + Thread.currentThread().toString());
        }
    }

    private void storeTileToDiskCache(String key, Bitmap bmp) {
        waitUntilDiskCacheInitializedOrDisabled();
        try {
            if (mDiskCacheEnabled) {
                Snapshot fromDiskCache = mDiskCache.get(key);
                if (fromDiskCache != null) {
                    LOGGER.d("already in disk cache: " + key);
                } else {
                    LOGGER.d("storing to disk cache: " + key);
                    mDiskCache.storeBitmap(0, key, bmp);
                }
            }
        } catch (DiskLruCacheException e) {
            LOGGER.e("failed to store tile to disk cache: " + key, e);
        }
    }

    @Override
    public Bitmap getTile(String tileUrl) {
        String key = buildKey(tileUrl);
        Bitmap inMemoryCache = getTileFromMemoryCache(key);
        // long afterHitOrMiss = System.currentTimeMillis();
        if (inMemoryCache != null) {
            // LOGGER.d("memory cache hit: " + key);
            // LOGGER.d("memory cache hit, delay: " + (afterHitOrMiss - start) + " ms");
            // return new TileBitmap(State.IN_MEMORY, inMemoryCache);
            return inMemoryCache;
        } else {
            // LOGGER.d("memory cache miss: " + key);
            // LOGGER.d("memory cache miss, delay: " + (afterHitOrMiss - start) + " ms");
            Bitmap fromDiskCache = getTileFromDiskCache(key);
            // store also to memory cache (nonblocking)
            if (fromDiskCache != null) {
                try {
                    new StoreTileToMemoryCacheTask(key).executeConcurrentIfPossible(fromDiskCache);
                } catch (RejectedExecutionException e) {
                    LOGGER.w("to many threads in execution pool");
                }
            }
            return fromDiskCache;
        }
    }

    private Bitmap getTileFromMemoryCache(String key) {
        synchronized (mMemoryCacheLock) {
            return mMemoryCache.get(key);
        }
    }

    @Override
    public boolean containsTile(String tileUrl) {
        String key = buildKey(tileUrl);
        // Bitmap inMemory = mMemoryCache.get(key);
        Bitmap inMemory = getTileFromMemoryCache(key);
        if (inMemory != null) {
            return true;
        } else {
            return diskCacheContainsTile(key);
        }
    }

    @Override
    public boolean containsTileInMemory(String tileUrl) {
        String key = buildKey(tileUrl);
        // Bitmap inMemory = mMemoryCache.get(key);
        Bitmap inMemory = getTileFromMemoryCache(key);
        return inMemory != null;
    }

    private void waitUntilDiskCacheInitializedOrDisabled() {
        try {
            synchronized (mDiskCacheInitializationLock) {
                // Log.v(TAG, "assuming disk cache initialization lock: " + Thread.currentThread().toString());
                // Wait until disk cache is initialized or disabled
                while (mDiskCache == null && mDiskCacheEnabled) {
                    try {
                        mDiskCacheInitializationLock.wait();
                    } catch (InterruptedException e) {
                        LOGGER.e("waiting for disk cache lock interrupted", e);
                    }
                }
            }
        } finally {
            LOGGER.v("releasing disk cache initialization lock: " + Thread.currentThread().toString());
        }
    }


    private boolean diskCacheContainsTile(String key) {
        waitUntilDiskCacheInitializedOrDisabled();
        try {
            if (mDiskCacheEnabled) {
                return mDiskCache.containsReadable(key);
            } else {
                return false;
            }
        } catch (IOException e) {
            LOGGER.e("error loading tile from disk cache: " + key, e);
            return false;
        }
    }

    protected Bitmap getTileFromDiskCache(String key) {
        waitUntilDiskCacheInitializedOrDisabled();
        try {
            if (mDiskCacheEnabled) {
                // long start = System.currentTimeMillis();
                Snapshot snapshot = mDiskCache.get(key);
                if (snapshot != null) {
                    // long afterHit = System.currentTimeMillis();
                    InputStream in = snapshot.getInputStream(0);
                    Bitmap stream = BitmapFactory.decodeStream(in);
                    // long afterDecoding = System.currentTimeMillis();
                    // long retrieval = afterHit - start;
                    // long decoding = afterDecoding - afterHit;
                    // long total = retrieval + decoding;
                    // LOGGER.d("disk cache hit: " + key + ", delay: " + total + "ms (retrieval: " + retrieval +
                    // "ms, decoding: " + decoding + " ms)");
                    if (stream == null) {
                        LOGGER.w("bitmap from disk cache was null, removing record");
                        mDiskCache.remove(key);
                    }
                    return stream;
                } else {
                    // long afterMiss = System.currentTimeMillis();
                    // LOGGER.d("disk cache miss: " + key + ", delay: " + (afterMiss - start) + " ms");
                    return null;
                }
            } else {
                return null;
            }
        } catch (DiskLruCacheException e) {
            LOGGER.e("error loading tile from disk cache: " + key, e);
            return null;
        }
    }

    @Override
    public TileBitmap getTileAsync(String tileUrl, FetchingBitmapFromDiskHandler handler) {
        // Debug.startMethodTracing();
        String key = buildKey(tileUrl);
        // long start = System.currentTimeMillis();
        // Bitmap inMemoryCache = mMemoryCache.get(key);
        Bitmap inMemoryCache = getTileFromMemoryCache(key);
        // long afterHitOrMiss = System.currentTimeMillis();
        if (inMemoryCache != null) {
            // Log.v(TAG, "memory cache hit: " + key);
            // LOGGER.d("memory cache hit, delay: " + (afterHitOrMiss - start) + " ms");
            return new TileBitmap(State.IN_MEMORY, inMemoryCache);
        } else {
            // Log.v(TAG, "memory cache miss: " + key);
            if (mDiskCacheEnabled) {
                try {
                    if (mDiskCache.containsReadable(key)) {
                        mBitmapFetchManager.registerTask(key, handler);
                        return new TileBitmap(State.IN_DISK, null);
                    } else {
                        return new TileBitmap(State.NOT_FOUND, null);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return new TileBitmap(State.NOT_FOUND, null);
                }
            } else {
                return new TileBitmap(State.NOT_FOUND, null);
            }
        }
    }


    @Override
    public void cancelAllTasks() {
        mBitmapFetchManager.cancelAllTasks();
    }

    @Override
    public void updateMemoryCacheSizeInItems(int minSize) {
        synchronized (mMemoryCacheLock) {
            // https://www.youtube.com/watch?v=oGrXdxpWgyY&list=PLOU2XLYxmsIKEOXh5TwZEv89aofHzNCiu&index=11
            // TODO: StrictMode.setThreadPolicy(policy);
            // StrictMode.noteSlowCall("updateMemoryCacheSizeInItems lock");
            int currentSize = mMemoryCache.maxSize();
            if (currentSize < minSize) {
                LOGGER.d("Increasing cache size " + currentSize + " -> " + minSize + " items");
                if (Build.VERSION.SDK_INT >= 21) {
                    mMemoryCache.resize(minSize);
                } else {//resize manually by creating new cache instance
                    Map<String, Bitmap> snapshot = mMemoryCache.snapshot();
                    mMemoryCache = new LruCache<>(minSize);
                    for (String key : snapshot.keySet()) {
                        mMemoryCache.put(key, snapshot.get(key));
                    }
                }
            } else {
                // Log.v(TAG, "" + currentSize + " >= " + minSize);
            }
        }
    }

    @Override
    public void close() {
        try {
            mDiskCache.flush();
        } catch (DiskLruCacheException e) {
            LOGGER.e("Error flushing disk cache");
        } finally {
            try {
                mDiskCache.close();
            } catch (IOException e) {
                LOGGER.e("Error closing disk cache");
            }
        }
    }

    private class InitDiskCacheTask extends AsyncTask<File, Void, Void> {
        private final int appVersion;
        private final boolean clearCache;
        private final long cacheSizeBytes;

        public InitDiskCacheTask(int appVersion, boolean clearCache, long diskCacheBytes) {
            this.appVersion = appVersion;
            this.clearCache = clearCache;
            this.cacheSizeBytes = diskCacheBytes;
        }

        @Override
        protected Void doInBackground(File... params) {
            synchronized (mDiskCacheInitializationLock) {
                // Log.v(TAG, "assuming mDiskCacheLock: " + Thread.currentThread().toString());
                try {
                    File cacheDir = params[0];
                    if (cacheDir.exists()) {
                        if (clearCache) {
                            LOGGER.i("clearing disk cache");
                            boolean cleared = DiskUtils.deleteDirContent(cacheDir);
                            if (!cleared) {
                                LOGGER.w("failed to delete content of " + cacheDir.getAbsolutePath());
                                disableDiskCache();
                                return null;
                            }
                        }
                    } else {
                        // LOGGER.i("creating cache dir " + cacheDir);
                        boolean created = cacheDir.mkdir();
                        if (!created) {
                            LOGGER.w("failed to create cache dir " + cacheDir.getAbsolutePath());
                            disableDiskCache();
                            return null;
                        }
                    }
                    mDiskCache = DiskLruCache.open(cacheDir, appVersion, 1, cacheSizeBytes);
                    LOGGER.i("disk cache initialized; size: " + Utils.formatBytes(cacheSizeBytes));
                    return null;
                } catch (DiskLruCacheException e) {
                    LOGGER.w("error opening disk cache, disabling");
                    disableDiskCache();
                    return null;
                } finally {
                    // Log.v(TAG, "releasing disk cache initialization lock: " + Thread.currentThread().toString());
                    mDiskCacheInitializationLock.notifyAll();
                }
            }
        }
    }

    private class StoreTileToMemoryCacheTask extends ConcurrentAsyncTask<Bitmap, Void, Void> {
        private final String key;

        public StoreTileToMemoryCacheTask(String key) {
            this.key = key;
        }

        @Override
        protected Void doInBackground(Bitmap... params) {
            storeTileToMemoryCache(key, params[0]);
            return null;
        }
    }

}