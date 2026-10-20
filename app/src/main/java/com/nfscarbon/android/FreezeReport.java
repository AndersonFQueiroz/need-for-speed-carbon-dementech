// Dementech · NFS Carbon — leitura do log do motor para o diagnóstico de congelamento.
// SPDX-License-Identifier: MIT
package com.nfscarbon.android;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Analisa o log nativo (carbon.log) do mesmo jeito que tools/diagnose_freeze.py,
 * mas dentro do app, para a aba "Diagnóstico".
 *
 * O motor registra o contador cumulativo de quadros entregues ao display
 * ("adquisicion anticipada: N de M fotogramas"). Se M para de crescer enquanto o
 * log continua, o jogo esta vivo mas a imagem nao esta sendo apresentada: e o
 * congelamento relatado. Ver doc/02-congelamento.md.
 */
final class FreezeReport {
    private static final java.text.SimpleDateFormat STAMP =
            new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS", Locale.US);

    /** Ultima leitura do log. */
    static final class Snapshot {
        long lastLogMillis;            // quando o motor escreveu por ultima vez
        long presented;                // contador de quadros apresentados
        long presentedAt;              // quando o contador foi lido
        double lastFrameMs;            // ultimo tempo de quadro registrado
        double presentedFps;           // quadros entregues por segundo (janela)
        int dropped;                   // apresentacoes descartadas (swapchain desatualizado)
        double swapsPerSecond;         // ritmo real de entrega de quadro (histograma do motor)
        int slowFrames;                // quadros acima de 60 ms na ultima janela
        double worstFrameMs;           // pior quadro da ultima janela
        long bytes;                    // tamanho do log
        boolean readable;

        /** Segundos sem o motor apresentar quadro. */
        long stalledSeconds() {
            if (!readable || presentedAt <= 0) return 0;
            long idle = (presentedAt - lastLogMillis) / 1000;
            return idle > 0 ? idle : 0;
        }

        boolean suspect() {
            return readable && stalledSeconds() >= 20;
        }
    }

    private FreezeReport() {}

