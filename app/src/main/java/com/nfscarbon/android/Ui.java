// Dementech · NFS Carbon — kit de UI do launcher.
// SPDX-License-Identifier: MIT
package com.nfscarbon.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Componentes e paleta do launcher Dementech.
 *
 * Identidade: preto carbono, vidro fumê e um único acento elétrico. Tipografia
 * condensada em caixa alta para títulos (peso de pôster de jogo) e texto curto
 * com tracking largo para rótulos. Tudo montado por código — o launcher não usa
 * layouts XML.
 */
final class Ui {
    // Paleta
    static final int BLACK = 0xFF05060A;
    static final int SURFACE = 0xFF0B0D12;
    static final int SURFACE_ALT = 0xFF14171F;
    static final int CARD = 0xFF10131A;
    static final int OUTLINE = 0x33FFFFFF;
    static final int CHROME = 0xFFF2F5FA;
    static final int CHROME_DIM = 0xFF98A2B5;
    static final int ACCENT = 0xFFFF3B4E;
    static final int ACCENT_DIM = 0xFF8E1220;
    static final int DANGER = 0xFFE46A6A;
    static final int OK = 0xFF7BD88F;
    static final int WARNING = 0xFFE5C07B;

    private static Typeface condensed, condensedBlack;

    private Ui() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Condensada pesada: o tipo de pôster usado nos títulos do launcher. */
    static Typeface display(Context context) {
        if (condensedBlack == null) {
            condensedBlack = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        }
        return condensedBlack;
    }

    private static Typeface label(Context context) {
        if (condensed == null) condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        return condensed;
    }

    /** Texto com tracking (letterSpacing) e peso. */
    static TextView text(Context context, String message, float size, int color, boolean bold, float tracking) {
        TextView view = new TextView(context);
        view.setText(message);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLetterSpacing(tracking);
        view.setTypeface(bold ? display(context) : label(context));
        return view;
    }

    static TextView label(Context context, String message, float size, int color) {
        return text(context, message, size, color, false, 0.01f);
    }

    /** Título gigante de pôster. */
    static TextView display(Context context, String message, float size, int color) {
        TextView view = text(context, message, size, color, true, 0.01f);
        view.setIncludeFontPadding(false);
        view.setShadowLayer(dp(context, 10), 0, dp(context, 3), 0x99000000);
        return view;
    }

    /** Sobretítulo: caixa alta, tracking largo, acento discreto. */
    static TextView eyebrow(Context context, String message) {
        return text(context, message.toUpperCase(), 11, ACCENT, true, 0.34f);
    }

