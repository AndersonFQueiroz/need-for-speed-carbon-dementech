// Dementech · NFS Carbon — sincronização do save do jogo.
//
// O motor grava o perfil no diretório privado do app (mesmo uid), que o
// Termux/adb não alcançam. Este hook expõe o arquivo em
// Android/data/com.nfscarbon.android/files/saveio/, onde a ferramenta de linha
// de comando (ou o futuro editor de save dentro do app) consegue ler e
// entregar correções.
//
//   out/ALIAS_ANDERSON  -> cópia do save atual (exportada a cada abertura)
//   in/ALIAS_ANDERSON   -> save corrigido; é importado no próximo boot
//
// SPDX-License-Identifier: MIT
package com.nfscarbon.android;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class SaveIO {
    private static final String SAVE_NAME = "ALIAS_ANDERSON";
    private static final String PROFILE_DIR = "nfscarbon/user";
    private static final String TITLE_DIR = "454107EC/00000001";

    private SaveIO() {}

    /** Encontra o arquivo de save do jogador dentro dos dados privados do app. */
    static File findSave(Context context) {
        File users = new File(context.getFilesDir(), PROFILE_DIR);
        File[] list = users.listFiles();
        if (list == null) return null;
        for (File user : list) {
            if (!user.isDirectory()) continue;
            File save = new File(new File(new File(user, TITLE_DIR), SAVE_NAME), SAVE_NAME);
            if (save.isFile()) return save;
        }
        return null;
    }

    /**
     * Exporta o save atual e importa o que estiver em saveio/in.
     * Chamado no onCreate (antes do jogo subir) e no onResume do launcher.
     */
    static void sync(Context context) {
        File save = findSave(context);
        File external = context.getExternalFilesDir(null);
        if (save == null || external == null) return;
        File io = new File(external, "saveio");
        File in = new File(new File(io, "in"), SAVE_NAME);
        File out = new File(new File(io, "out"), SAVE_NAME);

        // 1) importa um save corrigido, se houver
        if (in.isFile()) {
            try {
                copy(in, save);
                in.delete();
            } catch (IOException ignored) {
            }
        }
        // 2) exporta o save atual
        try {
            out.getParentFile().mkdirs();
            copy(save, out);
        } catch (IOException ignored) {
        }
    }

    private static void copy(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream input = new FileInputStream(from);
             OutputStream output = new FileOutputStream(to)) {
            byte[] buffer = new byte[1 << 16];
            for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
        }
    }
}
