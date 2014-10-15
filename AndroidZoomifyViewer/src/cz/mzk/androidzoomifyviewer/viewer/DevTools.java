package cz.mzk.androidzoomifyviewer.viewer;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import cz.mzk.androidzoomifyviewer.R;

/**
 * @author Martin Řehánek
 * 
 */
public class DevTools {

	private static final String TAG = DevTools.class.getSimpleName();

	private Canvas mCanv;

	private final Paint paintBlue;
	private final Paint paintRed;
	private final Paint paintYellow;
	private final Paint paintGreen;
	private final Paint paintBlack;
	private final Paint paintWhite;

	private final Paint paintRedTrans;
	private final Paint paintYellowTrans;
	private final Paint paintBlackTrans;

	public DevTools(Context context) {
		// init paints
		paintBlue = new Paint();
		paintBlue.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_blue));
		paintRed = new Paint();
		paintRed.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_red));
		paintYellow = new Paint();
		paintYellow.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_yellow));
		paintGreen = new Paint();
		paintGreen.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_green));
		paintBlack = new Paint();
		paintBlack.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_black));
		paintWhite = new Paint();
		paintWhite.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_white));
		// transparent
		paintRedTrans = new Paint();
		paintRedTrans.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_red_trans));
		paintYellowTrans = new Paint();
		paintYellowTrans.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_yellow_trans));
		paintBlackTrans = new Paint();
		paintBlackTrans.setColor(context.getResources().getColor(R.color.androidzoomifyviewer_black_trans));
	}

	public void fillWholeCanvasWithColor(Paint paint) {
		Rect wholeCanvas = new Rect(0, 0, mCanv.getWidth(), mCanv.getHeight());
		mCanv.drawRect(wholeCanvas, paint);
	}

	public void fillRectAreaWithColor(Rect rect, Paint paint) {
		mCanv.drawRect(rect, paint);
	}

	public void drawPoint(PointD pointInCanvas, Paint paint, float size) {
		mCanv.drawCircle((float) pointInCanvas.x, (float) pointInCanvas.y, size, paint);
	}

	public void drawImageCoordPoints(ImageCoordsPoints testPoints, double resizeFactor, VectorD imageShiftInCanvas) {
		drawImageCoordPoint(testPoints.getCenter(), resizeFactor, imageShiftInCanvas, paintYellow);
		for (Point corner : testPoints.getCorners()) {
			drawImageCoordPoint(corner, resizeFactor, imageShiftInCanvas, paintYellow);
		}
	}

	private void drawImageCoordPoint(Point point, double resizeFactor, VectorD imageShiftInCanvas, Paint paint) {
		int resizedAndShiftedX = (int) (point.x * resizeFactor + imageShiftInCanvas.x);
		int resizedAndShiftedY = (int) (point.y * resizeFactor + imageShiftInCanvas.y);
		mCanv.drawCircle(resizedAndShiftedX, resizedAndShiftedY, 15f, paint);
	}

	public void drawZoomCenters(PointD gestureCenterInCanvas, PointD zoomCenterInImage, double resizeFactor,
			VectorD totalShift) {
		if (gestureCenterInCanvas != null && zoomCenterInImage != null) {
			PointD zoomCenterInImageInCanvasCoords = Utils.toCanvasCoords(zoomCenterInImage, resizeFactor, totalShift);
			mCanv.drawCircle((float) zoomCenterInImageInCanvasCoords.x, (float) zoomCenterInImageInCanvasCoords.y,
					15.0f, paintGreen);
			mCanv.drawCircle((float) gestureCenterInCanvas.x, (float) gestureCenterInCanvas.y, 12.0f, paintRed);
			mCanv.drawLine((float) zoomCenterInImageInCanvasCoords.x, (float) zoomCenterInImageInCanvasCoords.y,
					(float) gestureCenterInCanvas.x, (float) gestureCenterInCanvas.y, paintRed);
		}
	}

	public void highlightTile(Rect rect, Paint paint) {
		// vertical borders
		mCanv.drawLine(rect.left, rect.top, rect.left, rect.bottom, paint);
		mCanv.drawLine(rect.right, rect.top, rect.right, rect.bottom, paint);
		// horizontal borders
		mCanv.drawLine(rect.left, rect.top, rect.right, rect.top, paint);
		mCanv.drawLine(rect.left, rect.bottom, rect.left, rect.bottom, paint);
		// diagonals
		mCanv.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint);
		mCanv.drawLine(rect.right, rect.top, rect.left, rect.bottom, paint);
		// center
		int centerX = (int) (rect.left + (rect.right - rect.left) / 2.0);
		int centerY = (int) (rect.top + (double) (rect.bottom - rect.top) / 2.0);
		mCanv.drawCircle((float) centerX, (float) centerY, 7.0f, paint);
	}

	public void setCanvas(Canvas canv) {
		this.mCanv = canv;
	}

	public Paint getPaintBlue() {
		return paintBlue;
	}

	public Paint getPaintRed() {
		return paintRed;
	}

	public Paint getPaintYellow() {
		return paintYellow;
	}

	public Paint getPaintGreen() {
		return paintGreen;
	}

	public Paint getPaintBlack() {
		return paintBlack;
	}

	public Paint getPaintWhite() {
		return paintWhite;
	}

	public Paint getPaintRedTrans() {
		return paintRedTrans;
	}

	public Paint getPaintYellowTrans() {
		return paintYellowTrans;
	}

	public Paint getPaintBlackTrans() {
		return paintBlackTrans;
	}

	private final List<RectWithPaint> rectStackAfterPrimaryDraws = new ArrayList<RectWithPaint>();

	public void clearRectStack() {
		rectStackAfterPrimaryDraws.clear();
	}

	public void addToRectStack(RectWithPaint rect) {
		rectStackAfterPrimaryDraws.add(rect);
	}

	public void drawRectStack() {
		for (RectWithPaint rect : rectStackAfterPrimaryDraws) {
			fillRectAreaWithColor(rect.getRect(), rect.getPaint());
		}
	}

	public static class RectWithPaint {
		private final Rect rect;
		private final Paint paint;

		public RectWithPaint(Rect rect, Paint paint) {
			super();
			this.rect = rect;
			this.paint = paint;
		}

		public Rect getRect() {
			return rect;
		}

		public Paint getPaint() {
			return paint;
		}

	}

}