    /** Título de seção: maiúsculo, tracking largo, com barra de acento. */
    static LinearLayout section(Context context, String message) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 4), 0, dp(context, 12));
        View bar = new View(context);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(ACCENT);
        barBg.setCornerRadius(dp(context, 2));
        bar.setBackground(barBg);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(context, 3), dp(context, 13));
        barParams.rightMargin = dp(context, 9);
        row.addView(bar, barParams);
        row.addView(text(context, message.toUpperCase(), 11, CHROME, true, 0.22f));
        return row;
    }

    /** Painel de vidro fumê: fundo escuro translúcido, borda de 1 px. */
    static LinearLayout glass(Context context, int radiusDp) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xF2141720, 0xF20A0C11});
        bg.setCornerRadius(dp(context, radiusDp));
        bg.setStroke(Math.max(1, dp(context, 1)), OUTLINE);
        view.setBackground(bg);
        view.setElevation(dp(context, 8));
        return view;
    }

    /** Cartão: fundo grafite, cantos arredondados, borda fina. */
    static LinearLayout card(Context context) {
        LinearLayout view = glass(context, 16);
        view.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        return view;
    }

    /** Cartão "elevado": fundo um pouco mais claro, usado no heroi. */
    static LinearLayout heroCard(Context context) {
        LinearLayout view = card(context);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xF21A1E28, 0xF20A0C11});
        bg.setCornerRadius(dp(context, 18));
        bg.setStroke(Math.max(1, dp(context, 1)), OUTLINE);
        view.setBackground(bg);
        return view;
    }

    /** Botão principal: vermelho Dementech, brilho próprio, texto em caixa alta. */
    static TextView primaryButton(Context context, String title, View.OnClickListener click) {
        TextView view = text(context, title.toUpperCase(), 20, 0xFFFFFFFF, true, 0.20f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF5566, ACCENT, 0xFFC21D30});
        bg.setCornerRadius(dp(context, 14));
        view.setBackground(bg);
        view.setElevation(dp(context, 10));
        view.setShadowLayer(dp(context, 14), 0, dp(context, 2), 0x66FF3B4E);
        view.setOnClickListener(click);
        Motion.press(view);
        return view;
    }

    /** Botão secundário: vidro com contorno fino. */
    static TextView ghostButton(Context context, String title, View.OnClickListener click) {
        TextView view = text(context, title.toUpperCase(), 12, CHROME, true, 0.10f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(dp(context, 12));
        bg.setStroke(Math.max(1, dp(context, 1)), 0x40FFFFFF);
        view.setBackground(bg);
        view.setOnClickListener(click);
        Motion.press(view);
        return view;
    }

    /** Rótulo de estado em cápsula, com ponto colorido. */
    static TextView chip(Context context, String title, int color) {
        TextView view = text(context, "●  " + title.toUpperCase(), 10, color, true, 0.14f);
        view.setPadding(dp(context, 11), dp(context, 6), dp(context, 11), dp(context, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1FFFFFFF);
        bg.setCornerRadius(dp(context, 20));
        bg.setStroke(Math.max(1, dp(context, 1)), 0x26FFFFFF);
        view.setBackground(bg);
        return view;
    }

    /** Linha de opção: título a esquerda, valor a direita, toque no fim. */
    static LinearLayout row(Context context, String title, String value, int valueColor, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 14), dp(context, 13), dp(context, 12), dp(context, 13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x0DFFFFFF);
        bg.setCornerRadius(dp(context, 11));
        bg.setStroke(Math.max(1, dp(context, 1)), 0x1FFFFFFF);
        row.setBackground(bg);
        row.addView(text(context, title, 13, CHROME, false, 0.01f), new LinearLayout.LayoutParams(0, -2, 1));
        TextView current = text(context, value + "  ›", 12, valueColor, true, 0.02f);
        current.setGravity(Gravity.END);
        row.addView(current, new LinearLayout.LayoutParams(0, -2, 1.15f));
        if (click != null) row.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(context, 6);
        row.setLayoutParams(params);
        Motion.press(row);
        return row;
    }

    /** Separador horizontal fino. */
    static View divider(Context context) {
        View view = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1FFFFFFF);
        view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Math.max(1, dp(context, 1)));
        params.topMargin = dp(context, 12);
        params.bottomMargin = dp(context, 12);
        view.setLayoutParams(params);
        return view;
    }

    /** Aba de navegação: caixa alta, com sublinhado de acento quando ativa. */
    static TextView tab(Context context, String title, boolean selected, View.OnClickListener click) {
        TextView view = text(context, title.toUpperCase(), 11, selected ? CHROME : CHROME_DIM, true, 0.18f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        view.setBackground(tabBackground(selected));
        view.setOnClickListener(click);
        return view;
    }

    private static GradientDrawable tabBackground(boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? 0x1FFFFFFF : 0x00000000);
        bg.setCornerRadius(10);
        bg.setStroke(1, selected ? ACCENT : 0x1AFFFFFF);
        return bg;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static int blend(int a, int b, float ratio) {
        float inverse = 1f - ratio;
        return Color.argb(Math.round(Color.alpha(a) * inverse + Color.alpha(b) * ratio),
                Math.round(Color.red(a) * inverse + Color.red(b) * ratio),
                Math.round(Color.green(a) * inverse + Color.green(b) * ratio),
                Math.round(Color.blue(a) * inverse + Color.blue(b) * ratio));
    }
}
