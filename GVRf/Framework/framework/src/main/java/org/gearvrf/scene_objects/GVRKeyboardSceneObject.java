/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gearvrf.scene_objects;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.ViewConfiguration;

import org.gearvrf.GVRActivity;
import org.gearvrf.GVRBaseSensor;
import org.gearvrf.GVRBoxCollider;
import org.gearvrf.GVRCollider;
import org.gearvrf.GVRComponent;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRDrawFrameListener;
import org.gearvrf.GVREventListeners;
import org.gearvrf.GVRExternalTexture;
import org.gearvrf.GVRMain;
import org.gearvrf.GVRMaterial;
import org.gearvrf.GVRMesh;
import org.gearvrf.GVRMeshCollider;
import org.gearvrf.GVRPicker;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRTexture;
import org.gearvrf.IActivityEvents;
import org.gearvrf.IKeyboardEvents;
import org.gearvrf.ITouchEvents;
import org.gearvrf.utility.MeshUtils;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  * A {@linkplain GVRSceneObject scene object} that renders a virtual {@link Keyboard}.
 *  It handles rendering of keys and detecting touch movements.
 *
 * See: {@link Keyboard}
 */
public class GVRKeyboardSceneObject extends GVRSceneObject {
    private final GVRActivity mActivity;

    private GVRMesh mKeyboardMesh;
    private GVRMesh mKeyMesh;
    private GVRTexture mKeyboardTexture;
    private Drawable mKeyBackground;
    private int mTextColor;
    private int mKeyboardResId;

    private GVRKeyboard mMainKeyboard;
    private GVRKeyboard mMiniKeyboard;

    private Map<Integer, GVRKeyboard> mGVRKeyboardCache;

    private float mKeyMeshDeepthSize;
    private float mKeyMeshDepthScale;
    private float mKeyMeshDeepthPos;

    private final float mDefaultKeyAnimZOffset;
    private GVRSceneObject mEditableSceneObject;
    private KeyEventsHandler mKeyEventsHandler;
    private GVRPicker mPicker;

    /**
     * Listens to touch events on all objects and hides the keyboard
     * when a touch event is received on something other than
     * the keyboard.
     */
    private ITouchEvents mTouchManager = new ITouchEvents()
    {
        @Override
        public void onTouchStart(GVRSceneObject sceneObject, GVRPicker.GVRPickedObject pickInfo)
        {
            if (sceneObject instanceof GVRKeyboard)
            {
                return;
            }
            if (sceneObject instanceof GVRKey)
            {
                mKeyEventsHandler.onTouchStartKey(mActivity, pickInfo);
            }
            else if (getParent() != null)
            {
                stopInput();
            }
        }

        @Override
        public void onEnter(GVRSceneObject sceneObject, GVRPicker.GVRPickedObject pickInfo)
        {
            if (sceneObject instanceof GVRKey)
            {
                mKeyEventsHandler.onEnterKey(mActivity, pickInfo);
            }
        }

        @Override
        public void onExit(GVRSceneObject sceneObject, GVRPicker.GVRPickedObject pickInfo)
        {
            if (sceneObject instanceof GVRKey)
            {
                mKeyEventsHandler.onExitKey(mActivity, pickInfo);
            }
        }

        @Override
        public void onTouchEnd(GVRSceneObject sceneObject, GVRPicker.GVRPickedObject pickInfo)
        {
            if (sceneObject instanceof GVRKey)
            {
                mKeyEventsHandler.onTouchEndKey(mActivity, pickInfo);
            }
        }

        @Override
        public void onInside(GVRSceneObject sceneObject, GVRPicker.GVRPickedObject pickInfo)
        {
            if (sceneObject instanceof GVRKey)
            {
                MotionEvent event = pickInfo.motionEvent;

                if (event != null)
                {
                    int action = event.getAction();
                    if ((action == MotionEvent.ACTION_CANCEL) || (action == MotionEvent.ACTION_OUTSIDE))
                    {
                        mKeyEventsHandler.onCancel();
                    }
                }
            }
        }

        @Override
        public void onMotionOutside(GVRPicker picker, MotionEvent event)
        {
            mKeyEventsHandler.onCancel();
        }
    };


    private IActivityEvents mActivityEventsHandler = new GVREventListeners.ActivityEvents()
    {
        @Override
        public void dispatchTouchEvent(MotionEvent event)
        {
            int action = event.getAction();
            boolean touched = (action == MotionEvent.ACTION_DOWN) ||
                              (action == MotionEvent.ACTION_MOVE);
            mPicker.processPick(touched, event);
        }
    };