    static File logFile(File externalFilesDir) {
        if (externalFilesDir == null) return null;
        File logs = new File(externalFilesDir, "logs");
        File[] files = logs.listFiles();
        if (files == null) return null;
        File best = null;
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith("carbon") || !name.endsWith(".log")) continue;
            if (best == null || file.lastModified() > best.lastModified()) best = file;
        }
        return best;
    }

    /** Le a cauda do log (no maximo 512 KB) e extrai os indicadores. */
    static Snapshot read(File log) {
        return read(log, 512 * 1024);
    }

    /** Le apenas os ultimos bytes do log (para a leitura periodica do watchdog). */
    static Snapshot read(File log, int tailBytes) {
        Snapshot snapshot = new Snapshot();
        if (log == null || !log.isFile()) return snapshot;
        snapshot.readable = true;
        snapshot.bytes = log.length();
        snapshot.lastLogMillis = log.lastModified();
        String tail;
        try (RandomAccessFile raf = new RandomAccessFile(log, "r")) {
            long size = raf.length();
            int wanted = (int) Math.min(size, tailBytes);
            raf.seek(size - wanted);
            byte[] buffer = new byte[wanted];
            raf.readFully(buffer);
            tail = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException error) {
            snapshot.readable = false;
            return snapshot;
        }
        List<long[]> presented = new ArrayList<>();  // {momento, contador}
        int lastSwaps = 0, lastSlow = 0;
        double lastWorst = 0;
        long lastStamp = 0;
        double frame = 0;
        int dropped = 0;
        for (String line : tail.split("\n")) {
            long stamp = timestamp(line);
            if (stamp > 0) lastStamp = stamp;
            int at = line.indexOf("adquisicion anticipada:");
            if (at >= 0) {
                int open = line.indexOf("de ", at);
                int close = line.indexOf(" fotogramas", open);
                if (open > 0 && close > open) {
                    try {
                        long counter = Long.parseLong(line.substring(open + 3, close).trim());
                        presented.add(new long[]{stamp, counter});
                    } catch (NumberFormatException ignored) { }
                }
            }
            at = line.indexOf("fotograma de ");
            if (at >= 0) {
                int end = line.indexOf(" ms", at);
                if (end > at) {
                    try { frame = Double.parseDouble(line.substring(at + 13, end).trim()); }
                    catch (NumberFormatException ignored) { }
                }
            }
            if (line.contains("Presentation to the swapchain image has been dropped")) dropped++;
            at = line.indexOf("intervalos entre Swaps: ");
            if (at >= 0) {
                int end = line.indexOf("; peor fotograma ", at);
                if (end > at) {
                    int total = 0, slow = 0;
                    for (String part : line.substring(at + 24, end).split("\\|")) {
                        String piece = part.trim();
                        int space = piece.lastIndexOf(' ');
                        if (space <= 0) continue;
                        String range = piece.substring(0, space).replace("ms", "").trim();
                        try {
                            int count = Integer.parseInt(piece.substring(space + 1).trim());
                            total += count;
                            double lower = 0;
                            if (range.startsWith(">=")) lower = Double.parseDouble(range.substring(2));
                            else if (range.contains("-")) lower = Double.parseDouble(range.substring(0, range.indexOf('-')));
                            else if (range.startsWith("<")) continue;
                            if (lower >= 60) slow += count;
                        } catch (NumberFormatException ignored) { }
                    }
                    int worstAt = line.indexOf("peor fotograma ", end);
                    if (worstAt >= 0) {
                        int ms = line.indexOf(" ms", worstAt);
                        if (ms > worstAt) {
                            try {
                                lastWorst = Double.parseDouble(line.substring(worstAt + 15, ms).trim());
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                    lastSwaps = total;
                    lastSlow = slow;
                }
            }
        }
        snapshot.lastFrameMs = frame;
        snapshot.dropped = dropped;
        snapshot.slowFrames = lastSlow;
        snapshot.worstFrameMs = lastWorst;
        if (lastSwaps > 0 && presented.size() >= 2) {
            // O motor escreve o histograma a cada ~20 s com o total de swaps da janela.
            long[] last = presented.get(presented.size() - 1);
            snapshot.swapsPerSecond = lastSwaps / 20.0;
        }
        if (!presented.isEmpty()) {
            long[] first = presented.get(0);
            long[] last = presented.get(presented.size() - 1);
            snapshot.presented = last[1];
            snapshot.presentedAt = last[0];
            double seconds = (last[0] - first[0]) / 1000.0;
            long delta = last[1] - first[1];
            if (seconds > 1 && delta > 0) snapshot.presentedFps = delta / seconds;
        } else if (lastStamp > 0) {
            snapshot.presentedAt = lastStamp;
        }
        return snapshot;
    }

    /** Extrai o timestamp "[2026-10-20 18:00:09.827]" em epoch milis. */
    private static long timestamp(String line) {
        int open = line.indexOf('[');
        if (open < 0) return 0;
        int close = line.indexOf(']', open);
        if (close < 0) return 0;
        String stamp = line.substring(open + 1, close);
        if (stamp.length() < 23) return 0;
        try {
            String normalized = stamp.substring(0, 23).replace('-', '/').replace('.', ':');
            return STAMP.parse(normalized).getTime();
        } catch (java.text.ParseException | RuntimeException ignored) {
            return 0;
        }
    }

    /** Texto pronto para a tela de diagnostico. */
    static String describe(Snapshot snapshot) {
        if (!snapshot.readable) return "Sem log do motor encontrado.";
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.US, "Quadros apresentados: %,d%n", snapshot.presented));
        if (snapshot.presentedFps > 0) {
            text.append(String.format(Locale.US, "Média de apresentação: %.1f fps%n", snapshot.presentedFps));
        }
        if (snapshot.lastFrameMs > 0) {
            text.append(String.format(Locale.US, "Último quadro registrado: %.1f ms (%.1f fps)%n",
                    snapshot.lastFrameMs, 1000.0 / snapshot.lastFrameMs));
        }
        if (snapshot.swapsPerSecond > 0) {
            text.append(String.format(Locale.US, "Ritmo real: %.1f fps entregues (última janela)%n",
                    snapshot.swapsPerSecond));
            text.append(String.format(Locale.US, "Quadros lentos (>60 ms) na janela: %d · pior quadro: %.1f ms%n",
                    snapshot.slowFrames, snapshot.worstFrameMs));
        }
        text.append(String.format(Locale.US, "Apresentações descartadas: %d%n", snapshot.dropped));
        text.append(String.format(Locale.US, "Log: %.1f MB%n", snapshot.bytes / 1e6));
        if (snapshot.suspect()) {
            text.append(String.format(Locale.US, "⚠ Sem apresentação há %d s — imagem provavelmente travada.",
                    snapshot.stalledSeconds()));
        } else {
            text.append("Apresentação normal.");
        }
        return text.toString();
    }
}
