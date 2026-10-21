// Dementech · NFS Carbon — painel de diagnóstico do congelamento.
// SPDX-License-Identifier: MIT
package com.nfscarbon.android;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;

/**
 * Leitura do estado da apresentação de quadros direto do log do motor.
 *
 * Usado em dois lugares: a aba "Diagnóstico" do launcher e o diálogo aberto
 * de dentro do jogo (pelo botão "Ajustes" do editor de controles), porque o
 * congelamento só aparece com o jogo em primeiro plano.
 *
 * O motor registra o contador cumulativo de quadros entregues ao display
 * ("adquisicion anticipada: N de M fotogramas"). Se M para de crescer enquanto
 * o log continua sendo escrito, o jogo está vivo mas a imagem não chega na
 * tela. Ver doc/02-congelamento.md.
 */
final class DiagnosticsPanel extends LinearLayout {
    private final TextView reading;
    private final TextView verdict;
    private final TextView lastStall;

    DiagnosticsPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        reading = Ui.label(context, "Lendo o log do motor…", 12, Ui.CHROME);
        reading.setLineSpacing(0, 1.25f);
        addView(reading, Ui.matchWrap());
        verdict = Ui.text(context, "", 11, Ui.CHROME_DIM, true, 0.08f);
        verdict.setPadding(0, Ui.dp(context, 8), 0, 0);
        addView(verdict, Ui.matchWrap());
        lastStall = Ui.label(context, "", 11, Ui.WARNING);
        lastStall.setPadding(0, Ui.dp(context, 6), 0, 0);
        addView(lastStall, Ui.matchWrap());
    }

    /** Arquivo de log que o motor está escrevendo agora. */
    static File logFile(Context context) {
        return FreezeReport.logFile(context.getExternalFilesDir(null));
    }

    /** Relê o log e atualiza os textos. */
    void refresh() {
        // 64 KB: pega o histograma de swaps (a cada ~20 s) sem pesar na UI.
        FreezeReport.Snapshot snapshot = FreezeReport.read(logFile(getContext()), 64 * 1024);
        reading.setText(FreezeReport.describe(snapshot));
        if (!snapshot.readable) {
            verdict.setText("SEM LOG");
            verdict.setTextColor(Ui.CHROME_DIM);
        } else if (snapshot.suspect()) {
            verdict.setText("⚠ IMAGEM TRAVADA — o motor parou de entregar quadro");
            verdict.setTextColor(Ui.WARNING);
        } else {
            verdict.setText("APRESENTAÇÃO NORMAL");
            verdict.setTextColor(Ui.OK);
        }
        long seconds = lastStallSeconds(getContext());
        long at = lastStallAt(getContext());
        if (seconds > 0) {
            java.text.SimpleDateFormat stamp = new java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.US);
            lastStall.setText("Última travada detectada: " + seconds + " s às " + stamp.format(new java.util.Date(at)));
            lastStall.setVisibility(View.VISIBLE);
        } else {
            lastStall.setVisibility(View.GONE);
        }
    }

    /** Registra a travada detectada (fica visivel no launcher e no jogo). */
    static void recordStall(Context context, long seconds, long atMillis) {
        context.getSharedPreferences("nfscarbon_game", Context.MODE_PRIVATE).edit()
                .putLong("last_stall_seconds", seconds)
                .putLong("last_stall_at", atMillis)
                .apply();
    }

    /** Segundos da ultima travada registrada (0 se nunca houve). */
    static long lastStallSeconds(Context context) {
        return context.getSharedPreferences("nfscarbon_game", Context.MODE_PRIVATE)
                .getLong("last_stall_seconds", 0);
    }

    /** Instante da ultima travada registrada (0 se nunca houve). */
    static long lastStallAt(Context context) {
        return context.getSharedPreferences("nfscarbon_game", Context.MODE_PRIVATE)
                .getLong("last_stall_at", 0);
    }
}
