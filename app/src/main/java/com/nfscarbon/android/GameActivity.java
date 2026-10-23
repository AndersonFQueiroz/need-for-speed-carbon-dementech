// Adapted from codepdbh/nfsmw-android (5f581c6). SPDX-License-Identifier: GPL-3.0-only
package com.nfscarbon.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public final class GameActivity extends SDLActivity
        implements TouchControlsView.Host, InputManager.InputDeviceListener, SensorEventListener {
    private static final String TAG = "NFSCARBON";
    /** true enquanto a Activity do jogo existe: o save so pode ser importado com o jogo fechado. */
    static boolean isRunning = false;
    private static final String SETTINGS = "nfscarbon.toml";

    private String gameRoot;
    private String userRoot;
    private String cacheRoot;
    private TouchControlsView controls;
    private LinearLayout toolbar;
    private InputManager inputManager;
    private SensorManager sensorManager;
    private Sensor gravity;
    private TextView fpsLabel;
    private final Handler frameHandler = new Handler(Looper.getMainLooper());
    // Sem vigia em segundo plano (decisao do dono, 22/10): o app nao age sozinho
    // -- nao renova a surface e nao relanca o jogo. Quem olha o log do motor e o
    // painel de diagnostico, quando aberto.
    private final Runnable updateFrames = new Runnable() {
        @Override public void run() {
            if (fpsLabel == null || isFinishing()) return;
            try {
                long frameUs = nativeFrameTimeUs();
                fpsLabel.setText(frameUs > 0 ? String.format(java.util.Locale.ROOT,
                        "%.0f FPS · %.1f ms", 1000000.0 / frameUs, frameUs / 1000.0) : "FPS · carregando…");
            } catch (UnsatisfiedLinkError ignored) { fpsLabel.setText("FPS · carregando…"); }
            frameHandler.postDelayed(this, 1000);
        }
    };

    private static native void nativeSetStretch(boolean stretch);
    private static native long nativeFrameTimeUs();

    @Override
    protected void onCreate(Bundle state) {
        File appRoot = new File(getFilesDir(), "nfscarbon");
        gameRoot = new File(new File(Environment.getExternalStorageDirectory(), MainActivity.GAME_PARENT), MainActivity.GAME_FOLDER_NAME).getAbsolutePath();
        userRoot = new File(appRoot, "user").getAbsolutePath();
        cacheRoot = new File(appRoot, "cache").getAbsolutePath();
        new File(userRoot).mkdirs();
        new File(cacheRoot).mkdirs();
        // The native side keeps nfscarbon.toml, the pipeline cache and its logs in the "executable folder", which
        // for app_process would be /system/bin (see GetExecutableFolder in filesystem_posix.cpp).
        try {
            isRunning = true;
        Os.setenv("REX_APP_FOLDER", userRoot, true);
        } catch (ErrnoException e) {
            Log.w(TAG, "REX_APP_FOLDER", e);
        }
        installSettings();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(state);
        useWholeScreen();

        ViewGroup root = findViewById(android.R.id.content);
        controls = new TouchControlsView(this);
        controls.setHost(this);
        root.addView(controls, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.FILL));
        toolbar = buildToolbar();
        toolbar.setVisibility(View.GONE);
        // In the middle of the screen, the one area the default layout leaves free, so no control hides under it.
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(toolbar, barParams);
        if ("true".equals(GameOptions.get(this, "fps_overlay"))) {
            fpsLabel = new TextView(this);
            fpsLabel.setTextColor(Color.WHITE);
            fpsLabel.setTextSize(12);
            fpsLabel.setPadding(dp(8), dp(4), dp(8), dp(4));
            fpsLabel.setBackgroundColor(0x99081017);
            FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            frameParams.topMargin = dp(62);
            root.addView(fpsLabel, frameParams);
        }

        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (gravity == null) {
                gravity = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }
    }

    // SDL picks an orientation from the window size when it creates its window; the game is landscape only.
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    // The image and the controls use all of the panel, camera cutout included, at its highest refresh rate
    // when the FPS limit asks for more than 60.
    private void useWholeScreen() {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        String requestedFps = GameOptions.get(this, "fps");
        int fps = "unlimited".equals(requestedFps) ? 240 : Integer.parseInt(requestedFps);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display display = getWindowManager().getDefaultDisplay();
            Display.Mode best = null;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() != display.getMode().getPhysicalWidth()) {
                    continue;
                }
                // The lowest mode that still shows every frame the game makes.
                if (mode.getRefreshRate() + 0.5f >= fps &&
                        (best == null || mode.getRefreshRate() < best.getRefreshRate())) {
                    best = mode;
                }
            }
            if (best == null) for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == display.getMode().getPhysicalWidth()
                        && (best == null || mode.getRefreshRate() > best.getRefreshRate())) best = mode;
            }
            if (best != null) {
                attrs.preferredDisplayModeId = best.getModeId();
            }
        }
        getWindow().setAttributes(attrs);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        frameHandler.removeCallbacks(updateFrames);
        if (fpsLabel != null) frameHandler.post(updateFrames);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(this, null);
            refreshGamepads();
        }
        updateTiltListener();
        applyStretch(controls.stretch);
    }

    private static void applyStretch(boolean stretch) {
        try {
            nativeSetStretch(stretch);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "libmain no cargada", e);
        }
    }

    @Override
    protected void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        frameHandler.removeCallbacks(updateFrames);
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(this);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (controls.isEditing()) {
            controls.setEditing(false);
        }
        super.onPause();
    }

    @Override
    protected String[] getArguments() {
        List<String> args = new ArrayList<>();
        args.add("--game_data_root=" + gameRoot);
        args.add("--user_data_root=" + userRoot);
        args.add("--cache_root=" + cacheRoot);
        // While the native renderer is experimental its log goes to the app's external folder
        // (Android/data/com.nfscarbon.android/files/logs), where it can be read over USB.
        boolean nativeRenderer = "nativo".equals(GameOptions.get(this, GameOptions.RENDERER.key));
        File external = getExternalFilesDir(null);
        File logs = nativeRenderer && external != null ? new File(external, "logs") : new File(userRoot, "logs");
        if (!logs.mkdirs() && !logs.isDirectory()) logs = new File(userRoot, "logs");
        logs.mkdirs();
        args.add("--log_file=" + new File(logs, "carbon.log").getAbsolutePath());
        // The launcher's graphics options win over nfscarbon.toml.
        args.addAll(GameOptions.arguments(this));
        // Testing: one extra argument per line in Android/data/com.nfscarbon.android/files/args.txt (written over
        // USB; the app never creates it).
        if (external != null) {
            File extra = new File(external, "args.txt");
            if (extra.isFile()) {
                try {
                    for (String line : java.nio.file.Files.readAllLines(extra.toPath())) {
                        line = line.trim();
                        if (line.startsWith("--")) args.add(line);
                    }
                } catch (java.io.IOException ignored) {
                }
            }
        }
        if (getSharedPreferences("nfscarbon_controls", MODE_PRIVATE).getBoolean("stretch", true)) {
            args.add("--present_letterbox=false");
            args.add("--present_safe_area_x=100");
            args.add("--present_safe_area_y=100");
        }
        return args.toArray(new String[0]);
    }

    // ---- Physical gamepads ---------------------------------------------------------------------------------

    private static boolean isGamepad(InputDevice device) {
        if (device == null || device.isVirtual()) {
            return false;
        }
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private void refreshGamepads() {
        boolean any = false;
        for (int id : inputManager.getInputDeviceIds()) {
            any |= isGamepad(inputManager.getInputDevice(id));
        }
        if (any) {
            controls.onGamepadActive();
        } else {
            controls.onGamepadGone();
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        refreshGamepads();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        refreshGamepads();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        refreshGamepads();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isGamepad(event.getDevice())) {
            controls.onGamepadActive();
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isGamepad(event.getDevice())) {
            controls.onGamepadActive();
        }
        return super.dispatchGenericMotionEvent(event);
    }

    // ---- Tilt steering -------------------------------------------------------------------------------------

    private void updateTiltListener() {
        if (sensorManager == null || gravity == null) {
            return;
        }
        sensorManager.unregisterListener(this);
        if (controls.tiltSteering) {
            sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // The "up" of the world in screen coordinates; turning the phone like a wheel rotates it.
        float gx = event.values[0], gy = event.values[1];
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        float ux, uy;
        if (rotation == Surface.ROTATION_270) {
            ux = -gx;
            uy = -gy;
        } else {
            ux = gx;
            uy = gy;
        }
        double degrees = Math.toDegrees(Math.atan2(uy, ux));
        controls.setTilt((float) (degrees / 30.0));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ---- Layout editor -------------------------------------------------------------------------------------

    @Override
    public void onEditModeChanged(boolean editing) {
        toolbar.setVisibility(editing ? View.VISIBLE : View.GONE);
    }

    // ---- Diagnostico e vigia de congelamento (Dementech) --------------------------------------------

    /**
     * Configuracao do jogo por cima do jogo: todas as opcoes do motor, no estilo
     * do menu do Carbon, com "aplicar agora" (reinicia a sessao com as novas
     * opcoes; o save fica intacto).
     */
    private void showGameOptions() {
        ConfigPanel panel = new ConfigPanel(this, () -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Aplicar agora?")
                    .setMessage("A sessão do jogo será reiniciada com as novas opções. "
                            + "O save é preservado.")
                    .setPositiveButton("Aplicar", (d, w) -> recoverRendering())
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(panel);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Configurações do jogo")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .create();
        dialog.show();
    }

    /** Abre o painel de diagnostico por cima do jogo (botao "Diagnostico" do editor de controles). */
    private void showDiagnostics() {
        DiagnosticsPanel panel = new DiagnosticsPanel(this);
        panel.refresh();
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Dementech · diagnóstico")
                .setView(panel)
                .setPositiveButton("Atualizar", (d, which) -> showDiagnostics())
                .setNeutralButton("Reiniciar renderização", (d, which) -> recoverRendering())
                .setNegativeButton("Fechar", null)
                .create();
        dialog.setOnShowListener(ignored -> panel.refresh());
        dialog.show();
    }

    /**
     * Recria a Activity: a surface Vulkan e o contexto SDL sao refeitos, que e
     * o que destrava a imagem quando ela para (sair do app e voltar).
     */
    private void recoverRendering() {
        // Se a surface Vulkan morreu, o truque de "fundo e volta" nao resolve:
        // o motor entra em laco tentando recriar a surface e falha (tela preta
        // definitiva). Nesse caso so matar o processo resolve.
        FreezeReport.Snapshot snapshot = FreezeReport.read(DiagnosticsPanel.logFile(this), 256 * 1024);
        if (snapshot.surfaceDead()) {
            new AlertDialog.Builder(this)
                    .setTitle("A surface Vulkan morreu")
                    .setMessage("O motor não consegue recriar a surface, então a tela fica preta "
                            + "para sempre. A única saída é reabrir o jogo.\n\n"
                            + "O que estiver em andamento (uma corrida) se perde.")
                    .setPositiveButton("Reabrir o jogo", (dialog, which) -> relaunchGame(this))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        // recreate() nao serve: o motor encerra o processo ao fechar a janela
        // ("Title terminated; hard-exiting process") e o usuario cai no launcher.
        // O que resolve e recriar a SURFACE, e o mesmo truque de "sair e voltar":
        // mandar a task para o fundo (a surface e destruida) e trazer de volta.
        try {
            moveTaskToBack(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent bring = new Intent(this, GameActivity.class);
                bring.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try {
                    startActivity(bring);
                } catch (RuntimeException error) {
                    Log.w(TAG, "recover", error);
                }
            }, 700);
        } catch (RuntimeException error) {
            Log.w(TAG, "recover", error);
        }
    }

    /**
     * Mata o processo e relanca o jogo: e a unica saida de uma surface Vulkan
     * morta (o motor nao recria a surface, a tela fica preta para sempre).
     * O relancamento e pedido ao sistema antes de matar o processo, entao o
     * jogo volta sozinho em um processo novo.
     */
    static void relaunchGame(Activity from) {
        try {
            Intent launch = new Intent(from, GameActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent relaunch = PendingIntent.getActivity(from, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            relaunch.send();
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            Log.w(TAG, "relaunch", error);
        }
        from.finishAffinity();
        Runtime.getRuntime().exit(0);
    }


    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6121A22);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x55FFFFFF);
        bar.setBackground(bg);

        TextView hint = new TextView(this);
        hint.setText("Toque um controle para escolher · arraste para mover · pince para mudar o tamanho");
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        bar.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(toolButton("Tamanho −", v -> controls.resizeSelected(1f / 1.12f)));
        row.addView(toolButton("Tamanho +", v -> controls.resizeSelected(1.12f)));
        row.addView(toolButton("Mostrar / ocultar", v -> controls.toggleSelectedVisible()));
        row.addView(toolButton("Ajustes", v -> showSettings()));
        row.addView(toolButton("Restaurar", v -> confirmReset()));
        Button done = toolButton("Pronto", v -> controls.setEditing(false));
        ((GradientDrawable) done.getBackground()).setColor(0xFFE07B00);
        row.addView(done);
        bar.addView(row);
        return bar;
    }

    private Button toolButton(String label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A3440);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(6), dp(4), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(onClick);
        return b;
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Restaurar controles")
                .setMessage("Voltar todos os controles à posição e ao tamanho originais?")
                .setPositiveButton("Restaurar", (d, w) -> controls.resetLayout())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(8));

        panel.addView(toggle("Imagem esticada em toda a tela", controls.stretch, on -> {
            controls.stretch = on;
            applyStretch(on);
        }));
        panel.addView(toggle("Vibrar ao tocar", controls.haptics, on -> controls.haptics = on));
        panel.addView(toggle("Ocultar os controles ao usar um controle", controls.hideWithGamepad,
                on -> controls.hideWithGamepad = on));
        panel.addView(toggle("Direção inclinando o aparelho", controls.tiltSteering, on -> {
            controls.tiltSteering = on;
            updateTiltListener();
        }));
        panel.addView(slider("Sensibilidade da inclinação", 50, 250,
                Math.round(controls.tiltSensitivity * 100), v -> controls.tiltSensitivity = v / 100f));
        panel.addView(slider("Opacidade dos controles", 15, 100,
                Math.round(controls.opacity * 100), v -> {
                    controls.opacity = v / 100f;
                    controls.invalidate();
                }));

        // Os dois atalhos ficam no TOPO do painel: em paisagem o dialogo e
        // baixo e o fim do painel cai fora da tela.
        android.widget.Button options = new android.widget.Button(this);
        options.setText("Configurações do jogo");
        options.setAllCaps(false);
        options.setOnClickListener(v -> {
            try {
                showGameOptions();
            } catch (RuntimeException ignored) { }
        });
        panel.addView(options);

        android.widget.Button diagnostics = new android.widget.Button(this);
        diagnostics.setText("Diagnóstico do congelamento");
        diagnostics.setAllCaps(false);
        diagnostics.setOnClickListener(v -> {
            try {
                showDiagnostics();
            } catch (RuntimeException ignored) { }
        });
        panel.addView(diagnostics);
        panel.addView(new android.view.View(this));

        // Painel rolavel: as opcoes ficam acessiveis em qualquer altura de tela.
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(panel);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Ajustes dos controles")
                .setView(scroll)
                .setPositiveButton("Fechar", (d, w) -> controls.save())
                .setOnDismissListener(d -> controls.save())
                .show();
    }

    private interface OnToggle {
        void set(boolean on);
    }

    private interface OnValue {
        void set(int value);
    }

    private View toggle(String label, boolean value, OnToggle onToggle) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setChecked(value);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setOnCheckedChangeListener((v, on) -> onToggle.set(on));
        return s;
    }

    private View slider(String label, int min, int max, int value, OnValue onValue) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(4));
        TextView title = new TextView(this);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        box.addView(title);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        title.setText(label + ": " + value + " %");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                title.setText(label + ": " + (progress + min) + " %");
                onValue.set(progress + min);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        box.addView(bar);
        return box;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---- Settings file -------------------------------------------------------------------------------------

    // Copies the Android settings from the APK. A copy the player edited is kept: it is only replaced while
    // it is still the one a previous APK installed.
    private void installSettings() {
        File target = new File(userRoot, SETTINGS);
        SharedPreferences prefs = getSharedPreferences("nfscarbon", MODE_PRIVATE);
        try {
            byte[] bundled = readAll(getAssets().open(SETTINGS));
            String bundledHash = sha256(bundled);
            String installedHash = prefs.getString("settings_sha256", "");
            if (target.isFile()) {
                String currentHash = sha256(readAll(new FileInputStream(target)));
                if (currentHash.equals(bundledHash) || !currentHash.equals(installedHash)) {
                    return;
                }
            }
            try (OutputStream out = new FileOutputStream(target)) {
                out.write(bundled);
            }
            prefs.edit().putString("settings_sha256", bundledHash).apply();
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.w(TAG, "No se pudo instalar " + SETTINGS, e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            for (int n; (n = stream.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String sha256(byte[] data) throws NoSuchAlgorithmException {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
