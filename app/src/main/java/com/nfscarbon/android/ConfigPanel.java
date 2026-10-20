// Dementech · NFS Carbon — painel de configuração do jogo (estilo menu do Carbon).
// SPDX-License-Identifier: MIT
package com.nfscarbon.android;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Lista completa de opcoes do motor, no estilo do menu do Carbon.
 *
 * O menu do proprio jogo e dado do jogo (recursos de frontend): para caber
 * la, o motor teria de ler os valores, e so recompilando o motor isso
 * acontece. Este painel entrega o mesmo resultado sem mexer no motor, e o
 * motor le as opcoes pela linha de comando quando a Activity do jogo e criada
 * de novo (aplicar agora).
 */
final class ConfigPanel extends LinearLayout {
    /** Chamado quando o usuario pede para aplicar as mudancas na hora. */
    interface OnApply {
        void applyNow();
    }

    private final Context context;
    private final OnApply onApply;

    ConfigPanel(Context context, OnApply onApply) {
        super(context);
        this.context = context;
        this.onApply = onApply;
        setOrientation(VERTICAL);
        build();
    }

    /** Opcoes agrupadas como no menu do jogo. */
    private static final String[][] GRUPOS = {
            {"Vídeo e desempenho", "renderer", "resolution", "fps", "single_pass", "vsync"},
            {"Compilação e CPU", "async", "workers", "cpu"},
            {"Áudio", "audio_buffer", "voices"},
            {"Idioma e tela", "language", "fps_overlay"},
            {"Registro e diagnóstico", "diagnostics"},
    };

    private void build() {
        for (String[] grupo : GRUPOS) {
            addView(Ui.section(context, grupo[0]), Ui.matchWrap());
            for (int i = 1; i < grupo.length; i++) {
                GameOptions.Option option = find(grupo[i]);
                if (option == null) continue;
                addView(optionRow(option));
            }
            addView(Ui.divider(context), Ui.matchWrap());
        }
        addView(Ui.text(context,
                "As opções valem na próxima sessão do jogo. Em “aplicar agora” a sessão é reiniciada "
                        + "com as novas opções (o save fica intacto).", 11, Ui.CHROME_DIM, false, 0f),
                Ui.matchWrap());
        addView(Ui.ghostButton(context, "Aplicar agora", ignored -> {
            if (onApply != null) onApply.applyNow();
        }), Ui.matchWrap());
    }

    private static GameOptions.Option find(String key) {
        for (GameOptions.Option option : GameOptions.ALL) if (option.key.equals(key)) return option;
        return null;
    }

    /** Linha de opção com o valor atual; tocar abre a lista de valores. */
    private View optionRow(GameOptions.Option option) {
        String current = option.label(GameOptions.get(context, option.key));
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 14), Ui.dp(context, 11), Ui.dp(context, 14), Ui.dp(context, 11));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Ui.SURFACE_ALT);
        bg.setCornerRadius(Ui.dp(context, 12));
        row.setBackground(bg);
        row.addView(Ui.text(context, option.title, 13, Ui.CHROME, false, 0f),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = Ui.text(context, current + "  ›", 12, Ui.ACCENT, true, 0.02f);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 1.1f));
        row.setOnClickListener(ignored -> choose(option));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(context, 6);
        row.setLayoutParams(params);
        return row;
    }

    /** Diálogo de escolha do valor (mesma lista de rótulos que o launcher). */
    private void choose(GameOptions.Option option) {
        int selected = 0;
        for (int i = 0; i < option.values.length; i++) {
            if (option.values[i].equals(GameOptions.get(context, option.key))) selected = i;
        }
        new android.app.AlertDialog.Builder(context)
                .setTitle(option.title)
                .setSingleChoiceItems(option.labels, selected, (choice, value) -> {
                    GameOptions.set(context, option.key, option.values[value]);
                    choice.dismiss();
                    rebuild();
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    /** Redesenha as linhas com os valores atuais. */
    private void rebuild() {
        removeAllViews();
        build();
    }
}
