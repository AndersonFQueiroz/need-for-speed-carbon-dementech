// Dementech · NFS Carbon — launcher reescrito (identidade visual, pt-BR, diagnóstico).
//
// A lógica de jogo (importar cópia, driver Vulkan, compilação de shaders, começar
// o jogo) vem do launcher base (GPL-3.0 — ver THIRD_PARTY_NOTICES);
// a camada visual e a aba de diagnóstico são do Dementech (MIT).
// SPDX-License-Identifier: GPL-3.0-only
package com.nfscarbon.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/** Launcher Dementech do NFS Carbon 360 (port recompilado para Android). */
public final class MainActivity extends Activity {
    static final String GAME_FOLDER_NAME = "NFSCARBON";
    static final String GAME_PARENT = "Jogos";
    private ShaderBuilder shaderBuilder;
    private static final int IMPORT = 10;
    private static final int REQUEST_DRIVER_ZIP = 45;
    private static final int REQUEST_DRIVER_PROBE = 46;
    private boolean playAfterProbe;
    private boolean launcherResumed;
    private boolean checkingUpdates;
    private ReleaseUpdates.Result pendingUpdate;
    private boolean pendingUpdateManual;
    private static final String EXPECTED_XEX = "b1e423914f5feb0871c8903aded4d86852d34be3b831b42f5c6473c7c7304221";

