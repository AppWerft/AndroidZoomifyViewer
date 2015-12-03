package cz.mzk.androidzoomifyviewer.viewer;

import android.content.res.Resources;
import android.graphics.Rect;

import java.math.BigDecimal;
import java.util.Locale;

import cz.mzk.androidzoomifyviewer.Logger;

/**
 * @author Martin Řehánek
 */
public class Utils {

    private static final Logger logger = new Logger(Utils.class);

    public static String toString(Rect rect, String unit) {
        StringBuilder builder = new StringBuilder();
        builder.append("horizontal: ").append(rect.left).append("-").append(rect.right).append(" (")
                .append(rect.width()).append(' ').append(unit).append(')');
        builder.append(", ");
        builder.append("vertical: ").append(rect.top).append("-").append(rect.bottom).append(" (")
                .append(rect.height()).append(' ').append(unit).append(')');
        return builder.toString();
    }

    public static PointD toImageCoords(PointD pointInCanvasCoords, double imageToCanvasScaleFactor, VectorD imageShiftInCanvas) {
        double imageX = (pointInCanvasCoords.x - imageShiftInCanvas.x) / imageToCanvasScaleFactor;
        double imageY = (pointInCanvasCoords.y - imageShiftInCanvas.y) / imageToCanvasScaleFactor;
        return new PointD(imageX, imageY);
    }

    public static PointD toCanvasCoords(PointD pointInImageCoords, double imageScaleFactor, VectorD imageShiftInCanvas) {
        double canvasX = (pointInImageCoords.x * imageScaleFactor + imageShiftInCanvas.x);
        double canvasY = (pointInImageCoords.y * imageScaleFactor + imageShiftInCanvas.y);
        PointD result = new PointD(canvasX, canvasY);
        // Log.d(TAG,
        // inImageCoords.toString() + "->" + result.toString() +
        // String.format(" SCALE: %.4f", imageScaleFactor)
        // + " shift: " + imageShiftInCanvas);
        return result;
    }

    public static RectD toCanvasCoords(Rect inImageCoords, double imageScaleFactor, VectorD imageShiftInCanvas) {
        double left = (inImageCoords.left * imageScaleFactor + imageShiftInCanvas.x);
        double top = (inImageCoords.top * imageScaleFactor + imageShiftInCanvas.y);
        double right = (inImageCoords.right * imageScaleFactor + imageShiftInCanvas.x);
        double bottom = (inImageCoords.bottom * imageScaleFactor + imageShiftInCanvas.y);
        return new RectD(left, top, right, bottom);
    }


    public static PointD computeShift(PointD inImageCoords, PointD inCanvasCoords, double imageToCanvasScaleFactor) {
        double shiftX = inCanvasCoords.x - inImageCoords.x * imageToCanvasScaleFactor;
        double shiftY = inCanvasCoords.y - inImageCoords.y * imageToCanvasScaleFactor;
        return new PointD(shiftX, shiftY);
    }

    public static double computeShiftX(double xInImageCoords, double xInCanvasCoords, double imageToCanvasScaleFactor) {
        return xInCanvasCoords - xInImageCoords * imageToCanvasScaleFactor;
    }

    public static double computeShiftY(double yInImageCoords, double yInCanvasCoords, double imageToCanvasScaleFactor) {
        return yInCanvasCoords - yInImageCoords * imageToCanvasScaleFactor;
    }

    public static double toImageX(double canvasX, double imageToCanvasResizeFactor, double imageShiftInCanvasX) {
        return (canvasX - imageShiftInCanvasX) / imageToCanvasResizeFactor;
    }

    public static double toImageY(double canvasY, double imageToCanvasResizeFactor, double imageShiftInCanvasY) {
        return (canvasY - imageShiftInCanvasY) / imageToCanvasResizeFactor;
    }

    public static double toCanvasX(double imageX, double imageScaleFactor, double imageShiftInCanvasX) {
        return imageX * imageScaleFactor + imageShiftInCanvasX;
    }

    public static double toCanvasY(double imageY, double imageScaleFactor, double imageShiftInCanvasY) {
        return imageY * imageScaleFactor + imageShiftInCanvasY;
    }

    public static String toString(Rect rect) {
        return toString(rect, "px");
    }

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static double dpToPx(double dp) {
        return dp * Resources.getSystem().getDisplayMetrics().density;
    }

    public static int pxToDp(int px) {
        return (int) (px / Resources.getSystem().getDisplayMetrics().density);
    }

    public static double pxToDp(double px) {
        return px / Resources.getSystem().getDisplayMetrics().density;
    }

    public static String coordsToString(int[] coords) {
        return "[" + coords[0] + ',' + coords[1] + ']';
    }

    /**
     * Should not be used for big number, use Math.pow(double, double) instead.
     *
     * @param x
     * @param power
     * @return
     */
    public static int pow(int x, int power) {
        int result = 1;
        for (int i = 1; i <= power; i++) {
            result *= x;
        }
        return result;
    }

    /**
     * General base logarithm.
     *
     * @param x
     * @param base
     * @return
     */
    public static double logarithm(double x, double base) {
        return Math.log(x) / Math.log(base);
    }

    /**
     * Rounds float to given decimals.
     *
     * @param d
     * @param decimals
     * @return
     */
    public static float round(float d, int decimals) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimals, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    public static String formatBytes(long cacheSizeBytes) {
        if (cacheSizeBytes < 1024) {
            return String.format(Locale.US, "%d B", cacheSizeBytes);
        } else if (cacheSizeBytes < 1024 * 1024) {
            long kB = cacheSizeBytes / 1024;
            return String.format(Locale.US, "%d kB", kB);
        } else if (cacheSizeBytes < 1024 * 1024 * 1024) {
            long MB = cacheSizeBytes / (1024 * 1024);
            return String.format(Locale.US, "%d MB", MB);
        } else {
            long GB = cacheSizeBytes / (1024 * 1024 * 1024);
            return String.format(Locale.US, "%d GB", GB);
        }
    }

}
