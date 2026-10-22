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

    DiagnosticsPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        reading = Ui.label(context, "Lendo o log do motor…", 12, Ui.CHROME);
        reading.setLineSpacing(0, 1.25f);
        addView(reading, Ui.matchWrap());
        verdict = Ui.text(context, "", 11, Ui.CHROME_DIM, true, 0.08f);
        verdict.setPadding(0, Ui.dp(context, 8), 0, 0);
        addView(verdict, Ui.matchWrap());
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
        } else if (snapshot.surfaceDead()) {
            verdict.setText("☠ A SURFACE VULKAN MORREU — o motor tentou recriá-la e falhou. "
                    + "A tela preta é esperada e não se recupera: use “Reabrir o jogo” "
                    + "(reinicia o app; o que estiver em andamento se perde).");
            verdict.setTextColor(Ui.WARNING);
        } else if (snapshot.suspect()) {
            verdict.setText("⚠ O MOTOR PAROU DE ENTREGAR QUADRO (imagem e áudio podem travar junto)");
            verdict.setTextColor(Ui.WARNING);
        } else {
            verdict.setText("MOTOR ENTREGANDO QUADROS NORMALMENTE — se a imagem está parada, "
                    + "o problema é na surface/display: use “Reiniciar renderização”");
            verdict.setTextColor(Ui.OK);
        }
    }





}