    /**
     * Creates a {@linkplain GVRKeyboardSceneObject keyboard} from the given xml key layout file.
     * Loads an XML description of a keyboard and stores the attributes of the keys.
     * A keyboard consists of rows of keys.
     *
     * @param gvrContext current {@link GVRContext}
     */
    private GVRKeyboardSceneObject(GVRContext gvrContext, int keyboardResId, GVRMesh keyboardMesh,
                                  GVRMesh keyMesh, GVRTexture keyboardTexture,
                                   Drawable keyBackground, int textColor, boolean enableHoverAnim) {
        super(gvrContext);
        mActivity = gvrContext.getActivity();
        mKeyboardMesh = keyboardMesh;
        mKeyMesh = keyMesh;
        mKeyboardTexture = keyboardTexture;
        mKeyBackground = keyBackground;
        mTextColor = textColor;

        if (enableHoverAnim)
            mDefaultKeyAnimZOffset = 0.1f;
        else
            mDefaultKeyAnimZOffset = 0.0f;

        MeshUtils.resize(mKeyboardMesh, 1.0f);
        MeshUtils.resize(mKeyMesh, 1.0f);

        mKeyMeshDeepthSize = MeshUtils.getBoundingSize(mKeyMesh)[2];

        mKeyEventsHandler = new KeyEventsHandler(mActivity.getMainLooper(), this);
        mGVRKeyboardCache = new HashMap<Integer, GVRKeyboard>();

        mEditableSceneObject = null;
        mMiniKeyboard = null;
        mMainKeyboard = null;
        mKeyboardResId = -1;
        setPicker(new GVRPicker(gvrContext, gvrContext.getMainScene()));
        //mActivity.getEventReceiver().addListener(mActivityEventsHandler);
        setKeyboard(keyboardResId);
    }

    /**
     * Establishes which picker should generate touch events for the keyboard.
     * By default, a new picker is created on startup which is attached
     * to the camera and follows the user's gaze. This function lets you
     * associate the keyboard with a specific controller or a custom picker.
     * @param picker GVRPicker used to generate touch events
     * @see org.gearvrf.GVRCursorController#getPicker()
     */
    public void setPicker(GVRPicker picker)
    {
        if (mPicker != null)
        {
            mPicker.getEventReceiver().removeListener(mTouchManager);
            mPicker.getEventReceiver().removeListener(GVRBaseSensor.getPickHandler());
        }
        mPicker = picker;
        if (picker != null)
        {
            EnumSet<GVRPicker.EventOptions> opts = picker.getEventOptions();

            opts.add(GVRPicker.EventOptions.SEND_TOUCH_EVENTS);
            picker.getEventReceiver().addListener(mTouchManager);
            picker.getEventReceiver().addListener(GVRBaseSensor.getPickHandler());
            picker.setEventOptions(opts);
        }
    }

    /**
     * Gets the picker which generates touch events for this keyboard
     * @returns {@link GVRPicker}
     */
    public GVRPicker getPicker() { return mPicker; }

    public void setKeyboard(int keyboardResId) {
        GVRKeyboard gvrKeyboard = mGVRKeyboardCache.get(keyboardResId);
        if (gvrKeyboard != null) {
            setKeyboard(gvrKeyboard.mKeyboard, keyboardResId);
        } else {
            setKeyboard(new Keyboard(mActivity, keyboardResId), keyboardResId);
        }
    }

    public Keyboard getKeyboard() {
        return mMainKeyboard.mKeyboard;
    }

    private void setKeyboard(Keyboard keyboard, int cacheId) {
        if (mMainKeyboard == null || mMainKeyboard.mKeyboard != keyboard) {
            onNewKeyboard(keyboard, cacheId);
        }
    }

    private void onNewKeyboard(Keyboard keyboard, int cacheId) {
        mKeyMeshDepthScale = 1.0f;

        if (keyboard.getKeys().size() > 0) {
            final Keyboard.Key key = keyboard.getKeys().get(0);
            mKeyMeshDepthScale = (float) Math.min(key.width, key.height)
                    / Math.max(keyboard.getMinWidth(), keyboard.getHeight());
        }

        mKeyMeshDeepthPos = mKeyMeshDepthScale * mKeyMeshDeepthSize * 0.5f + 0.02f;

        GVRKeyboard newGVRKeybaord = getGVRKeyboard(keyboard, cacheId);

        addChildObject(newGVRKeybaord);

        if (mMainKeyboard != null && mMainKeyboard.getParent() != null) {
            removeChildObject(mMainKeyboard);
        }

        mMainKeyboard = newGVRKeybaord;
    }

