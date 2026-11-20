package com.fongmi.android.tv.api;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RemotePush {

    private static final String[] MIRRORS = {
            "https://ghproxy.net/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/push-queue.json",
            "https://ghp.ci/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/push-queue.json",
            "https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/push-queue.json"
    };

    public static void check() {
        App.post(() -> new Thread(RemotePush::doCheck).start(), 6000);
    }

    private static void doCheck() {
        try {
            for (String mirror : MIRRORS) {
                try {
                    String resp = OkHttp.string(mirror);
                    if (TextUtils.isEmpty(resp)) continue;
                    String trimmed = resp.trim();
                    if (!trimmed.startsWith("{")) continue;
                    JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
                    if (!obj.has("time") || !obj.has("url")) continue;
                    long time = obj.get("time").getAsLong();
                    String url = obj.get("url").getAsString();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "";
                    if (time <= 0 || TextUtils.isEmpty(url)) continue;
                    if (time <= getLast()) return;
                    if (url.equals(VodConfig.getUrl())) {
                        putLast(time);
                        return;
                    }
                    apply(time, url, name);
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void apply(long time, String url, String name) {
        App.post(() -> VodConfig.load(Config.find(url, TextUtils.isEmpty(name) ? url : name, 0), new Callback() {
            @Override
            public void success() {
                putLast(time);
                Notify.show("远程推送已更新:" + name);
            }

            @Override
            public void error(String msg) {
                Notify.show("远程推送失败:" + msg);
            }
        }));
    }

    private static long getLast() {
        try {
            return Long.parseLong(Prefers.getString("remote_push_time", "0"));
        } catch (Throwable e) {
            return 0;
        }
    }

    private static void putLast(long time) {
        Prefers.put("remote_push_time", String.valueOf(time));
    }
}