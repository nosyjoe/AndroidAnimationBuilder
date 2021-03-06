/**
 MIT license, do whatever

 Copyright (c) 2017 Matthias Schicker

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */


package com.komaxx.androidanimationbuilder;

import android.animation.Animator;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewPropertyAnimator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * <p>
 * Provides a simple, promise-like, interface to create complex
 * animations, easily extensible with callbacks and hooks all
 * along the way.
 * </p>
 *
 * Exemplary usage to create a simple wiggle animation:
 * <pre>
 *      AndroidAnimationBuilder builder = new AndroidAnimationBuilder(v);
 *      builder.setDefaultStepLength(60)
 *             .rotateBy(2)
 *             .then().rotateBy(-6)
 *             .then().rotateBy(7)
 *             .then().rotateBy(-3).ms(120)
 *             .execute();
 * </pre>
 *
 * @author  Created by Matthias Schicker (KoMaXX) on 28/02/2017.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class AndroidAnimationBuilder {
    private static final boolean DEBUG_LOGGING = false;

    private final WeakReference<View> viewRef;

    private boolean allowLayerAdjustmentForAnimation = true;
    private int defaultStepDurationMS = 300;

    private final ArrayList<AnimationStep> steps = new ArrayList<>();

    // not yet added to steps!
    private AnimationStep currentStep = new AnimationStep();

    private boolean executionTriggered = false;


    /**
     * Starting point for all the animation fun. Will take any ol' view.
     *
     * @param v The view on which the animations will be executed. WEAKLY held!
     */
    public AndroidAnimationBuilder(View v) {
        viewRef = new WeakReference<>(v);
    }

    /**
     * Set this to define the default duration of all steps, i.e., the
     * duration of all steps that did not receive explicit duration
     * definitions.<br/>
     * Defaults to 300ms.
     */
    public AndroidAnimationBuilder setDefaultStepDuration(int ms) {
        if (alreadyExecuted()) return this;

        defaultStepDurationMS = ms;
        return this;
    }

    /**
     * If <code>true</code>, the AnimationBuilder will attempt to set the
     * layer type of the view for the duration of the animation.
     * Default: <code>true</code>.
     */
    public AndroidAnimationBuilder setAllowLayerAdjustmentForAnimation(boolean allowLayerAdjustmentForAnimation) {
        if (alreadyExecuted()) return this;

        this.allowLayerAdjustmentForAnimation = allowLayerAdjustmentForAnimation;
        return this;
    }

    /**
     * Add a rotation animation to the current step. Will replace previously
     * set rotation animations for the current step.
     */
    public AndroidAnimationBuilder rotateBy(float degrees) {
        if (alreadyExecuted()) return this;

        currentStep.setRotateBy(degrees);
        return this;
    }

    /**
     * Add a translation on the xAxis to the current animation step.
     * Will replace current x-translation definitions for the current step.
     */
    public AndroidAnimationBuilder translateX(float xTransPx){
        if (alreadyExecuted()) return this;

        currentStep.setTranslateX(xTransPx);
        return this;
    }

    /**
     * Add a translation on the yAxis to the current animation step.
     * Will replace current y-translation definitions for the current step.
     */
    public AndroidAnimationBuilder translateY(float yTransPx){
        if (alreadyExecuted()) return this;

        currentStep.setTranslateY(yTransPx);
        return this;
    }

    /**
     * Add a translation on the zAxis to the current animation step.
     * Will replace current z-translation definitions for the current step.
     *
     * ONLY available in API level >=21 environments. No-op otherwise.
     */
    public AndroidAnimationBuilder translateZ(float zTransPx){
        if (alreadyExecuted()) return this;

        currentStep.setTranslateZ(zTransPx);
        return this;
    }

    /**
     * Add scaling in x direction to the current animation step.
     * Will replace current x-scale definitions for the current step.
     */
    public AndroidAnimationBuilder scaleX(float scaleX){
        if (alreadyExecuted()) return this;

        currentStep.setScaleX(scaleX);
        return this;
    }

    /**
     * Add scaling in y direction to the current animation step.
     * Will replace current y-scale definitions for the current step.
     */
    public AndroidAnimationBuilder scaleY(float scaleY){
        if (alreadyExecuted()) return this;

        currentStep.setScaleY(scaleY);
        return this;
    }

    /**
     * Add alpha animation to the current step.
     */
    public AndroidAnimationBuilder alpha(float alpha){
        if (alreadyExecuted()) return this;

        currentStep.setAlpha(alpha);
        return this;
    }

    /**
     * Add some action that is to be executed at the beginning
     * of the animation step. Called in main thread.
     */
    public AndroidAnimationBuilder run(AnimationStepHook toRun){
        if (alreadyExecuted()) return this;
        currentStep.setPreStep(toRun);
        return this;
    }

    /**
     * Add some action that is to be executed at the END
     * of the animation step, after the animation stopped. Called in main thread.
     */
    public AndroidAnimationBuilder runAfter(AnimationStepHook toRun){
        if (alreadyExecuted()) return this;
        currentStep.setPostStep(toRun);
        return this;
    }

    /**
     * Finishes the current animation step definition and starts the next one.
     * Unless given a specific duration it will have the default duration.
     * An empty step will simply appear as a pause. An empty step at the end
     * of the sequence will be ignored.
     */
    public AndroidAnimationBuilder then() {
        if (alreadyExecuted()) return this;

        steps.add(currentStep);
        currentStep = new AnimationStep();
        return this;
    }

    /**
     * Call this to insert a pause between two animation steps.
     * It is essentially a shortcut for 'then().ms(ms).then()'
     */
    public AndroidAnimationBuilder pause(int ms){
        then();
        ms(ms);
        then();
        return this;
    }

    /**
     * Duplicates the current step x times. Afterwards the builder
     * is in the last copy of the repeated step. You might want
     * to call 'then' afterwards to get a fresh step ;)
     *
     * @param times How often the current step is to be repeated.<br/>
     *              <=0: Does nothing<br/>
     *              1: The current step will be executed two times<br/>
     *              n: The current step will be executed (n+1) times overall
     */
    public AndroidAnimationBuilder repeat(int times){
        if (times <= 0) return this;
        if (alreadyExecuted()) return this;

        for (int i = 1; i < times; i++){
            steps.add(new AnimationStep(currentStep));
        }

        return this;
    }

    private boolean alreadyExecuted() {
        if (executionTriggered){
            Log.w("AndroidAnimationBuilder", "Further animation definitions ignored: Execution already started!");
        }
        return executionTriggered;
    }

    /**
     * Set the duration for the current step. If not set, the step will use the
     * default duration. Values <1 unset any previously set duration.
     */
    public AndroidAnimationBuilder ms(int ms) {
        currentStep.durationMs = ms;
        return this;
    }

    /**
     * Sets multiple values in this step in such a way that all modifications up
     * to this point are undone (translated back to original position, unscaled,
     * rotated back to original orientation, alpha = startAlpha).
     *
     * May be combined with any other step definition.
     */
    public AndroidAnimationBuilder reset() {
        if (alreadyExecuted()) return this;

        currentStep.setResetting(true);

        return this;
    }

    /**
     * MUST be the final call to the builder. Compiles the actual animations
     * out of the definitions.
     * All following calls to the build will have no effect;
     */
    public void execute() {
        if (alreadyExecuted()) return;
        executionTriggered = true;

        // prepare the chain:
        // add the current step
        if (!currentStep.isEmpty()) steps.add(currentStep);
        if (steps.size() < 1){
            if (DEBUG_LOGGING){
                Log.w("AndroidAnimationBuilder", "No animation defined.");
            }
            return;
        }

        // build startState to enable 'reset' and 'undo'
        View view = viewRef.get();
        StartState startState = new StartState(view);

        // build final step that reverts layer changes.
        FinalStep finalStep = new FinalStep();
        finalStep.viewRef = viewRef;
        steps.add(finalStep);

        // build chain out of animation steps. Set defaults if not done yet
        for (int i = 0; i < steps.size()-1; i++){
            AnimationStep step = steps.get(i);
            step.viewRef = viewRef;
            step.nextStep = steps.get(i+1);
            step.startState = startState;
            step.setDurationIfUnset(defaultStepDurationMS);
        }

        if (view != null && allowLayerAdjustmentForAnimation) {
            finalStep.setEndLayerType(view.getLayerType());

            // speed up the animation if available
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        steps.get(0).execute();
    }

    static class AnimationStep implements Animator.AnimatorListener {
        boolean resetting;

        Float rotateByDegrees;

        Float translationX; Float translationY; Float translationZ;

        Float scaleX; Float scaleY;

        Float alpha;

        AnimationStepHook preStep;
        AnimationStepHook postStep;

        // set at latest when animation is built
        int durationMs;

        // set when animation is built
        WeakReference<View> viewRef;
        StartState startState;
        AnimationStep nextStep;


        public AnimationStep(){ }

        public AnimationStep(AnimationStep from) {
            this.resetting = from.resetting;

            this.rotateByDegrees = from.rotateByDegrees;

            this.translationX = from.translationX;
            this.translationY = from.translationY;
            this.translationZ = from.translationZ;

            this.scaleX = from.scaleX;
            this.scaleY = from.scaleY;

            this.alpha = from.alpha;

            this.preStep = from.preStep;
            this.postStep = from.postStep;
        }


        void setRotateBy(float degrees) {
            rotateByDegrees = degrees;
        }

        void setTranslateX(float translateX) {
            this.translationX = translateX;
        }
        void setTranslateY(float translateY) {
            this.translationY = translateY;
        }
        void setTranslateZ(float translateZ) {
            this.translationZ = translateZ;
        }

        void setScaleX(float scaleX) {
            this.scaleX = scaleX;
        }
        void setScaleY(float scaleY) {
            this.scaleY = scaleY;
        }

        void setAlpha(float alpha){
            this.alpha = alpha;
        }

        void setPreStep(AnimationStepHook toRun){
            this.preStep = toRun;
        }

        public void setPostStep(AnimationStepHook toRun) {
            this.postStep = toRun;
        }

        void setResetting(boolean resetting) {
            this.resetting = resetting;
        }


        /**
         * Decides if the step is at least minimally defined. Empty steps
         * will simply be a pause - unless it's the final step, then it
         * will be discarded.
         */
        boolean isEmpty() {
            return !hasAnimation() && preStep==null && !resetting;
        }

        boolean hasAnimation() {
            return     rotateByDegrees!=null
                    || translationX!=null
                    || translationY!=null
                    || translationZ!=null
                    || scaleX!=null
                    || scaleY!=null
                    || alpha!=null
                    || resetting;
        }

        void setDurationIfUnset(int ms) {
            if (durationMs <= 0) durationMs = ms;
        }

        void execute() {
            View view = viewRef.get();
            if (view == null){
                Log.w("AndroidAnimationBuilder", "Aborting animation step: View was cleaned up");
                return;
            }

            if (preStep != null){
                preStep.run(view);
            }

            if (hasAnimation()){
                ViewPropertyAnimator animate = view.animate();

                if (resetting){
                    animate.alpha(startState.alpha);

                    animate.scaleX(startState.scaleX);
                    animate.scaleY(startState.scaleY);

                    animate.translationX(startState.translationX);
                    animate.translationY(startState.translationY);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                        animate.translationZ(startState.translationZ);
                    }

                    animate.rotation(startState.rotation);
                }

                if (rotateByDegrees != null) animate.rotationBy(rotateByDegrees);

                if (translationX != null) animate.translationX(translationX);
                if (translationY != null) animate.translationY(translationY);

                if (translationZ != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    animate.translationZ(translationZ);
                }

                if (scaleX != null) animate.scaleX(scaleX);
                if (scaleY != null) animate.scaleY(scaleY);

                if (alpha != null) animate.alpha(alpha);

                // add other animation types here.
                animate.setDuration(durationMs);
                animate.setListener(this);
                animate.start();
            } else {
                view.postDelayed(new Runnable() {
                    @Override  public void run() {
                        stepFinished();
                    }
                }, durationMs);
            }
        }

        /**
         * Called when the step was finished.
         */
        private void stepFinished() {
            View view = viewRef.get();
            if (view == null){
                if (DEBUG_LOGGING){
                    Log.i("AndroidAnimationBuilder", "Aborting animation step when scheduling next step: View was cleaned up");
                }
                return;
            }

            if (postStep != null) postStep.run(view);

            view.postDelayed(new Runnable() {
                @Override public void run() {
                    nextStep.execute();
                }
            }, 1);
        }


        @Override  public void onAnimationEnd(Animator animator) {
            stepFinished();
        }

        @Override  public void onAnimationStart(Animator animator) {}
        @Override  public void onAnimationCancel(Animator animator) {}
        @Override  public void onAnimationRepeat(Animator animator) {}
    }

    /**
     * Special no-op final step of the animation. Undoes any changes
     * to layer settings done to make the animation smooth.
     */
    private static class FinalStep extends AnimationStep {
        private Integer endLayerType;

        @Override
        public void execute() {
            if (DEBUG_LOGGING){
                Log.d("AndroidAnimationBuilder","Animation done!");
            }

            View view = viewRef.get();
            if (view != null && endLayerType != null){
                view.setLayerType(endLayerType, null);
            }
        }

        public void setEndLayerType(int layerType) {
            this.endLayerType = layerType;
        }
    }

    /**
     * Encapsulates the state of the view at the beginning of the animation
     * for later comparison and undoing.
     */
    private static class StartState {
        public final float alpha;
        public final float scaleX;
        public final float scaleY;
        public final float translationX;
        public final float translationY;
        public final float translationZ;
        public final float rotation;

        public StartState(@Nullable View view) {
            if (view == null){
                alpha = 0;
                scaleX = scaleY = 0;
                translationX = translationY = translationZ = 0;
                rotation = 0;
            } else {
                alpha = view.getAlpha();

                scaleX = view.getScaleX();
                scaleY = view.getScaleY();

                translationX = view.getTranslationX();
                translationY = view.getTranslationY();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                    translationZ = view.getTranslationZ();
                } else {
                    translationZ = 0;
                }

                rotation = view.getRotation();
            }
        }
    }

    /**
     * Objects implementing this interface can be added to any animation step
     * to be called when the animation step is triggered.
     */
    public interface AnimationStepHook {
        /**
         * Run when an animation step is triggered. Only called when the view
         * was not previously cleaned up as the AnimationBuilder does *not* retain
         * the view (-> memory)
         */
        void run(@NonNull View view);
    }
}