    private GVRKeyboard getGVRKeyboard(Keyboard keyboard, int cacheId) {
        GVRKeyboard gvrKeyboard = mGVRKeyboardCache.get(cacheId);

        if (gvrKeyboard == null) {
            // Keyboard not cached yet
            gvrKeyboard = createGVRKeyboard(keyboard, cacheId);

            mGVRKeyboardCache.put(cacheId, gvrKeyboard);
        }

        return gvrKeyboard;
    }

    private GVRKeyboard createGVRKeyboard(Keyboard keyboard, int cacheId) {
        GVRContext gvrContext = getGVRContext();
        GVRKeyboard gvrKeyboard = new GVRKeyboard(gvrContext, keyboard,
                MeshUtils.clone(getGVRContext(), mKeyboardMesh), cacheId);
        final GVRMaterial material = new GVRMaterial(gvrContext, GVRMaterial.GVRShaderType.Texture.ID);
        material.setMainTexture(mKeyboardTexture);
        gvrKeyboard.getRenderData().setMaterial(material);

        for (Keyboard.Key key: keyboard.getKeys()) {
            final float x = gvrKeyboard.posViewXToScene(key.x + key.width / 2.0f);
            final float y = gvrKeyboard.posViewYToScene(key.y + key.height / 2.0f);
            final float xscale = gvrKeyboard.sizeViewToScene(key.width);
            final float yscale = gvrKeyboard.sizeViewToScene(key.height);
            final float gap = gvrKeyboard.sizeViewToScene(key.gap);

            final GVRBoxCollider collider = new GVRBoxCollider(gvrContext);
            collider.setHalfExtents((xscale + gap) * 0.5f, (yscale + gap) * 0.5f, mKeyMeshDepthScale * 0.5f);
            final GVRMesh mesh = MeshUtils.clone(gvrContext, mKeyMesh);
            MeshUtils.scale(mesh, xscale, yscale, mKeyMeshDepthScale);


            GVRKey gvrKey = new GVRKey(gvrContext, key, mesh, mKeyBackground,
                    mTextColor);
            gvrKey.getTransform().setPosition(x, y, mKeyMeshDeepthPos);
            gvrKey.setHoveredOffset(mKeyMeshDeepthPos, mDefaultKeyAnimZOffset);

            gvrKey.attachComponent(collider);

            gvrKeyboard.addKey(gvrKey);

            gvrKey.onDraw(keyboard.isShifted());
        }

        return gvrKeyboard;
    }

    public void startInput(GVRSceneObject sceneObject) {
        mEditableSceneObject = sceneObject;
        mKeyEventsHandler.start();
        onStartInput(mEditableSceneObject);
    }

    public void stopInput() {
        mKeyEventsHandler.stop();
        onHideMiniKeyboard();
        onClose();
        onStopInput(mEditableSceneObject);
        mEditableSceneObject = null;
    }

    private void enableCollision(boolean enabled) {
        GVRComponent component;
        final List<GVRSceneObject> children = mMainKeyboard.getChildren();

        for (GVRSceneObject child : children) {
            component = child.getComponent(GVRCollider.getComponentType());
            if (component != null) {
                component.setEnable(enabled);
            }
        }
    }

    private boolean isModifierKey(Keyboard.Key key) {
        return key.codes[0] == Keyboard.KEYCODE_MODE_CHANGE
                || key.modifier;
    }

    private void onShowHoveredKey(GVRKey gvrKey, boolean selected) {
        gvrKey.setHovered(selected);

        gvrKey.onDraw(mMainKeyboard.isShifted() || mMainKeyboard.mCapsLocked);
    }

    private void onShowPressedKey(GVRKey gvrKey, boolean pressed, boolean inside) {
        if (pressed) {
            gvrKey.onPressed();
        } else {
            gvrKey.onReleased(inside);
        }

        gvrKey.onDraw(mMainKeyboard.isShifted() || mMainKeyboard.mCapsLocked);
    }

    private boolean isShiftKey(Keyboard.Key key) {
        return key == getShiftKey();
    }

    private Keyboard.Key getShiftKey() {
        final int index = mMainKeyboard.mKeyboard.getShiftKeyIndex();

        if (index > 0) {
            return mMainKeyboard.mKeyboard.getKeys().get(index);
        }

        return null;
    }

    private boolean onShiftMode(GVRKey gvrKey, boolean capsLocked) {
        Keyboard.Key popupKey = gvrKey.getKey();

        if (mMainKeyboard.isShifted()) {
            if (!popupKey.on) {
                mMainKeyboard.setCapsLocked(true);
            } else {
                mMainKeyboard.setShifted(false);
            }
        } else {
            mMainKeyboard.mCapsLocked = capsLocked;
            mMainKeyboard.setShifted(true);

            if (capsLocked) {
                popupKey.on = false;
                gvrKey.onDraw(capsLocked);
            }
        }

        return true;
    }

