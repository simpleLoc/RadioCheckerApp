package de.fhws.indoor.maprenderer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import de.fhws.indoor.radiochecker.R;
import de.fhws.indoor.xmlmapparser.AccessPoint;
import de.fhws.indoor.xmlmapparser.Beacon;
import de.fhws.indoor.xmlmapparser.Floor;
import de.fhws.indoor.xmlmapparser.Map;
import de.fhws.indoor.xmlmapparser.UWBAnchor;
import de.fhws.indoor.xmlmapparser.Vec2;
import de.fhws.indoor.xmlmapparser.Wall;

import java.util.Optional;

public class MapView extends View {
    private final Paint wallPaint;
    private final Paint unseenPaint;
    private final Paint seenPaint;

    // this is required to draw small texts with text sizes around 1dp
    private final float textScale = 16.f;

    private static final int INVALID_POINTER_ID = 1;
    private int mActivePointerId = INVALID_POINTER_ID;

    private float mScaleFactor = 10;
    private final ScaleGestureDetector mScaleDetector;

    private float mPosX;
    private float mPosY;
    private float mLastTouchX;
    private float mLastTouchY;

    private final Matrix mModelTranslate = new Matrix();
    private final Matrix mModelTranslateInverse = new Matrix();
    private final Matrix mModelScale = new Matrix();
    private final Matrix mViewMatrix = new Matrix();
    private final Matrix mMVMatrix = new Matrix();
    private final Matrix mMVMatrixInverse = new Matrix();

