package de.fhws.indoor.maprenderer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
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

    private final TwoFingerGestureDetector mTwoFingerGestureDetector;

    private float mLastTouchX;
    private float mLastTouchY;

    private final Matrix mModelMatrix = new Matrix();
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

        mTwoFingerGestureDetector = new TwoFingerGestureDetector();

        mModelMatrix.setScale(10, 10);
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
        mMVMatrix.postConcat(mModelMatrix);
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
        if(mTwoFingerGestureDetector.onTouchEvent(ev)) { return true; } // rotation scale detector consumed event

        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();

                float[] vec = {x, y};
                mMVMatrixInverse.mapPoints(vec);

                mLastTouchX = vec[0];
                mLastTouchY = vec[1];

                // Save the ID of this pointer
                mActivePointerId = ev.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // Find the index of the active pointer and fetch its position
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);

                float[] preTransformPoint = {ev.getX(pointerIndex), ev.getY(pointerIndex)};
                mMVMatrixInverse.mapPoints(preTransformPoint);

                final float dx = preTransformPoint[0] - mLastTouchX;
                final float dy = preTransformPoint[1] - mLastTouchY;

                Matrix offset = new Matrix();
                offset.postTranslate(dx, dy);
                mModelMatrix.preConcat(offset);
                updateMVMatrix();

                float[] postTransformPoint = {ev.getX(pointerIndex), ev.getY(pointerIndex)};
                mMVMatrixInverse.mapPoints(postTransformPoint);
                mLastTouchX = postTransformPoint[0];
                mLastTouchY = postTransformPoint[1];

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
                    float[] vec = {ev.getX(newPointerIndex), ev.getY(newPointerIndex)};
                    mMVMatrixInverse.mapPoints(vec);
                    mLastTouchX = vec[0];
                    mLastTouchY = vec[1];
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                } else {
                    float[] vec = {ev.getX(mActivePointerId), ev.getY(mActivePointerId)};
                    mMVMatrixInverse.mapPoints(vec);
                    mLastTouchX = vec[0];
                    mLastTouchY = vec[1];
                }
                break;
            }
        }
        return true;
    }

    private class TwoFingerGestureDetector {
        private static final int INVALID_POINTER_ID = -1;
        private float x1, y1, x2, y2;
        private int ptrID1 = INVALID_POINTER_ID, ptrID2 = INVALID_POINTER_ID;

        public TwoFingerGestureDetector(){
        }

        public boolean onTouchEvent(MotionEvent event){
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    ptrID1 = event.getPointerId(event.getActionIndex());
                    break;
                case MotionEvent.ACTION_POINTER_DOWN: {
                    ptrID2 = event.getPointerId(event.getActionIndex());
                    setCurrentFingerState(event);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (ptrID1 != INVALID_POINTER_ID && ptrID2 != INVALID_POINTER_ID && ptrID1 != ptrID2) {
                        float nx2, ny2, nx1, ny1;
                        nx1 = event.getX(event.findPointerIndex(ptrID1));
                        ny1 = event.getY(event.findPointerIndex(ptrID1));
                        nx2 = event.getX(event.findPointerIndex(ptrID2));
                        ny2 = event.getY(event.findPointerIndex(ptrID2));
                        float[] vec = {nx1, ny1, nx2, ny2};
                        mMVMatrixInverse.mapPoints(vec);
                        nx1 = vec[0];
                        ny1 = vec[1];
                        nx2 = vec[2];
                        ny2 = vec[3];
                        float cx = (x1 + x2)/2, cy = (y1 + y2)/2;
                        float ncx = (nx2 + nx1)/2, ncy = (ny2 + ny1)/2;

                        float rotation = angleBetweenLines(x1, y1, x2, y2, nx1, ny1, nx2, ny2);
                        float scale = Math.max(0.01f, Math.min(10.0f, scaleBetweenLines(x1, y1, x2, y2, nx1, ny1, nx2, ny2)));
                        float dx = ncx - cx, dy = ncy - cy;


                        Matrix offset = new Matrix();
                        offset.postTranslate(-cx, -cy);
                        offset.postRotate(rotation);
                        offset.postScale(scale, scale);
                        offset.postTranslate(cx, cy);
                        offset.postTranslate(dx, dy);
                        mModelMatrix.preConcat(offset);
                        invalidate();

                        updateMVMatrix();
                        setCurrentFingerState(event);

                        return true;
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                    ptrID1 = INVALID_POINTER_ID;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    ptrID2 = INVALID_POINTER_ID;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    ptrID1 = INVALID_POINTER_ID;
                    ptrID2 = INVALID_POINTER_ID;
                    break;
            }
            return false;
        }

        private float angleBetweenLines (float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY) {
            float angle1 = (float) Math.atan2( (fY - sY), (fX - sX) );
            float angle2 = (float) Math.atan2( (nfY - nsY), (nfX - nsX) );

            float angle = ((float)Math.toDegrees(angle2 - angle1)) % 360;
            if (angle < -180.f) angle += 360.0f;
            if (angle > 180.f) angle -= 360.0f;
            return angle;
        }

        private float scaleBetweenLines(float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY) {
            return lineLength(nfX, nfY, nsX, nsY) / lineLength(fX, fY, sX, sY);
        }

        private float lineLength(float ox, float oy, float tx, float ty) {
            float dx = tx - ox;
            float dy = ty - oy;
            return (float) Math.sqrt(dx*dx + dy*dy);
        }

        private void setCurrentFingerState(MotionEvent event) {
            x1 = event.getX(event.findPointerIndex(ptrID1));
            y1 = event.getY(event.findPointerIndex(ptrID1));
            x2 = event.getX(event.findPointerIndex(ptrID2));
            y2 = event.getY(event.findPointerIndex(ptrID2));
            float[] vec = {x1, y1, x2, y2};
            mMVMatrixInverse.mapPoints(vec);
            x1 = vec[0];
            y1 = vec[1];
            x2 = vec[2];
            y2 = vec[3];
        }
    }
}