    private int getCacheId(Keyboard.Key key) {
        if (key.popupCharacters != null) {
            return key.popupCharacters.hashCode();
        }

        return key.popupResId;
    }

    private boolean onChangeMode(GVRKey gvrKey) {
        Keyboard.Key popupKey = gvrKey.getKey();
        Keyboard popupKeyboard = gvrKey.getPopupKeyboard();

        if (popupKeyboard == null || !isModifierKey(popupKey)) {
            return false;
        }
        int cacheId = getCacheId(popupKey);


        setKeyboard(popupKeyboard, cacheId);

        mMainKeyboard.mModifierKey = gvrKey;

        return true;
    }

    private boolean onShowMiniKeyboard(GVRKey gvrKey) {
        Keyboard.Key popupKey = gvrKey.getKey();
        Keyboard popupKeyboard = gvrKey.getPopupKeyboard();


        if (popupKeyboard == null) {
            return false;
        }
        int cacheId = getCacheId(popupKey);
        mMiniKeyboard = getGVRKeyboard(popupKeyboard, cacheId);

        float scale = mMainKeyboard.sizeViewToScene(mMiniKeyboard.mKeyboardSize);
        float x = popupKey.x + popupKey.width + popupKeyboard.getMinWidth() * 0.5f;

        if (x + mMiniKeyboard.mKeyboardWidth * 0.5f > mMainKeyboard.mKeyboardWidth) {
            x = x - ((x + mMiniKeyboard.mKeyboardWidth * 0.5f) - mMainKeyboard.mKeyboardWidth);
        }

        mMiniKeyboard.getTransform().setScale(scale, scale, scale);

        mMiniKeyboard.getTransform().setPosition(
                mMainKeyboard.posViewXToScene(x),
                mMainKeyboard.posViewYToScene(popupKey.y - popupKey.height * 0.8f),
                mMainKeyboard.sizeViewToScene(popupKey.height * 0.5f) + mKeyMeshDeepthPos * 2.0f);

        mMiniKeyboard.mModifierKey = gvrKey;
        mMiniKeyboard.setShifted(mMainKeyboard.isShifted() || mMainKeyboard.mCapsLocked);

        mMainKeyboard.addChildObject(mMiniKeyboard);

        enableCollision(false);
       return true;
    }

    private boolean onHideMiniKeyboard() {
        if (mMiniKeyboard != null) {
            onShowPressedKey(mMiniKeyboard.mModifierKey, false, false);
            mMainKeyboard.removeChildObject(mMiniKeyboard);
            enableCollision(true);
            mMiniKeyboard = null;
            return true;
        }

        return false;
    }

    private boolean onClose() {
        if (getParent() != null)
            getParent().removeChildObject(this);
        return true;
    }

    protected void onStartInput(GVRSceneObject sceneObject) {
        getGVRContext().getEventManager().sendEvent(sceneObject, IKeyboardEvents.class,
                "onStartInput", this);
    }

    protected void onStopInput(GVRSceneObject sceneObject) {
        getGVRContext().getEventManager().sendEvent(sceneObject, IKeyboardEvents.class,
                "onStopInput", this);
    }

    protected void onSendKey(GVRKey gvrKey) {
        if (mEditableSceneObject == null)
            return;

        Keyboard.Key key = gvrKey.getKey();

        getGVRContext().getEventManager().sendEvent(mEditableSceneObject, IKeyboardEvents.class,
                "onKey", this, key.codes[0], key.codes);
    }

    private static class GVRKeyboard extends GVRSceneObject {
        private final Keyboard mKeyboard;
        private final float mKeyboardSize;
        private final float mKeyboardWidth;
        private final float mKeyboardHeight;
        private final int mResId;
        private boolean mCapsLocked;
        private GVRKey mModifierKey;
        private List<GVRKey> mGVRkeys;

        public GVRKeyboard(GVRContext gvrContext, Keyboard keyboard, GVRMesh mesh, int resId) {
            super(gvrContext, mesh);

            mKeyboard = keyboard;

            mKeyboardWidth = keyboard.getMinWidth();
            mKeyboardHeight = keyboard.getHeight();
            mKeyboardSize = Math.max(mKeyboardWidth, mKeyboardHeight);
            mResId = resId;
            mCapsLocked = false;
            mModifierKey = null;
            mGVRkeys = new ArrayList<GVRKey>();

            attachComponent(new GVRMeshCollider(gvrContext, true));

            adjustMesh(60);
        }

        public void addKey(GVRKey gvrKey) {
            addChildObject(gvrKey);
            mGVRkeys.add(gvrKey);
        }

        public void setShifted(boolean shifted) {
            if (!shifted)
                mCapsLocked = false;

            if (mKeyboard.setShifted(shifted)) {
                drawKeys();
            }
        }

        public void setCapsLocked(boolean capsLocked) {
            if (mCapsLocked != capsLocked) {
                mCapsLocked = capsLocked;
                drawKeys();
            }
        }

        public boolean isShifted() {
            return mKeyboard.isShifted();
        }

        public void drawKeys() {
            for (GVRKey gvrKey: mGVRkeys) {
                gvrKey.onDraw(mKeyboard.isShifted() || mCapsLocked);
            }
        }

        public GVRKey getShiftKey() {
            final int index = mKeyboard.getShiftKeyIndex();
            if (index < 0 || index > mGVRkeys.size())
                return null;

            return mGVRkeys.get(index);
        }

        private void adjustMesh(float border) {
            MeshUtils.scale(getRenderData().getMesh(), sizeViewToScene(mKeyboardWidth + border),
                    sizeViewToScene(mKeyboardHeight + border), 1.0f);
        }

        private float posViewXToScene(float value) {
            return (value - mKeyboardWidth / 2) / mKeyboardSize;
        }

        private float posViewYToScene(float value) {
            return ((mKeyboardHeight / 2) - value) / mKeyboardSize;
        }

        private float sizeViewToScene(float value) {
            return value / mKeyboardSize;
        }
    }

    private static class GVRKey extends GVRSceneObject {
        private final Keyboard.Key mKey;
        private final Drawable mBackground;
        private final int mTextColor;
        private final Paint mPaint;
        private Surface mSurface;
        private SurfaceTexture mSurfaceTexture;
        private boolean mIsDirty;
        private boolean mHovered;
        private float mNormalZPos;
        private float mHoveredZOffset;
        private Keyboard mPopupKeyboard;

        private final static int[] KEY_STATE_NORMAL_ON = {
                android.R.attr.state_checkable,
                android.R.attr.state_checked
        };

        private final static int[] KEY_STATE_PRESSED_ON = {
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
                android.R.attr.state_checked
        };

        private final static int[] KEY_STATE_HOVERED_ON = {
                android.R.attr.state_hovered,
                android.R.attr.state_checkable,
                android.R.attr.state_checked
        };

        private final static int[] KEY_STATE_NORMAL_OFF = {
                android.R.attr.state_checkable
        };

        private final static int[] KEY_STATE_PRESSED_OFF = {
                android.R.attr.state_pressed,
                android.R.attr.state_checkable
        };

        private final static int[] KEY_STATE_HOVERED_OFF = {
                android.R.attr.state_hovered,
                android.R.attr.state_checkable
        };

        private final static int[] KEY_STATE_NORMAL = {
        };

        private final static int[] KEY_STATE_PRESSED = {
                android.R.attr.state_pressed
        };

        private final static int[] KEY_STATE_HOVERED = {
                android.R.attr.state_hovered
        };

        public GVRKey(final GVRContext gvrContext, Keyboard.Key key, GVRMesh mesh,
                      Drawable background, int textColor) {
            super(gvrContext, mesh);
            final GVRTexture texture = new GVRExternalTexture(gvrContext);
            final GVRMaterial material = new GVRMaterial(gvrContext, GVRMaterial.GVRShaderType.OES.ID);

            mKey = key;
            mBackground = background;
            mTextColor = textColor;

            mSurfaceTexture = new SurfaceTexture(texture.getId());
            mSurfaceTexture.setDefaultBufferSize(key.width, key.height);
            mSurface = new Surface(mSurfaceTexture);

            material.setMainTexture(texture);
            getRenderData().setMaterial(material);

            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setTextSize(android.R.attr.keyTextSize);
            mPaint.setTextAlign(Paint.Align.CENTER);
            mPaint.setAlpha(255);

            mHovered = false;
            mIsDirty = false;
            mPopupKeyboard = null;

            mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                GVRDrawFrameListener drawFrameListener = new GVRDrawFrameListener() {
                    @Override
                    public void onDrawFrame(float frameTime) {
                        mSurfaceTexture.updateTexImage();
                        gvrContext.unregisterDrawFrameListener(this);
                    }
                };

                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    gvrContext.registerDrawFrameListener(drawFrameListener);
                }
            });
        }

        public Keyboard getPopupKeyboard() {
            if (mPopupKeyboard != null
                    || mKey.popupResId == 0) {
                return mPopupKeyboard;
            }

            if (mKey.popupCharacters != null) {
                mPopupKeyboard = new Keyboard(getGVRContext().getActivity(),
                        mKey.popupResId,
                        mKey.popupCharacters, -1, 0);
            } else {
                mPopupKeyboard = new Keyboard(getGVRContext().getActivity(),
                        mKey.popupResId);
            }

            return mPopupKeyboard;
        }

        public void setPopupKeyboard(Keyboard keyboard) {
            mPopupKeyboard = keyboard;
        }

        private void setHoveredOffset(float normal, float hovered) {
            mNormalZPos = normal;
            mHoveredZOffset = hovered;
        }

        public Keyboard.Key getKey() {
            return mKey;
        }

        public void onPressed() {
            mKey.pressed = true;
        }

        public void onReleased(boolean inside) {
            mKey.pressed = false;

            if (mKey.sticky && inside) {
                mKey.on = !mKey.on;
            }
        }

        public void setHovered(boolean hovered) {
            mHovered = hovered;
        }


        public int[] getCurrentDrawableState(boolean isShifted) {
            int[] states = KEY_STATE_NORMAL;

            if (mKey.on) {
                if (mKey.pressed) {
                    states = KEY_STATE_PRESSED_ON;
                } else if (mHovered) {
                    states = KEY_STATE_HOVERED_ON;
                } else {
                    states = KEY_STATE_NORMAL_ON;
                }
            } else {
                if (mKey.sticky) {
                    if (mKey.pressed
                            || (mKey.codes[0] == Keyboard.KEYCODE_SHIFT && isShifted)) {
                        states = KEY_STATE_PRESSED_OFF;
                    } else if (mHovered) {
                        states = KEY_STATE_HOVERED_OFF;
                    } else {
                        states = KEY_STATE_NORMAL_OFF;
                    }
                } else {
                    if (mKey.pressed) {
                        states = KEY_STATE_PRESSED;
                    } else if (mHovered) {
                        states = KEY_STATE_HOVERED;
                    }
                }
            }
            return states;
        }

        //TODO: Fix cause of concurrency calling onDraw
        // Can called by touch events at UI Thread or hover events at GL Thread
        public synchronized void onDraw(boolean isShifted) {
            final Paint paint = mPaint;
            final Keyboard.Key key = mKey;
            final Drawable background = mBackground;
            int[] drawableState = this.getCurrentDrawableState(isShifted);
            final Rect bounds = background.getBounds();

            background.setState(drawableState);

            if (key.width != bounds.right ||
                    key.height != bounds.bottom) {
                background.setBounds(0, 0, key.width, key.height);
            }

            Canvas canvas = mSurface.lockCanvas(null);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            background.draw(canvas);

            paint.setFakeBoldText(true);

            if (mKey.on || (mKey.codes[0] == Keyboard.KEYCODE_SHIFT && isShifted)) {
                paint.setColor(Color.rgb(255 - Color.red(mTextColor),
                        255 - Color.green(mTextColor), 255 - Color.blue(mTextColor)));
            } else {
                paint.setColor(mTextColor);
            }

            if (key.label != null) {
                String label = key.label.toString();

                if (isShifted && label.length() < 3
                        && Character.isLowerCase(label.charAt(0))) {
                    label = label.toString().toUpperCase();
                }

                // For characters, use large font. For labels like "Done", use small font.
                if (label.length() > 1 && key.codes.length < 2) {
                    paint.setTextSize(14 * 5);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    paint.setTextSize(18 * 5);
                    paint.setTypeface(Typeface.DEFAULT);
                }

                canvas.drawText(label,
                        key.width / 2,
                        key.height / 2 + (paint.getTextSize() - paint.descent()) / 2.0f, paint);
            } else if (key.icon != null) {
                key.icon.setFilterBitmap(true);
                key.icon.setBounds( (key.width - key.icon.getIntrinsicWidth())/2,
                        (key.height - key.icon.getIntrinsicHeight()) / 2,
                        (key.width  +  key.icon.getIntrinsicWidth()) / 2,
                        (key.height + key.icon.getIntrinsicHeight()) / 2);
                key.icon.draw(canvas);
            }

            mSurface.unlockCanvasAndPost(canvas);

            if (mKey.pressed || !mHovered) {
                getTransform().setPositionZ(mNormalZPos);
            } else {
                getTransform().setPositionZ(mNormalZPos + mHoveredZOffset);
            }
        }
    }

    private static class KeyEventsHandler extends Handler
    {
        private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
        private static final int REPEAT_TIMEOUT = ViewConfiguration.getKeyRepeatTimeout();
        private static final int REPEAT_DELAY = ViewConfiguration.getKeyRepeatDelay();

        private static final int SHOW_LONG_PRESS = 3;
        private static final int MSG_REPEAT = 4;

        private boolean mIsProcessing = false;
        GVRKeyboardSceneObject mGvrKeyboard;
        GVRKey mSelectedKey;
        GVRKey mPressedKey;

        static class KeyEventDispatcher implements Runnable
        {
            public GVRKey HitKey;

            public void run() { };
        }

        private KeyEventDispatcher mOnEnterKey = new KeyEventDispatcher()
        {
            public void run()
            {
                onKeyHovered(HitKey, true);
            }
        };


        private Runnable mOnTouchStartKey = new Runnable()
        {
            public void run()
            {
                if (mSelectedKey != null) {
                    onKeyPress(mSelectedKey, true);
                }
            }
        };

        private Runnable mOnTouchEndKey = new Runnable()
        {
            public void run()
            {
                if (mPressedKey != null) {
                    onKeyPress(mPressedKey, false);
                }
            }
        };

        private KeyEventDispatcher mOnExitKey = new KeyEventDispatcher()
        {
            public void run()
            {
                onKeyHovered(HitKey, false);
                if (mPressedKey != null)
                {
                    onKeyPress(mPressedKey, false);
                }
            }
        };

        public KeyEventsHandler(Looper loop, GVRKeyboardSceneObject gvrKeyboard) {
            super(loop);
            mGvrKeyboard = gvrKeyboard;
        }

        public void start() {
            mIsProcessing = true;
            mSelectedKey = null;
            mPressedKey = null;
        }

        public void stop() {
            onCancel();
        }

        @Override
        public void handleMessage(Message msg) {
            if (mGvrKeyboard == null)
                return;

            switch (msg.what) {
                case SHOW_LONG_PRESS:
                    if (mPressedKey != null && mPressedKey == mSelectedKey) {
                        onLongPress(mPressedKey);
                    }
                    break;
                case MSG_REPEAT:
                    if (onRepeatKey()) {
                        sendEmptyMessageDelayed(MSG_REPEAT, REPEAT_DELAY);
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown message " + msg); //never
            }
        }

        public void onEnterKey(Activity activity, GVRPicker.GVRPickedObject pickInfo) {
            mOnEnterKey.HitKey = (GVRKey) pickInfo.hitObject;
            activity.runOnUiThread(mOnEnterKey);
        }

        public void onExitKey(Activity activity, GVRPicker.GVRPickedObject pickInfo) {
            mOnExitKey.HitKey = (GVRKey) pickInfo.hitObject;
            activity.runOnUiThread(mOnExitKey);
       }

        public void onTouchStartKey(Activity activity, GVRPicker.GVRPickedObject pickInfo) {
            activity.runOnUiThread(mOnTouchStartKey);
        }

        public void onTouchEndKey(Activity activity, GVRPicker.GVRPickedObject pickInfo) {
            activity.runOnUiThread(mOnTouchEndKey);
        }

        public void onCancel()
        {
            if (mIsProcessing)
            {
                mIsProcessing = false;
                mSelectedKey = null;
                mPressedKey = null;
                mGvrKeyboard.stopInput();
            }
        }

        public void onKeyHovered(GVRKey gvrKey, boolean hovered) {
            if (mGvrKeyboard == null) {
                return;
            }

            if (mPressedKey == null || mPressedKey == gvrKey) {
                mGvrKeyboard.onShowHoveredKey(gvrKey, hovered);
            }

            if (hovered) {
                mSelectedKey = gvrKey;
            } else if (mSelectedKey == gvrKey) {
                mSelectedKey = null;
            }
        }

        public boolean onRepeatKey() {
             if (mGvrKeyboard == null || mPressedKey == null
                     || mPressedKey != mSelectedKey
                     || !mPressedKey.mKey.repeatable) {
                return false;
            }

            mGvrKeyboard.onSendKey(mPressedKey);

            return true;
        }

        public void onKeyPress(GVRKey gvrKey, boolean pressed) {
            if (mGvrKeyboard == null) {
                return;
            }

            if (pressed) {
                mPressedKey = gvrKey;
                if (gvrKey.mKey.repeatable) {
                    sendEmptyMessageDelayed(MSG_REPEAT, REPEAT_TIMEOUT);
                } else {
                    sendEmptyMessageDelayed(SHOW_LONG_PRESS, LONGPRESS_TIMEOUT);
                }

                mGvrKeyboard.onShowPressedKey(gvrKey, true, true);
            } else {
                boolean isLongPress = !hasMessages(SHOW_LONG_PRESS);

                mPressedKey = null;
                removeMessages(REPEAT_TIMEOUT);
                removeMessages(SHOW_LONG_PRESS);

                if (gvrKey == mSelectedKey) {
                    mGvrKeyboard.onShowPressedKey(gvrKey, false, true);

                    if (gvrKey.mKey.codes[0] == Keyboard.KEYCODE_SHIFT) {
                        mGvrKeyboard.onShiftMode(gvrKey, isLongPress);
                    } else if (mGvrKeyboard.isModifierKey(gvrKey.mKey)) {
                        if (mGvrKeyboard.mMainKeyboard.isShifted()
                                && !mGvrKeyboard.mMainKeyboard.mCapsLocked) {
                            mGvrKeyboard.mMainKeyboard.setShifted(false);
                        }

                        mGvrKeyboard.onChangeMode(gvrKey);
                    } else {
                        mGvrKeyboard.onSendKey(gvrKey);

                        if (mGvrKeyboard.mMainKeyboard.isShifted()
                                && !mGvrKeyboard.mMainKeyboard.mCapsLocked) {
                            mGvrKeyboard.mMainKeyboard.setShifted(false);
                        }
                    }
                } else {
                    mGvrKeyboard.onShowPressedKey(gvrKey, false, false);

                    if (mGvrKeyboard.mMainKeyboard.isShifted()
                            && !mGvrKeyboard.mMainKeyboard.mCapsLocked) {
                        mGvrKeyboard.mMainKeyboard.setShifted(false);
                    }
                }

                if (mGvrKeyboard.mMiniKeyboard != null) {
                    if (mSelectedKey != null) {
                        mGvrKeyboard.onShowHoveredKey(mSelectedKey, false);
                    }

                    mGvrKeyboard.onHideMiniKeyboard();
                    mSelectedKey = null;
                }

                if (mSelectedKey != null) {
                    // FIXME: Check mode change
                    mGvrKeyboard.onShowHoveredKey(mSelectedKey, true);
                }
            }
        }

        public void onLongPress(GVRKey gvrKey) {
            if (mGvrKeyboard == null)
                return;

            if (gvrKey.mKey.popupResId == 0
                    || mGvrKeyboard.isModifierKey(gvrKey.mKey)) {
                return;
            }

            mPressedKey = null;

            mGvrKeyboard.onShowMiniKeyboard(gvrKey);
        }
    }

    /**
     * Builder for the {@link GVRKeyboardSceneObject}.
     */
    public static class Builder {
        private GVRMesh keyboardMesh;
        private GVRMesh keyMesh;
        private GVRTexture keyboardTexture;
        private Drawable keyBackground;
        private boolean keyHoveredAnimated;
        private int textColor;

        /**
         * Creates a builder for the {@link GVRKeyboardSceneObject}.
         */
        public Builder() {
            this.keyboardMesh = null;
            this.keyMesh = null;
            this.keyboardTexture = null;
            this.keyBackground = null;
            this.keyHoveredAnimated = true;
            this.textColor = Color.BLACK;
        }

        public Builder setKeyboardMesh(GVRMesh keyboardMesh) {
            this.keyboardMesh = keyboardMesh;
            return this;
        }

        public Builder setKeyMesh(GVRMesh keyMesh) {
            this.keyMesh = keyMesh;
            return this;
        }

        public Builder setKeyboardTexture(GVRTexture keyboardTexture) {
            this.keyboardTexture = keyboardTexture;
            return this;
        }

        public Builder setKeyBackground(Drawable keyBackground) {
            this.keyBackground = keyBackground;
            return this;
        }

        public Builder enableKeyHoverAnimation(boolean enabled) {
            this.keyHoveredAnimated = enabled;
            return this;
        }

        public Builder setTextColor(int color) {
            this.textColor = color;
            return this;
        }

        public GVRKeyboardSceneObject build(GVRContext gvrContext, int keyboardResId) {
            if (keyboardMesh == null) {
                keyboardMesh = MeshUtils.createQuad(gvrContext, 1.0f, 1.0f);
            }
            if (keyMesh == null) {
                keyMesh = MeshUtils.createQuad(gvrContext, 1.0f, 1.0f);
            }

            if (keyboardTexture == null) {
                throw new IllegalArgumentException("Keyboard's texture should not be null.");
            }

            if (keyBackground == null) {
                throw new IllegalArgumentException("Key's texture should not be null.");
            }

            return new GVRKeyboardSceneObject(gvrContext, keyboardResId, this.keyboardMesh,
                    this.keyMesh, this.keyboardTexture, this.keyBackground,
                    this.textColor, keyHoveredAnimated);
        }
    }

}
