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
 * A identidade e monocroma (prata cromada sobre preto), com um unico acento
 * eletrico para acoes interativas. Tudo e montado por codigo porque o
 * launcher nao depende de layouts XML nem de Android Studio.
 */
final class Ui {
    // Paleta
    static final int BLACK = 0xFF07070A;
    static final int SURFACE = 0xFF101017;
    static final int SURFACE_ALT = 0xFF17171F;
    static final int CARD = 0xFF1C1C26;
    static final int OUTLINE = 0xFF2C2C39;
    static final int CHROME = 0xFFC9CFDD;
    static final int CHROME_DIM = 0xFF8A92A3;
    static final int ACCENT = 0xFFFF3B4E;
    static final int ACCENT_DIM = 0xFF8E1220;
    static final int DANGER = 0xFFE46A6A;
    static final int OK = 0xFF7BD88F;
    static final int WARNING = 0xFFE5C07B;

    private Ui() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Texto com tracking (letterSpacing) e peso. */
    static TextView text(Context context, String message, float size, int color, boolean bold, float tracking) {
        TextView view = new TextView(context);
        view.setText(message);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLetterSpacing(tracking);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    static TextView label(Context context, String message, float size, int color) {
        return text(context, message, size, color, false, 0f);
    }

    /** Titulo de secao: maiusculo, tracking largo, cor cromo. */
    static TextView section(Context context, String message) {
        TextView view = text(context, message.toUpperCase(), 11, CHROME, true, 0.22f);
        view.setPadding(0, dp(context, 2), 0, dp(context, 10));
        return view;
    }

    /** Cartao: fundo grafite, cantos arredondados, borda fina. */
    static LinearLayout card(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(context, 18));
        bg.setStroke(dp(context, 1), OUTLINE);
        view.setBackground(bg);
        return view;
    }

    /** Cartao "elevado": fundo um pouco mais claro, usado no heroi. */
    static LinearLayout heroCard(Context context) {
        LinearLayout view = card(context);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1A1A24, 0xFF0C0C12});
        bg.setCornerRadius(dp(context, 20));
        bg.setStroke(dp(context, 1), OUTLINE);
        view.setBackground(bg);
        return view;
    }

    /** Botao principal: gradiente cromado, texto escuro. */
    static TextView primaryButton(Context context, String title, View.OnClickListener click) {
        TextView view = text(context, title, 19, BLACK, true, 0.18f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFF2F5FA, 0xFF9AA3B5});
        bg.setCornerRadius(dp(context, 14));
        view.setBackground(bg);
        view.setElevation(dp(context, 2));
        view.setOnClickListener(click);
        return view;
    }

    /** Botao secundario: contorno fino. */
    static TextView ghostButton(Context context, String title, View.OnClickListener click) {
        TextView view = text(context, title, 13, CHROME, true, 0.04f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14FFFFFF);
        bg.setCornerRadius(dp(context, 12));
        bg.setStroke(dp(context, 1), OUTLINE);
        view.setBackground(bg);
        view.setOnClickListener(click);
        return view;
    }

    /** Rotulo de estado em capsula. */
    static TextView chip(Context context, String title, int color) {
        TextView view = text(context, title, 10, color, true, 0.12f);
        view.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color & 0x22000000 | 0x22000000);
        bg.setCornerRadius(dp(context, 20));
        bg.setStroke(dp(context, 1), color & 0x66000000);
        view.setBackground(bg);
        return view;
    }

    /** Linha de opcao: titulo a esquerda, valor a direita, toque no fim. */
    static LinearLayout row(Context context, String title, String value, int valueColor, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE_ALT);
        bg.setCornerRadius(dp(context, 12));
        row.setBackground(bg);
        row.addView(text(context, title, 13, CHROME, false, 0f), new LinearLayout.LayoutParams(0, -2, 1));
        TextView current = text(context, value + "  ›", 12, valueColor, true, 0.02f);
        current.setGravity(Gravity.END);
        row.addView(current, new LinearLayout.LayoutParams(0, -2, 1.15f));
        if (click != null) row.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(context, 6);
        row.setLayoutParams(params);
        return row;
    }

    /** Separador horizontal fino. */
    static View divider(Context context) {
        View view = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(OUTLINE);
        view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Math.max(1, dp(context, 1)));
        params.topMargin = dp(context, 10);
        params.bottomMargin = dp(context, 10);
        view.setLayoutParams(params);
        return view;
    }

    /** Titulo de aba selecionada. */
    static TextView tab(Context context, String title, boolean selected, View.OnClickListener click) {
        TextView view = text(context, title.toUpperCase(), 11, selected ? BLACK : CHROME_DIM, true, 0.16f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 16), dp(context, 9), dp(context, 16), dp(context, 9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? CHROME : 0x00000000);
        bg.setCornerRadius(dp(context, 20));
        view.setBackground(bg);
        view.setOnClickListener(click);
        return view;
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