    private Map map = null;
    private Floor floor = null;

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);

        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(getResources().getColor(R.color.wallColor, context.getTheme()));

        unseenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unseenPaint.setColor(getResources().getColor(R.color.unseenColor, context.getTheme()));
        unseenPaint.setTextSize(8);

        seenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        seenPaint.setColor(getResources().getColor(R.color.seenColor, context.getTheme()));
        seenPaint.setTextSize(16);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());

        setPosition(new Vec2(0, 0));
        setScale(mScaleFactor);
    }

    public void setMap(Map map) {
        this.map = map;
        if (this.map == null) { return; }

        Optional<Floor> first = map.getFloors().values().stream().findFirst();
        floor = first.orElse(null);
        invalidate();
    }

    public void selectFloor(String name) {
        if (map == null) { return; }
        floor = map.getFloors().getOrDefault(name, floor);
        invalidate();
    }

    public void setPosition(Vec2 position) {
        mPosX = position.x;
        mPosY = position.y;
        mModelTranslate.setTranslate(mPosX, mPosY);
        mModelTranslate.invert(mModelTranslateInverse);
        updateMVMatrix();
    }

    public void setScale(float scale) {
        mScaleFactor = scale;
        mModelScale.setScale(mScaleFactor, mScaleFactor);
        updateMVMatrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mViewMatrix.reset();
        mViewMatrix.preTranslate(getWidth()/2f, getHeight()/2f);
        mViewMatrix.preScale(1, -1);
        updateMVMatrix();

        canvas.setMatrix(mMVMatrix);
        super.onDraw(canvas);

        if (map == null || floor == null) { return; }

        floor.getWalls().forEach(wall -> drawWall(wall, canvas));
        floor.getBeacons().values().forEach(beacon -> drawBeacon(beacon, canvas));
        floor.getUwbAnchors().values().forEach(uwbAnchor -> drawUWB(uwbAnchor, canvas));
        floor.getAccessPoints().values().forEach(accessPoint -> drawAP(accessPoint, canvas));
    }

    private void updateMVMatrix() {
        mMVMatrix.reset();
        mMVMatrix.postConcat(mModelTranslateInverse);
        mMVMatrix.postConcat(mModelScale);
        mMVMatrix.postConcat(mModelTranslate);
        mMVMatrix.postConcat(mViewMatrix);
        mMVMatrix.invert(mMVMatrixInverse);
    }

    private void drawWall(Wall wall, Canvas canvas) {
        Vec2 p0p1 = wall.p1.sub(wall.p0);
        Vec2 halfWidth = p0p1.normalized().getPerpendicular().mul(wall.thickness/2);
        Vec2 fullWidth = halfWidth.mul(2);

        Vec2 p0 = wall.p0.add(halfWidth);
        Vec2 p1 = p0.sub(fullWidth);
        Vec2 p2 = p1.add(p0p1);
        Vec2 p3 = p2.add(fullWidth);

        float[] pts = {
                p0.x, p0.y,
                p1.x, p1.y,
                p1.x, p1.y,
                p2.x, p2.y,
                p2.x, p2.y,
                p3.x, p3.y,
                p3.x, p3.y,
                p0.x, p0.y
        };
        //canvas.drawLines(pts, wallPaint);
        canvas.drawRect(p0.x, p0.y, p2.x, p2.y, wallPaint);
    }

    private void drawBeacon(Beacon beacon, Canvas canvas) {
        final float size = (float) Math.sqrt(0.15 * 0.15 * 2);
        Vec2 dir = new Vec2(0, size);
        final Vec2 p0 = new Vec2(beacon.position.x, beacon.position.y).add(dir);
        dir = dir.rotated(Math.PI/2);
        final Vec2 p1 = new Vec2(beacon.position.x, beacon.position.y).add(dir);
        dir = dir.rotated(Math.PI/2);
        final Vec2 p2 = new Vec2(beacon.position.x, beacon.position.y).add(dir);
        dir = dir.rotated(Math.PI/2);
        final Vec2 p3 = new Vec2(beacon.position.x, beacon.position.y).add(dir);

        Path path = new Path();
        path.moveTo(p0.x, p0.y);
        path.lineTo(p1.x, p1.y);
        path.lineTo(p2.x, p2.y);
        path.lineTo(p3.x, p3.y);
        path.close();
        canvas.drawPath(path, beacon.seen ? seenPaint : unseenPaint);

        if (!beacon.seen) {
            // flip y axis to draw text
            canvas.scale(1/textScale, -1/textScale);
            canvas.drawText(beacon.name,
                    (beacon.position.x + 0.15f) * textScale,
                    (-beacon.position.y - 0.15f) * textScale,
                    unseenPaint);
            canvas.scale(textScale, -textScale);
        }
    }

    private void drawUWB(UWBAnchor uwbAnchor, Canvas canvas) {
        final float size = 0.15f;
        canvas.drawRect(uwbAnchor.position.x - size, uwbAnchor.position.y + size,
                uwbAnchor.position.x + size, uwbAnchor.position.y - size,
                uwbAnchor.seen ? seenPaint : unseenPaint);

        // flip y axis to draw text
        if (!uwbAnchor.seen) {
            canvas.scale(1/textScale, -1/textScale);
            float height = unseenPaint.descent() - unseenPaint.ascent();
            canvas.drawText(uwbAnchor.name,
                    (uwbAnchor.position.x + 0.15f) * textScale,
                    -uwbAnchor.position.y * textScale + height,
                    unseenPaint);
            canvas.scale(textScale, -textScale);
        }
    }

    private void drawAP(AccessPoint accessPoint, Canvas canvas) {
        canvas.drawCircle(accessPoint.position.x, accessPoint.position.y, 0.15f,
                accessPoint.seen ? seenPaint : unseenPaint);

        // flip y axis to draw text
        if (!accessPoint.seen) {
            canvas.scale(1/textScale, -1/textScale);
            canvas.drawText(accessPoint.name,
                    accessPoint.position.x * textScale - unseenPaint.measureText(accessPoint.name),
                    (-accessPoint.position.y - 0.15f) * textScale,
                    unseenPaint);
            canvas.scale(textScale, -textScale);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);

        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();

                mLastTouchX = x;
                mLastTouchY = y;

                // Save the ID of this pointer
                mActivePointerId = ev.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // Find the index of the active pointer and fetch its position
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                final float dx = x - mLastTouchX;
                final float dy = y - mLastTouchY;
                float[] vec = {dx, dy};
                mMVMatrixInverse.mapVectors(vec);
                setPosition(new Vec2(mPosX - vec[0], mPosY - vec[1]));

                mLastTouchX = x;
                mLastTouchY = y;

                invalidate();
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                // Extract the index of the pointer that left the touch sensor
                final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mLastTouchX = ev.getX(newPointerIndex);
                    mLastTouchY = ev.getY(newPointerIndex);
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            setScale(Math.max(10f, Math.min(mScaleFactor * detector.getScaleFactor(), 500f)));
            invalidate();

            return true;
        }
    }
}
