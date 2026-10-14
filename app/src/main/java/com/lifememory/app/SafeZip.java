package com.lifememory.app;

import java.io.*;
import java.util.*;
import java.util.zip.*;

final class SafeZip {
  static void extract(InputStream input, File root, long limit) throws IOException {
    if (!root.isDirectory() && !root.mkdirs())
      throw new IOException("Cannot create import directory");
    File archive = new File(root, ".import.zip");
    String prefix = root.getCanonicalPath() + File.separator;
    byte[] b = new byte[32768];
    long compressed = 0, total = 0;
    int count = 0;
    Set<String> names = new HashSet<>();
    try {
      try (InputStream in = input;
          OutputStream out = new FileOutputStream(archive)) {
        int n;
        while ((n = in.read(b)) != -1) {
          compressed += n;
          if (compressed > 512L * 1024 * 1024)
            throw new IOException("Compressed ZIP exceeds 512 MB limit");
          if (root.getUsableSpace() < 32L * 1024 * 1024)
            throw new IOException("Not enough free storage");
          out.write(b, 0, n);
        }
      }
      // ZipFile avoids large-entry ZipInputStream inflater failures on older Android runtimes.
      try (ZipFile z = new ZipFile(archive)) {
        Enumeration<? extends ZipEntry> entries = z.entries();
        while (entries.hasMoreElements()) {
          ZipEntry e = entries.nextElement();
          String name = e.getName();
          File f = new File(root, name);
          if (++count > 4096
              || name.contains("\\")
              || name.contains(":")
              || !f.getCanonicalPath().startsWith(prefix)
              || f.getCanonicalPath().equals(archive.getCanonicalPath())
              || !names.add(f.getCanonicalPath()))
            throw new IOException("Invalid or duplicate ZIP path");
          if (e.isDirectory()) {
            if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Cannot create directory");
            continue;
          }
          if (!f.getParentFile().isDirectory() && !f.getParentFile().mkdirs())
            throw new IOException("Cannot create directory");
          CRC32 crc = new CRC32();
          try (InputStream in = z.getInputStream(e);
              OutputStream out = new FileOutputStream(f)) {
            int n;
            while ((n = in.read(b)) != -1) {
              total += n;
              if (total > limit) throw new IOException("ZIP exceeds import size limit");
              if (root.getUsableSpace() < 32L * 1024 * 1024)
                throw new IOException("Not enough free storage");
              crc.update(b, 0, n);
              out.write(b, 0, n);
            }
          }
          if (e.getCrc() != -1 && e.getCrc() != crc.getValue())
            throw new IOException("ZIP attachment checksum mismatch");
        }
      }
    } finally {
      archive.delete();
    }
  }

  static void remove(File f) {
    File[] a = f.listFiles();
    if (a != null) for (File child : a) remove(child);
    f.delete();
  }
}
