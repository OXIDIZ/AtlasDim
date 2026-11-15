package com.mmwtl.atlasdim;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Explicit user-triggered backup of Kanzi resource.kzb over the existing QNX/Android
 * NFS share. Reads the source; writes only a uniquely named file in /shared.
 * Never changes cluster-hmi processes or the original resource.
 */
final class KzbExport {
    private static final String SOURCE =
            "/apps/cluster/FX11_HEV/bin/hmi/GLY_FS11-A2J1/ClusterHMI/resource.kzb";
    private static final File SHARE = new File("/data/vendor/nfs/shared");
    private static final long EXPECTED = 60536188L;
    private static final long MAX = 128L * 1024 * 1024;
    private static final long TIMEOUT = 180000L;

    interface Progress {
        void update(String message);
    }

    private KzbExport() { }

    static File export(Context context, String host, String password, Progress progress)
            throws IOException {
        if (!SHARE.isDirectory() || !SHARE.canRead()) {
            throw new IOException("Android не видит /data/vendor/nfs/shared");
        }
        File outputDir = ReportProvider.dir(context);
        if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
            throw new IOException("Не удалось создать папку экспорта");
        }
        long free = outputDir.getUsableSpace();
        if (free < MAX + EXPECTED) {
            throw new IOException("Недостаточно свободного места для экспорта");
        }
        String name = "atlasdim-cityray-resource-" + System.currentTimeMillis() + ".kzb";
        File shared = new File(SHARE, name);
        File part = new File(outputDir, name + ".part");
        File dest = new File(outputDir, name);
        if (shared.exists() || part.exists() || dest.exists()) {
            throw new IOException("Имя экспортируемого файла занято");
        }
        boolean copied = false;
        try {
            progress.update("Подключаюсь к QNX…");
            try (QnxShell qnx = QnxShell.open(host, "root", password)) {
                QnxShell.Result check = qnx.exec(
                        "test -r " + SOURCE + " && test -d /shared && "
                        + "test -w /shared && wc -c < " + SOURCE, 15000);
                if (!check.ok() || !check.output.matches("(?s).*\\b" + EXPECTED + "\\b.*")) {
                    throw new IOException("Источник не найден, размер отличается или /shared недоступна");
                }
                progress.update("Копирую ресурс в общую папку QNX/Android…");
                // Fixed source and generated basename: no user-controlled shell arguments.
                QnxShell.Result result = qnx.exec(
                        "cp " + SOURCE + " /shared/" + name, TIMEOUT);
                if (!result.ok()) {
                    throw new IOException("QNX не смогла скопировать ресурс в /shared");
                }
            }
            if (!shared.isFile() || shared.length() != EXPECTED) {
                throw new IOException("Копия на NFS-шаре отсутствует или имеет неверный размер");
            }
            progress.update("Переношу копию в Android и проверяю SHA-256…");
            MessageDigest fromShare = sha256();
            MessageDigest fromCopy = sha256();
            try (FileInputStream in = new FileInputStream(shared);
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    fromShare.update(buf, 0, n);
                }
                out.getFD().sync();
            }
            if (part.length() != EXPECTED) {
                throw new IOException("Размер локальной копии отличается");
            }
            try (FileInputStream in = new FileInputStream(part)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) fromCopy.update(buf, 0, n);
            }
            if (!MessageDigest.isEqual(fromShare.digest(), fromCopy.digest())) {
                throw new IOException("Контрольная сумма копии не совпала");
            }
            if (!part.renameTo(dest)) {
                throw new IOException("Не удалось завершить сохранение файла");
            }
            copied = true;
            return dest;
        } finally {
            if (!copied) part.delete();
            // Delete only the generated export copy, never the QNX source.
            // If Android cannot delete it, leave it untouched for manual cleanup.
            if (shared.isFile()) shared.delete();
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
