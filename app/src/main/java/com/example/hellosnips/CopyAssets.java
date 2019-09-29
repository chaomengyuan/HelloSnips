package com.example.hellosnips;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CopyAssets {
    public static void copyAssets(Context context) {
        File folder = new File(Environment.getExternalStorageDirectory() +
                File.separator + "helloSnips");
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }

        AssetManager assetManager = context.getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        if (files != null) {
            for (String filename : files) {
                if (filename.contains(".conf")){
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        in = assetManager.open(filename);
                        out = new FileOutputStream(Environment.getExternalStorageDirectory() + "/helloSnips/" + filename);
                        copyFile(in, out);
                    } catch (IOException e) {
                        Log.e("tag", "Failed to copy asset file: " + filename, e);
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                                in = null;
                            } catch (IOException e) {

                            }
                        }
                        if (out != null) {
                            try {
                                out.flush();
                                out.close();
                                out = null;
                            } catch (IOException e) {

                            }
                        }
                    }
                }
            }
        }
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }}