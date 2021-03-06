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

package org.gearvrf.utility;

import org.gearvrf.GVRAndroidResource;
import org.gearvrf.GVRAndroidResource.TextureCallback;
import org.gearvrf.GVRAndroidResource.BitmapTextureCallback;
import org.gearvrf.GVRAndroidResource.CompressedTextureCallback;
import org.gearvrf.GVRHybridObject;
import org.gearvrf.GVRAndroidResource.Callback;
import org.gearvrf.GVRAndroidResource.CancelableCallback;
import org.gearvrf.GVRImage;

/**
 * Basic cache-by-resource-description.
 * 
 * Keeps system from reloading resources, so long as a previous copy is still in
 * memory. Generic, so there can be separate caches for meshes and textures: a
 * 'unified cache' (mapping resource to hybrid-object) with hooks in
 * {@link org.gearvrf.asynchronous.Throttler Throttler} would not be safe.
 * Passing the descriptor for a cached mesh to a get-texture call would return
 * the mesh ....
 * 
 * @since 2.0.2
 */
public class ResourceCache<T extends GVRHybridObject> extends ResourceCacheBase {
    private static final String TAG = Log.tag(ResourceCache.class);

    /** Save a weak reference to the resource */
    public void put(GVRAndroidResource androidResource, T resource) {
        Log.d(TAG, "put resource %s to cache", androidResource);

        super.put(androidResource, resource);
    }

    /** Get the cached resource, or {@code null} */
    public T get(GVRAndroidResource androidResource) {
        return (T) super.get(androidResource);
    }

    /**
     * Wrap the callback, to cache the
     * {@link Callback#loaded(GVRHybridObject, GVRAndroidResource) loaded()}
     * resource
     */
    public Callback<T> wrapCallback(Callback<T> callback) {
        return new CallbackWrapper<T>(this, callback);
    }

    /**
     * Wrap the callback, to cache the
     * {@link CancelableCallback#loaded(GVRHybridObject, GVRAndroidResource)
     * loaded()} resource
     */
    public CancelableCallback<T> wrapCallback(CancelableCallback<T> callback) {
        return new CancelableCallbackWrapper<T>(this, callback);
    }

    /**
     * Wrap the callback, to cache the
     * {@link CompressedTextureCallback#loaded(GVRHybridObject, GVRAndroidResource)
     * loaded()} resource
     */
    public static CompressedTextureCallback wrapCallback(
            ResourceCache<GVRImage> cache, CompressedTextureCallback callback) {
        return new CompressedTextureCallbackWrapper(cache, callback);
    }

    /**
     * Wrap the callback, to cache the
     * {@link BitmapTextureCallback#loaded(GVRHybridObject, GVRAndroidResource)
     * loaded()} resource
     */
    public static BitmapTextureCallback wrapCallback(
            ResourceCache<GVRImage> cache, BitmapTextureCallback callback) {
        return new BitmapTextureCallbackWrapper(cache, callback);
    }

    /**
     * Wrap the callback, to cache the
     * {@link TextureCallback#loaded(GVRHybridObject, GVRAndroidResource)
     * loaded()} resource
     */
    public static TextureCallback wrapCallback(
            ResourceCache<GVRImage> cache, TextureCallback callback) {
        return new TextureCallbackWrapper(cache, callback);
    }

    private static class CallbackWrapper<T extends GVRHybridObject> implements
            CancelableCallback<T> {

        protected final ResourceCache<T> cache;
        protected final Callback<T> callback;

        CallbackWrapper(ResourceCache<T> cache, Callback<T> callback) {
            Assert.checkNotNull("cache", cache);
            Assert.checkNotNull("callback", callback);

            this.cache = cache;
            this.callback = callback;
        }

        @Override
        public void loaded(T resource, GVRAndroidResource androidResource) {
            cache.put(androidResource, resource);
            callback.loaded(resource, androidResource);
        }

        @Override
        public void failed(Throwable t, GVRAndroidResource androidResource) {
            callback.failed(t, androidResource);
        }

        @Override
        public boolean stillWanted(GVRAndroidResource androidResource ) {
            return true;
        }
    }

    private static class CancelableCallbackWrapper<T extends GVRHybridObject>
            extends CallbackWrapper<T> implements CancelableCallback<T> {

        private CancelableCallbackWrapper(ResourceCache<T> cache,
                CancelableCallback<T> cancelableCallback) {
            super(cache, cancelableCallback);
        }

        @Override
        public boolean stillWanted(GVRAndroidResource androidResource) {
            return ((CancelableCallback<T>) callback)
                    .stillWanted(androidResource);
        }
    }

    // Those 'convenience' interfaces are getting to be a real annoyance
    private static class CompressedTextureCallbackWrapper extends
            CancelableCallbackWrapper<GVRImage> implements CompressedTextureCallback {

        CompressedTextureCallbackWrapper(ResourceCache<GVRImage> cache,
                CompressedTextureCallback callback) {
            super(cache, callback);
        }
    }

    private static class TextureCallbackWrapper extends
            CancelableCallbackWrapper<GVRImage> implements
            TextureCallback {
        TextureCallbackWrapper(ResourceCache<GVRImage> cache,
                               TextureCallback callback) {
            super(cache, callback);
        }
    }

    private static class BitmapTextureCallbackWrapper extends
            CancelableCallbackWrapper<GVRImage> implements
            BitmapTextureCallback {
        BitmapTextureCallbackWrapper(ResourceCache<GVRImage> cache,
                BitmapTextureCallback callback) {
            super(cache, callback);
        }
    }
}
