package cz.mzk.tiledimageview.dev;

import java.util.ArrayList;
import java.util.List;

import cz.mzk.tiledimageview.Point;

/**
 * @author Martin Řehánek
 */
public class DevPoints {

    private final Point center;
    private final List<Point> corners;
    private final List<Point> clickedPoints;

    private Point initialZoomCenter;

    public DevPoints(int imageWidth, int imageHeight) {
        this.center = new Point(imageWidth / 2, imageHeight / 2);
        this.corners = initCorners(imageWidth, imageHeight);
        this.clickedPoints = new ArrayList<Point>();
    }

    private List<Point> initCorners(int imageWidth, int imageHeight) {
        List<Point> corners = new ArrayList<Point>();
        corners.add(new Point(0, 0));
        corners.add(new Point(imageWidth, 0));
        corners.add(new Point(imageWidth, imageHeight));
        corners.add(new Point(0, imageHeight));
        return corners;
    }

    public Point getCenter() {
        return center;
    }

    public List<Point> getCorners() {
        return corners;
    }

    public List<Point> getClickedPoints() {
        return clickedPoints;
    }

    public void addOtherPoint(Point point) {
        clickedPoints.add(point);
    }

    public Point getInitialZoomCenter() {
        return initialZoomCenter;
    }

    public void setInitialZoomCenter(Point initialZoomCenter) {
        this.initialZoomCenter = initialZoomCenter;
    }

}