    private TextView status;
    private TextView play, importButton;
    private boolean busy;
    private LinearLayout checks, optionsList, diagnosticsList, creditsList;
    private DiagnosticsPanel diagnosticsPanel;
    private TextView accessButton;
    private TextView tabGame, tabDiagnostics, tabCredits;
    private LinearLayout tabContent;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        if (state != null) playAfterProbe = state.getBoolean("play_after_probe");
        setContentView(buildScreen());
        // Importa um save corrigido (saveio/in) e exporta o atual (saveio/out).
        SaveIO.sync(this);
        checkUpdates(false);
    }

    // ---------------------------------------------------------------- tela

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B0B11, 0xFF13131B, 0xFF08080C}));

        root.addView(buildHeader(), Ui.matchWrap());

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 16));
        root.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));

        columns.addView(buildLeftColumn(), new LinearLayout.LayoutParams(0, -1, 1.02f));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -1, 1.1f);
        rightParams.leftMargin = Ui.dp(this, 18);
        columns.addView(buildRightColumn(), rightParams);
        return root;
    }

    /** Cabecalho: marca a esquerda, versao e assinatura a direita. */
    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 22), Ui.dp(this, 14), Ui.dp(this, 22), Ui.dp(this, 4));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 62)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(Ui.dp(this, 14), 0, 0, 0);
        names.addView(Ui.text(this, "DEMENTECH", 22, Ui.CHROME, true, 0.30f));
        names.addView(Ui.text(this, "NFS CARBON 360 · PORT ANDROID", 10, Ui.CHROME_DIM, true, 0.18f));
        header.addView(names, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setGravity(Gravity.END);
        meta.addView(Ui.text(this, "v" + ReleaseUpdates.installed(this), 11, Ui.ACCENT, true, 0.06f));
        meta.addView(Ui.text(this, "Desenvolvido por Dementech", 10, Ui.CHROME_DIM, false, 0.02f));
        header.addView(meta, new LinearLayout.LayoutParams(-2, -2));
        return header;
    }

    /** Coluna da esquerda: identidade, estado do jogo e o botao de jogar. */
    private View buildLeftColumn() {
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hero = Ui.heroCard(this);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.bottomMargin = Ui.dp(this, 12);
        left.addView(hero, heroParams);

        hero.addView(Ui.section(this, "Arquivos do jogo"));
        status = Ui.label(this, "Verificando arquivos…", 13, Ui.CHROME);
        status.setPadding(0, 0, 0, Ui.dp(this, 10));
        hero.addView(status);
        checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.HORIZONTAL);
        hero.addView(checks);

        play = Ui.primaryButton(this, "JOGAR", ignored -> play());
        play.setOnClickListener(ignored -> play());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 58));
        playParams.topMargin = Ui.dp(this, 16);
        left.addView(play, playParams);

        LinearLayout fileActions = new LinearLayout(this);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 46));
        actionParams.topMargin = Ui.dp(this, 10);
        left.addView(fileActions, actionParams);
        accessButton = Ui.ghostButton(this, "Permitir acesso", ignored -> permission());
        fileActions.addView(accessButton, new LinearLayout.LayoutParams(0, -1, 1));
        importButton = Ui.ghostButton(this, "Importar minha cópia", ignored -> importGame());
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0, -1, 1);
        importParams.leftMargin = Ui.dp(this, 8);
        fileActions.addView(importButton, importParams);

        TextView note = Ui.label(this, "Armazenamento interno/Jogos/NFSCARBON", 10, Ui.CHROME_DIM);
        note.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, 0);
        left.addView(note);
        return left;
    }

    /** Coluna da direita: abas (jogo, diagnóstico, créditos). */
    private View buildRightColumn() {
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        bg.setCornerRadius(Ui.dp(this, 20));
        bg.setStroke(Ui.dp(this, 1), Ui.OUTLINE);
        right.setBackground(bg);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.START);
        tabGame = Ui.tab(this, "Jogo", true, ignored -> selectTab(0));
        tabDiagnostics = Ui.tab(this, "Diagnóstico", false, ignored -> selectTab(1));
        tabCredits = Ui.tab(this, "Créditos", false, ignored -> selectTab(2));
        tabs.addView(tabGame, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams diagParams = new LinearLayout.LayoutParams(-2, -2);
        diagParams.leftMargin = Ui.dp(this, 6);
        tabs.addView(tabDiagnostics, diagParams);
        LinearLayout.LayoutParams creditParams = new LinearLayout.LayoutParams(-2, -2);
        creditParams.leftMargin = Ui.dp(this, 6);
        tabs.addView(tabCredits, creditParams);
        right.addView(tabs, Ui.matchWrap());

        tabContent = new LinearLayout(this);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(tabContent);
        right.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        optionsList = new LinearLayout(this);
        optionsList.setOrientation(LinearLayout.VERTICAL);
        diagnosticsList = new LinearLayout(this);
        diagnosticsList.setOrientation(LinearLayout.VERTICAL);
        creditsList = new LinearLayout(this);
        creditsList.setOrientation(LinearLayout.VERTICAL);
        tabContent.addView(optionsList, Ui.matchWrap());
        tabContent.addView(diagnosticsList, Ui.matchWrap());
        tabContent.addView(creditsList, Ui.matchWrap());

        refreshOptions();
        buildDiagnostics();
        buildCredits();
        selectTab(0);
        return right;
    }

    private void selectTab(int index) {
        boolean game = index == 0, diag = index == 1, credits = index == 2;
        styleTab(tabGame, game);
        styleTab(tabDiagnostics, diag);
        styleTab(tabCredits, credits);
        optionsList.setVisibility(game ? View.VISIBLE : View.GONE);
        diagnosticsList.setVisibility(diag ? View.VISIBLE : View.GONE);
        creditsList.setVisibility(credits ? View.VISIBLE : View.GONE);
        if (diag) refreshDiagnostics();
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setTextColor(selected ? Ui.BLACK : Ui.CHROME_DIM);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? Ui.CHROME : 0x00000000);
        bg.setCornerRadius(Ui.dp(this, 20));
        tab.setBackground(bg);
    }

    // ------------------------------------------------------------ opções

    private void refreshOptions() {
        if (optionsList == null) return;
        optionsList.removeAllViews();
        optionsList.addView(Ui.section(this, "Configurações do jogo"));
        optionsList.addView(Ui.label(this,
                "Aplicadas ao pressionar JOGAR. Com antialiasing original o motor usa 1280×720. "
                        + "FPS altos e modo sem limite são experimentais.", 11, Ui.CHROME_DIM));

        GpuDrivers.Driver driver = GpuDrivers.selected(this);
        optionsList.addView(Ui.row(this, "Driver Vulkan",
                driver == null ? "Do sistema" : driver.name, Ui.ACCENT, ignored -> chooseDriver()));
        optionsList.addView(Ui.row(this, "Testar driver", "Verificar GPU e Vulkan", Ui.ACCENT,
                ignored -> probeDriver(false)));
        optionsList.addView(Ui.row(this, "Procurar atualizações",
                checkingUpdates ? "Consultando GitHub…" : "Versão " + ReleaseUpdates.installed(this),
                Ui.ACCENT, ignored -> checkUpdates(true)));
        // Perfil de GPU: um toque para a configuracao que o driver aguenta (veja GameOptions).
        optionsList.addView(Ui.row(this, "Perfil de GPU",
                profileLabel(), Ui.ACCENT, ignored -> chooseProfile()));
        for (GameOptions.Option option : GameOptions.ALL) {
            optionsList.addView(Ui.row(this, option.title,
                    option.label(GameOptions.get(this, option.key)), Ui.ACCENT,
                    ignored -> chooseOption(option)));
        }

        optionsList.addView(Ui.divider(this));
        optionsList.addView(Ui.section(this, "Arquivos do jogo"));
        LinearLayout reports = new LinearLayout(this);
        reports.addView(Ui.ghostButton(this, "Enviar diagnóstico", ignored -> report()),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1.5f));
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1);
        githubParams.leftMargin = Ui.dp(this, 8);
        reports.addView(Ui.ghostButton(this, "GitHub", ignored -> startActivity(new Intent(
                Intent.ACTION_VIEW, Uri.parse(Diagnostics.ISSUES)))), githubParams);
        optionsList.addView(reports);
    }

    // -------------------------------------------------------- diagnóstico

    private void buildDiagnostics() {
        diagnosticsList.addView(Ui.section(this, "Congelamento"));
        diagnosticsList.addView(Ui.label(this,
                "O congelamento aparece como imagem parada enquanto o jogo continua rodando: o motor para "
                        + "de entregar quadros na tela. O log do motor mede isso (veja doc/02-congelamento.md).\n\n"
                        + "Para ver esta leitura com o jogo aberto: use o botão de RECENTES e volte para o app "
                        + "(o jogo continua rodando em segundo plano). Se preferir, o botão VOLTAR fecha o jogo "
                        + "— e sair e voltar é justamente o que destrava a imagem travada.",
                11, Ui.CHROME_DIM));

        LinearLayout freezeCard = Ui.card(this);
        diagnosticsList.addView(freezeCard, Ui.matchWrap());
        LinearLayout.LayoutParams freezeParams = new LinearLayout.LayoutParams(-1, -2);
        freezeParams.topMargin = Ui.dp(this, 8);
        freezeCard.setLayoutParams(freezeParams);
        diagnosticsPanel = new DiagnosticsPanel(this);
        freezeCard.addView(diagnosticsPanel, Ui.matchWrap());
        LinearLayout freezeActions = new LinearLayout(this);
        LinearLayout.LayoutParams freezeActionParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 44));
        freezeActionParams.topMargin = Ui.dp(this, 12);
        freezeCard.addView(freezeActions, freezeActionParams);
        freezeActions.addView(Ui.ghostButton(this, "Atualizar leitura", ignored -> refreshDiagnostics()),
                new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams recoverParams = new LinearLayout.LayoutParams(0, -1, 1);
        recoverParams.leftMargin = Ui.dp(this, 8);
        freezeActions.addView(Ui.ghostButton(this, "Reiniciar renderização", ignored -> recoverRendering()),
                recoverParams);
        LinearLayout.LayoutParams relaunchParams = new LinearLayout.LayoutParams(0, -1, 1);
        relaunchParams.leftMargin = Ui.dp(this, 8);
        freezeActions.addView(Ui.ghostButton(this, "Reabrir o jogo", ignored -> confirmRelaunchGame()),
                relaunchParams);

        diagnosticsList.addView(Ui.divider(this));
        diagnosticsList.addView(Ui.section(this, "Perfis de desempenho"));
        diagnosticsList.addView(Ui.label(this,
                "Perfis ajustam as opções do motor para reduzir travadas. Depois de aplicar, JOGAR "
                        + "recomeça a sessão com as novas opções.", 11, Ui.CHROME_DIM));
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 46));
        presetParams.topMargin = Ui.dp(this, 8);
        diagnosticsList.addView(presets, presetParams);
        presets.addView(Ui.ghostButton(this, "Estável (60 fps)", ignored -> applyPreset(Preset.STABLE)),
                new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams preset2 = new LinearLayout.LayoutParams(0, -1, 1);
        preset2.leftMargin = Ui.dp(this, 8);
        presets.addView(Ui.ghostButton(this, "Leve (30 fps)", ignored -> applyPreset(Preset.RECORDING)),
                preset2);
        LinearLayout.LayoutParams preset3 = new LinearLayout.LayoutParams(0, -1, 1);
        preset3.leftMargin = Ui.dp(this, 8);
        presets.addView(Ui.ghostButton(this, "Qualidade", ignored -> applyPreset(Preset.QUALITY)),
                preset3);

        diagnosticsList.addView(Ui.divider(this));
        diagnosticsList.addView(Ui.section(this, "Log do motor"));
        diagnosticsList.addView(Ui.label(this,
                "O motor escreve carbon.log em Android/data/com.nfscarbon.android/files/logs. "
                        + "A ferramenta tools/diagnose_freeze.py analisa o arquivo inteiro e devolve o "
                        + "histórico de sessões.", 11, Ui.CHROME_DIM));
    }

    private void refreshDiagnostics() {
        if (diagnosticsPanel == null) return;
        diagnosticsPanel.refresh();
    }

    /** Reinicia a renderizacao: fecha o jogo e volta a rodar. */
    private void recoverRendering() {
        // Surface Vulkan morta: o CLEAR_TOP nao resolve (o motor mata o processo
        // ao fechar a janela e o jogo nem chega a reabrir). Nesse caso so
        // matar o processo e relancar resolve.
        FreezeReport.Snapshot snapshot = FreezeReport.read(DiagnosticsPanel.logFile(this), 256 * 1024);
        if (snapshot.surfaceDead()) {
            confirmRelaunchGame();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Reiniciar renderização")
                .setMessage("O jogo será fechado e aberto de novo. Use quando a imagem travar; "
                        + "é o mesmo truque de sair e voltar ao app, automático.")
                .setPositiveButton("Reiniciar", (dialog, which) -> {
                    // CLEAR_TOP recria a Activity do jogo: a surface Vulkan e o contexto
                    // SDL sao refeitos, que e exatamente a recuperacao que funciona
                    // quando a imagem trava (sair do app e voltar).
                    Intent intent = new Intent(this, GameActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    /** Reabre o jogo do zero (mata o processo): saida de surface Vulkan morta. */
    private void confirmRelaunchGame() {
        new AlertDialog.Builder(this)
                .setTitle("Reabrir o jogo")
                .setMessage("O jogo será fechado e aberto de novo do zero.\n\n"
                        + "É a solução quando a tela fica preta: o motor não conseguiu recriar a "
                        + "surface e não se recupera sem matar o processo.\n\n"
                        + "O que estiver em andamento (uma corrida) se perde.")
                .setPositiveButton("Reabrir", (dialog, which) -> GameActivity.relaunchGame(this))
                .setNegativeButton("Cancelar", null).show();
    }

    private enum Preset { STABLE, RECORDING, QUALITY }

    private void applyPreset(Preset preset) {
        switch (preset) {
            case STABLE:
                // 60 fps (escolha do dono). Medido: a 30 fps o ritmo fica perfeito
                // (600/600 swaps por 20 s), mas o congelamento aparece depois de um
                // tempo de jogo de qualquer jeito — a vigia e o que resolve.
                GameOptions.set(this, "resolution", "1280x720");
                GameOptions.set(this, "fps", "60");
                GameOptions.set(this, "single_pass", "true");
                GameOptions.set(this, "vsync", "true");
                GameOptions.set(this, "async", "true");
                GameOptions.set(this, "workers", "2");
                break;
            case RECORDING:
                // 30 fps: ritmo medido como perfeito (600/600 swaps por 20 s, nenhum
                // quadro acima de 36 ms). A gravacao de tela tambem evita o
                // congelamento no port original (issue #137 do upstream).
                GameOptions.set(this, "resolution", "1280x720");
                GameOptions.set(this, "fps", "30");
                GameOptions.set(this, "single_pass", "true");
                GameOptions.set(this, "vsync", "false");
                GameOptions.set(this, "async", "true");
                GameOptions.set(this, "workers", "2");
                break;
            case QUALITY:
                GameOptions.set(this, "resolution", "1280x720");
                GameOptions.set(this, "fps", "60");
                GameOptions.set(this, "single_pass", "false");
                GameOptions.set(this, "vsync", "true");
                GameOptions.set(this, "async", "false");
                GameOptions.set(this, "workers", "4");
                break;
        }
        refreshOptions();
        refreshDiagnostics();
        Toast.makeText(this, "Perfil aplicado. Use JOGAR para iniciar com as novas opções.", Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------ créditos

    private void buildCredits() {
        creditsList.addView(Ui.section(this, "Dementech"));
        creditsList.addView(Ui.label(this,
                "Launcher reescrito com identidade visual própria, em português do Brasil, com aba de "
                        + "diagnóstico do congelamento.\n\nDesenvolvido por Dementech.", 12, Ui.CHROME));
        creditsList.addView(Ui.divider(this));

        creditsList.addView(Ui.section(this, "Licenças"));
        creditsList.addView(Ui.label(this,
                "Código do Dementech: MIT.\n"
                        + "Componentes de terceiros sob GPL-3.0 e zlib — os avisos completos estão no "
                        + "repositório, em THIRD_PARTY_NOTICES.md.\n\n"
                        + "Need for Speed é marca da Electronic Arts. Este é um projeto comunitário, "
                        + "não oficial e sem vínculo com a EA.", 12, Ui.CHROME));
        creditsList.addView(Ui.divider(this));

        creditsList.addView(Ui.section(this, "Código-fonte"));
        creditsList.addView(Ui.row(this, "Repositório",
                "need-for-speed-carbon-dementech", Ui.ACCENT,
                ignored -> startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/AndersonFQueiroz/need-for-speed-carbon-dementech")))));
        creditsList.addView(Ui.row(this, "Problemas e ideias", "abrir issue", Ui.ACCENT,
                ignored -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Diagnostics.ISSUES)))));
    }

    // ------------------------------------------------------------ lógica

    private void importGame() {
        if (busy) return;
        if (!storageAllowed()) { permission(); return; }
        new AlertDialog.Builder(this).setTitle("Importar Carbon")
            .setMessage("Os arquivos serão copiados para Jogos/NFSCARBON. São necessários ~6 GB livres. "
                    + "A cópia existente fica como backup e ocupa espaço extra.")
            .setPositiveButton("Escolher pasta", (dialog, which) -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), IMPORT))
            .setNegativeButton("Cancelar", null).show();
    }

    @Override public void onResume() {
        super.onResume();
        launcherResumed = true;
        SaveIO.sync(this);
        refreshOptions();
        refresh();
        refreshDiagnostics();
        if (pendingUpdate != null && !busy) {
            ReleaseUpdates.Result result = pendingUpdate;
            pendingUpdate = null;
            showUpdate(result, pendingUpdateManual);
        }
    }

    @Override protected void onPause() {
        launcherResumed = false;
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("play_after_probe", playAfterProbe);
        super.onSaveInstanceState(state);
    }
    private void reportError(String message) {
        new AlertDialog.Builder(this).setTitle("Dementech · NFS Carbon")
                .setMessage(message).setPositiveButton("Ok", null).show();
    }

    private void checkUpdates(boolean manual) {
        if (checkingUpdates) return;
        checkingUpdates = true;
        if (optionsList != null) refreshOptions();
        ReleaseUpdates.check(this, manual, result -> {
            if (isFinishing() || isDestroyed()) return;
            checkingUpdates = false;
            refreshOptions();
            if (!launcherResumed || busy) {
                pendingUpdate = result; pendingUpdateManual = manual;
            } else showUpdate(result, manual);
        });
    }

    private void showUpdate(ReleaseUpdates.Result result, boolean manual) {
        if (result.error != null) {
            if (manual) reportError(result.error);
            return;  // Offline/rate-limited checks never affect playing.
        }
        if (!ReleaseVersion.newer(result.tag, ReleaseUpdates.installed(this))) {
            if (manual) new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Atualizações").setMessage("Você está na versão " + ReleaseUpdates.installed(this)
                            + ". Não há versão mais recente no GitHub.")
                    .setPositiveButton("Ok", null).show();
            return;
        }
        android.content.SharedPreferences updates = ReleaseUpdates.prefs(this);
        if (!manual && result.tag.equals(updates.getString("notified_tag", ""))) return;
        updates.edit().putString("notified_tag", result.tag).apply();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nova versão disponível: " + result.tag)
                .setMessage("Você pode atualizar pelo GitHub ou continuar jogando nesta versão. "
                        + "Instale o APK por cima do anterior para manter suas partidas e ajustes.")
                .setPositiveButton("Atualizar", (dialog, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.url))); }
                    catch (ActivityNotFoundException error) { reportError("Nenhum app encontrado para abrir o GitHub."); }
                })
                .setNegativeButton("Depois", null).show();
    }

    private void chooseDriver() {
        java.util.List<GpuDrivers.Driver> drivers = GpuDrivers.list(this);
        String[] names = new String[drivers.size() + 1];
        names[0] = "Driver do sistema";
        int selected = 0;
        for (int i = 0; i < drivers.size(); ++i) {
            names[i + 1] = drivers.get(i).name;
            if (drivers.get(i).id.equals(GpuDrivers.selectedId(this))) selected = i + 1;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Driver Vulkan")
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    GpuDrivers.select(this, which == 0 ? "" : drivers.get(which - 1).id);
                    refreshOptions(); dialog.dismiss();
                })
                .setPositiveButton("Importar ZIP", (dialog, which) -> {
                    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                            .addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(picker, REQUEST_DRIVER_ZIP);
                })
                .setNeutralButton("Remover selecionado", (dialog, which) -> {
                    GpuDrivers.Driver driver = GpuDrivers.selected(this);
                    if (driver == null) return;
                    try { GpuDrivers.remove(this, driver); refreshOptions(); }
                    catch (IOException error) { reportError(error.getMessage()); }
                })
                .setNegativeButton("Voltar", null).show();
    }

    /**
     * Troca o driver e ajusta o renderizador se o novo driver nao tem os recursos que o
     * renderizador nativo exige. Sem isso, escolher "Do sistema" num aparelho com Vulkan 1.1
     * (sem VK_EXT_descriptor_indexing) deixaria o jogo sem poder iniciar.
     */
    private void selectDriver(String id) {
        GpuDrivers.select(this, id);
        if (!"nativo".equals(GameOptions.get(this, GameOptions.RENDERER.key))) {
            refreshOptions();
            return;
        }
        org.json.JSONObject gpu = Diagnostics.gpu(this);
        boolean compatible = gpu.optBoolean("compatible");
        boolean probed = gpu.has("compatible");
        if (probed && !compatible) {
            // O renderizador nativo precisa de descriptor indexing; o Xenos emula a GPU do
            // console e funciona sem isso.
            GameOptions.set(this, GameOptions.RENDERER.key, "xenos");
            new AlertDialog.Builder(this).setTitle("Renderizador ajustado")
                .setMessage("Este driver não tem os recursos que o renderizador nativo exige "
                    + "(VK_EXT_descriptor_indexing). O renderizador foi trocado para Xenos, "
                    + "que emula a GPU do console e roda em qualquer Vulkan 1.1 — mais lento, "
                    + "mas estável.")
                .setPositiveButton("Ok", null).show();
        }
        refreshOptions();
    }

    private void probeDriver(boolean playAfter) {
        if (busy) return;
        busy = true;
        playAfterProbe = playAfter;
        play.setEnabled(false);
        startActivityForResult(new Intent(this, GpuProbeActivity.class), REQUEST_DRIVER_PROBE);
    }

    private void showDriverReport(String report) {
        try {
            Diagnostics.acceptGpu(this, report);
            org.json.JSONObject gpu = new org.json.JSONObject(report);
            boolean failed = gpu.optBoolean("driverLoadFailed") || gpu.has("probeError");
            String text = "GPU: " + gpu.optString("gpu", "indisponível")
                    + "\nVulkan: " + gpu.optString("vulkan", "indisponível")
                    + "\nConjuntos de descritores: " + gpu.optInt("maxBoundDescriptorSets")
                    + "\n" + (gpu.optBoolean("compatible") ? "Recursos do renderizador nativo disponíveis."
                            : "Recursos ausentes: " + gpu.optJSONArray("missing"));
            if (failed) text = "Não foi possível testar o driver. " + gpu.optString("probeError", "Erro ao carregar o Vulkan.");
            if (playAfterProbe && !failed && (!"nativo".equals(GameOptions.get(this, GameOptions.RENDERER.key)) || gpu.optBoolean("compatible"))) { playAfterProbe = false; playChecked(); return; }
            playAfterProbe = false;
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Teste do driver Vulkan").setMessage(text)
                    .setPositiveButton("Ok", null)
                    .setNeutralButton("Usar o do sistema", (dialog, which) -> {
                        selectDriver("");
                    }).show();
        } catch (Exception error) { playAfterProbe = false; reportError("Diagnóstico inválido: " + error.getMessage()); }
    }

    private void chooseOption(GameOptions.Option option) {
        int selected = 0;
        for (int i = 0; i < option.values.length; i++)
            if (option.values[i].equals(GameOptions.get(this, option.key))) selected = i;
        new AlertDialog.Builder(this).setTitle(option.title)
            .setSingleChoiceItems(option.labels, selected, (choice, value) -> {
                GameOptions.set(this, option.key, option.values[value]);
                refreshOptions(); choice.dismiss();
            }).setNegativeButton("Fechar", null).show();
    }

    private String profileLabel() {
        String profile = GameOptions.profileOf(this);
        for (int i = 0; i < GameOptions.PROFILE_VALUES.length; i++)
            if (GameOptions.PROFILE_VALUES[i].equals(profile)) return GameOptions.PROFILE_LABELS[i];
        return profile;
    }

    /** Escolhe o perfil de GPU: aplica as opcoes dele de uma vez. */
    private void chooseProfile() {
        String current = GameOptions.profileOf(this);
        int selected = 0;
        for (int i = 0; i < GameOptions.PROFILE_VALUES.length; i++)
            if (GameOptions.PROFILE_VALUES[i].equals(current)) selected = i;
        new AlertDialog.Builder(this).setTitle("Perfil de GPU")
            .setSingleChoiceItems(GameOptions.PROFILE_LABELS, selected, (choice, value) -> {
                if (value < 3) {
                    GameOptions.applyProfile(this, GameOptions.PROFILE_VALUES[value]);
                    refreshOptions();
                }
                choice.dismiss();
            })
            .setNegativeButton("Fechar", null).show();
    }

    private void showChecks() {
        checks.removeAllViews();
        File root = gameRoot();
        check("default.xex", new File(root, "default.xex").isFile());
        check("NFS", new File(root, "NFS").isDirectory());
        check("Movies", new File(root, "Movies").isDirectory() || new File(root, "MOVIES").isDirectory());
    }

    private void check(String name, boolean found) {
        TextView row = Ui.text(this, (found ? "✓ " : "✗ ") + name, 12,
                found ? Ui.OK : Ui.DANGER, true, 0.02f);
        row.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        checks.addView(row, new LinearLayout.LayoutParams(0, -2, name.equals("default.xex") ? 2 : 1));
    }

    private File gameRoot() {
        return new File(new File(Environment.getExternalStorageDirectory(), GAME_PARENT), GAME_FOLDER_NAME);
    }
    private boolean storageAllowed() {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
                : checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    private void permission() {
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 11);
    }
    private static boolean valid(File root) {
        return new File(root, "default.xex").isFile() && new File(root, "NFS").isDirectory()
                && (new File(root, "Movies").isDirectory() || new File(root, "MOVIES").isDirectory());
    }
    private void refresh() {
        if (status == null || busy) return;
        boolean ready = storageAllowed() && valid(gameRoot());
        play.setEnabled(ready);
        play.setAlpha(ready ? 1f : .45f);
        accessButton.setVisibility(storageAllowed() ? View.GONE : View.VISIBLE);
        showChecks();
        status.setText(!storageAllowed() ? "Permita o acesso à sua cópia."
                : ready ? "Sua cópia está pronta para iniciar."
                : "Importe sua cópia extraída do Xbox 360.");
    }
    private void play() {
        if (busy || !storageAllowed() || !valid(gameRoot())) { refresh(); return; }
        // Probe both the system native path and every imported driver in a fresh process.
        if (GpuDrivers.selected(this) != null || "nativo".equals(GameOptions.get(this, GameOptions.RENDERER.key))) {
            probeDriver(true);
        } else playChecked();
    }
    private void playChecked() {
        busy = true; play.setEnabled(false); status.setText("Verificando a edição do executável…");
        new Thread(() -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = new FileInputStream(new File(gameRoot(), "default.xex"))) {
                    byte[] buffer = new byte[65536];
                    for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
                }
                StringBuilder hash = new StringBuilder();
                for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                if (!EXPECTED_XEX.equals(hash.toString())) throw new IOException(
                        "Esta edição do default.xex não corresponde à recompilada. Use a cópia de Xbox 360 indicada no README.");
                runOnUiThread(() -> {
                    busy = false;
                    if ("nativo".equals(GameOptions.get(this, GameOptions.RENDERER.key))
                            && !ShaderBuilder.hasLibrary(shaderFolder())) {
                        buildShaders();
                        return;
                    }
                    refresh(); Diagnostics.recordLaunch(this);
                    startActivity(new Intent(this, GameActivity.class));
                });
            } catch (Exception error) { failed(error); }
        }, "CarbonEditionCheck").start();
    }
    // Where the native renderer looks first for its library: the executable folder (REX_APP_FOLDER in GameActivity).
    private File shaderFolder() { return new File(getFilesDir(), "nfscarbon/user"); }

    /** Builds nfscarbon_shaders.nfsp from the game files (a minute or two, only once), then starts the game. */
    private void buildShaders() {
        if (shaderBuilder != null) return;
        busy = true; play.setEnabled(false); play.setAlpha(.45f);
        status.setText("Gerando os shaders do renderizador nativo (só na primeira vez)…");
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        shaderBuilder = new ShaderBuilder(this, gameRoot(), shaderFolder(), new ShaderBuilder.Listener() {
            @Override public void onProgress(float fraction, String text) {
                status.setText(Math.round(fraction * 100) + " % · " + text);
            }
            @Override public void onDone(File library) {
                shadersFinished();
                refresh(); Diagnostics.recordLaunch(MainActivity.this);
                startActivity(new Intent(MainActivity.this, GameActivity.class));
            }
            @Override public void onError(String message) {
                shadersFinished();
                refresh();
                status.setText("Não foi possível gerar os shaders: " + message);
            }
        });
        shaderBuilder.start();
    }

    private void shadersFinished() {
        shaderBuilder = null; busy = false;
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_DRIVER_PROBE) {
            busy = false;
            refresh();
            showDriverReport(result == RESULT_OK && data != null ? data.getStringExtra("gpu_report")
                    : "{\"probeError\":\"O teste foi fechado ou cancelado. Você pode voltar ao driver do sistema.\",\"driverLoadFailed\":true}");
            return;
        }
        if (request == REQUEST_DRIVER_ZIP) {
            if (result != RESULT_OK || data == null || data.getData() == null) return;
            Uri zip = data.getData();
            busy = true; play.setEnabled(false);
            status.setText("Importando driver Vulkan…");
            new Thread(() -> {
                try {
                    GpuDrivers.Driver driver = GpuDrivers.importZip(this, zip);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        busy = false; GpuDrivers.select(this, driver.id); refreshOptions(); refresh();
                        status.setText("Driver importado: " + driver.name + ". Use Testar driver para verificar.");
                    });
                } catch (Exception error) { failed(error); }
            }, "CarbonDriverImport").start();
            return;
        }
        if (request != IMPORT || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); busy = true; play.setEnabled(false); importButton.setEnabled(false);
        status.setText("Copiando seu jogo. Deixe esta tela aberta…");
        new Thread(() -> {
            File staging = new File(new File(Environment.getExternalStorageDirectory(), GAME_PARENT), GAME_FOLDER_NAME + "-import-" + System.currentTimeMillis());
            try {
                DocumentFile source = DocumentFile.fromTreeUri(this, uri);
                if (source == null || !source.isDirectory()) throw new IOException("A pasta não pode ser aberta.");
                if (source.findFile("default.xex") == null || source.findFile("NFS") == null
                        || (source.findFile("Movies") == null && source.findFile("MOVIES") == null))
                    throw new IOException("Escolha a pasta do jogo que contém default.xex, NFS e Movies.");
                if (!staging.mkdirs()) throw new IOException("Não foi possível criar a pasta de importação.");
                copy(source, staging);
                if (!valid(staging)) throw new IOException("A pasta precisa conter default.xex, NFS e Movies.");
                File target = gameRoot();
                File backup = new File(target.getParentFile(), GAME_FOLDER_NAME + "-backup-" + System.currentTimeMillis());
                boolean backedUp = target.exists();
                if (backedUp && !target.renameTo(backup)) throw new IOException("Não foi possível guardar a cópia anterior.");
                if (!staging.renameTo(target)) {
                    if (backedUp) backup.renameTo(target);
                    throw new IOException("Não foi possível instalar a pasta copiada.");
                }
                runOnUiThread(() -> { busy = false; importButton.setEnabled(true); refresh(); });
            } catch (Exception error) { failed(error); }
        }, "CarbonImport").start();
    }
    private void copy(DocumentFile source, File target) throws IOException {
        for (DocumentFile child : source.listFiles()) {
            String name = child.getName();
            if (name == null || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\"))
                throw new IOException("Nome de arquivo inválido.");
            File destination = new File(target, name);
            if (child.isDirectory()) {
                if (!destination.mkdir()) throw new IOException("Não foi possível criar " + name);
                copy(child, destination);
            } else {
                try (InputStream input = getContentResolver().openInputStream(child.getUri());
                        FileOutputStream output = new FileOutputStream(destination)) {
                    if (input == null) throw new IOException("Não foi possível ler " + name);
                    byte[] buffer = new byte[1024 * 1024];
                    for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
                }
            }
        }
    }
    private void report() {
        if (busy) return;
        busy = true; status.setText("Preparando registro e diagnóstico…");
        new Thread(() -> {
            try {
                File report = Diagnostics.createReport(this);
                runOnUiThread(() -> {
                    busy = false; refresh();
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".reports", report);
                    Intent email = new Intent(Intent.ACTION_SEND).setType("application/zip")
                        .putExtra(Intent.EXTRA_EMAIL, new String[]{Diagnostics.EMAIL})
                        .putExtra(Intent.EXTRA_SUBJECT, "NFS Carbon 360 (Dementech): diagnóstico de " + Build.MODEL)
                        .putExtra(Intent.EXTRA_TEXT, Diagnostics.summary(this) + "\nDescreva aqui o que aconteceu.\n")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    email.setClipData(ClipData.newUri(getContentResolver(), "Diagnóstico Carbon", uri));
                    try { startActivity(Intent.createChooser(email, "Enviar registro")); }
                    catch (Exception error) { failed(error); }
                });
            } catch (Exception error) { failed(error); }
        }, "CarbonReport").start();
    }
    private void failed(Exception error) {
        runOnUiThread(() -> {
            busy = false; importButton.setEnabled(true); refresh(); status.setText(error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
