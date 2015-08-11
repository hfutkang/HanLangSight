package com.ingenic.glass.camera.gallery;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;

import java.util.HashSet;

public class SettingAnimUtils {
    static HashSet<Animator> sAnimators = new HashSet<Animator>();
    static Animator.AnimatorListener sEndAnimListener = new Animator.AnimatorListener() {
        @Override
		public void onAnimationStart(Animator animation) {
        }

        @Override
		public void onAnimationRepeat(Animator animation) {
        }

        @Override
		public void onAnimationEnd(Animator animation) {
            sAnimators.remove(animation);
        }

        @Override
		public void onAnimationCancel(Animator animation) {
            sAnimators.remove(animation);
        }
    };

    public static void cancelOnDestroyActivity(Animator a) {
        sAnimators.add(a);
        a.addListener(sEndAnimListener);
    }

    public static void onDestroyActivity() {
        HashSet<Animator> animators = new HashSet<Animator>(sAnimators);
        for (Animator a : animators) {
            if (a.isRunning()) {
                a.cancel();
            } else {
                sAnimators.remove(a);
            }
        }
    }

    public static AnimatorSet createAnimatorSet() {
        AnimatorSet anim = new AnimatorSet();
        cancelOnDestroyActivity(anim);
        return anim;
    }

    public static ValueAnimator ofFloat(float... values) {
        ValueAnimator anim = new ValueAnimator();
        anim.setFloatValues(values);
        cancelOnDestroyActivity(anim);
        return anim;
    }

    public static ObjectAnimator ofFloat(Object target, String propertyName, float... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setPropertyName(propertyName);
        anim.setFloatValues(values);
        cancelOnDestroyActivity(anim);
        return anim;
    }

    public static ObjectAnimator ofPropertyValuesHolder(Object target,
            PropertyValuesHolder... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(values);
        cancelOnDestroyActivity(anim);
        return anim;
    }
}

