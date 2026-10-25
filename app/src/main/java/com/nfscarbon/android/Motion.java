// Dementech · NFS Carbon — movimento do launcher.
// SPDX-License-Identifier: MIT
package com.nfscarbon.android;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Movimento do launcher: entrada em cascata, resposta ao toque, brilho pulsante
 * e o "Ken Burns" lento do vídeo de fundo.
 *
 * Regras: nada acima de ~500 ms na entrada, 120 ms no toque, e a curva padrão é
 * uma desaceleração longa (0.16, 1, 0.3, 1) — o mesmo tipo de easing usado em
 * aberturas de jogo.
 */
final class Motion {
    private static final Interpolator OUT = new PathInterpolator(0.16f, 1f, 0.3f, 1f);
    private static final Interpolator IN = new PathInterpolator(0.4f, 0f, 1f, 1f);

    private Motion() {}

    /** Entrada: sobe 24 dp e aparece, com atraso em cascata. */
    static void enter(View view, int delayMs) {
        float rise = Ui.dp(view.getContext(), 24);
        view.setAlpha(0f);
        view.setTranslationY(rise);
        view.animate().alpha(1f).translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(520)
                .setInterpolator(OUT)
                .start();
    }

    /** Entrada lateral (painéis). */
    static void slideIn(View view, int fromDp, int delayMs) {
        view.setAlpha(0f);
        view.setTranslationX(Ui.dp(view.getContext(), fromDp));
        view.animate().alpha(1f).translationX(0f)
                .setStartDelay(delayMs)
                .setDuration(300)
                .setInterpolator(OUT)
                .start();
    }

    static void slideOut(View view, int toDp, Runnable after) {
        view.animate().alpha(0f).translationX(Ui.dp(view.getContext(), toDp))
                .setDuration(220)
                .setInterpolator(IN)
                .withEndAction(after)
                .start();
    }

    /** Resposta ao toque: encolhe 3% e volta. */
    static void press(View view) {
        view.setOnTouchListener((target, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    target.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    target.animate().scaleX(1f).scaleY(1f).setDuration(160)
                            .setInterpolator(OUT).start();
                    break;
            }
            return false;
        });
    }

    /** Brilho suave e infinito (usado atrás do botão JOGAR). */
    static void pulse(View view, float min, float max, long periodMs) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, min, max);
        animator.setDuration(periodMs);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    /** Zoom lento do vídeo de fundo (24 s por ciclo, ida e volta). */
    static void kenBurns(View view) {
        view.setScaleX(1f);
        view.setScaleY(1f);
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.07f);
        ObjectAnimator animatorY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.07f);
        animator.setDuration(24000);
        animatorY.setDuration(24000);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animatorY.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animatorY.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animatorY.setInterpolator(new LinearInterpolator());
        animator.start();
        animatorY.start();
    }

    /** Troca de conteúdo: fade rápido com leve deslocamento. */
    static void swap(View view) {
        view.setAlpha(0f);
        view.setTranslationY(Ui.dp(view.getContext(), 10));
        view.animate().alpha(1f).translationY(0f).setDuration(240)
                .setInterpolator(OUT).start();
    }
}
