package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogCloudLiveBinding;
import com.fongmi.android.tv.databinding.ItemCloudSourceBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudLiveDialog extends BaseAlertDialog {

    private static final String[] MIRRORS = {
            "https://ghproxy.net/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/live-sources.json",
            "https://ghfast.top/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/live-sources.json",
            "https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/live-sources.json"
    };

    private DialogCloudLiveBinding binding;
    private LiveAdapter adapter;
    private final List<LiveItem> items = new ArrayList<>();
    private ExecutorService pool;

    public static CloudLiveDialog create() {
        return new CloudLiveDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogCloudLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle("云端直播源库（当前: " + LiveConfig.getUrl() + "）").setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        items.clear();
        adapter = new LiveAdapter();
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter);
        start();
    }

    private void start() {
        pool = Executors.newFixedThreadPool(4);
        new Thread(() -> {
            String json = fetchList();
            if (TextUtils.isEmpty(json)) {
                App.post(() -> { Notify.show("直播源清单拉取失败"); dismiss(); });
                return;
            }
            parse(json);
            App.post(adapter::notifyDataSetChanged);
            for (int i = 0; i < items.size(); i++) check(items.get(i));
        }).start();
    }

    private String fetchList() {
        for (String mirror : MIRRORS) {
            try {
                String text = OkHttp.string(mirror);
                if (!TextUtils.isEmpty(text) && text.trim().startsWith("{")) return text;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private void parse(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json.trim()).getAsJsonObject();
            if (!obj.has("sources")) return;
            JsonArray arr = obj.getAsJsonArray("sources");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject s = arr.get(i).getAsJsonObject();
                LiveItem item = new LiveItem();
                item.name = s.has("name") ? s.get("name").getAsString() : "";
                item.desc = s.has("desc") ? s.get("desc").getAsString() : "";
                item.url = s.has("url") ? s.get("url").getAsString() : "";
                item.status = "检测中…";
                items.add(item);
            }
        } catch (Throwable ignored) {}
    }

    private void check(LiveItem item) {
        pool.execute(() -> {
            item.status = checkLive(item.url);
            App.post(adapter::notifyDataSetChanged);
        });
    }

    private String checkLive(String url) {
        try {
            String text = OkHttp.string(url);
            if (TextUtils.isEmpty(text)) return "❌ 失效";
            int channels = 0;
            if (text.contains("#EXTINF")) channels = text.split("#EXTINF").length - 1;
            else if (text.contains("\"lives\"")) channels = 1;
            else if (text.length() > 100) channels = 1;
            return channels > 0 ? "✅ 有效（约" + channels + "频道）" : "❌ 无频道数据";
        } catch (Throwable e) {
            return "❌ 无法访问";
        }
    }

    private void onItemClick(LiveItem item) {
        if (TextUtils.isEmpty(item.url)) return;
        Config config = Config.find(item.url, item.name, 1);
        LiveConfig.load(config, new Callback() {
            @Override public void start() { Notify.progress(getActivity()); }
            @Override public void success() { Notify.dismiss(); Notify.show("直播源已切换:" + item.name); dismiss(); }
            @Override public void error(String msg) { Notify.dismiss(); Notify.show("切换失败:" + msg); }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.42f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pool != null) pool.shutdownNow();
    }

    private class LiveAdapter extends RecyclerView.Adapter<LiveAdapter.ViewHolder> {
        @Override public int getItemCount() { return items.size(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) { return new ViewHolder(ItemCloudSourceBinding.inflate(LayoutInflater.from(p.getContext()), p, false)); }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
            LiveItem item = items.get(pos);
            h.binding.name.setText(item.name + (item.url.equals(LiveConfig.getUrl()) ? "（当前）" : ""));
            h.binding.desc.setText(item.desc + "  " + item.status);
            h.binding.getRoot().setOnClickListener(v -> onItemClick(item));
        }
        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemCloudSourceBinding binding;
            ViewHolder(ItemCloudSourceBinding b) { super(b.getRoot()); this.binding = b; }
        }
    }

    private static class LiveItem {
        String name, desc, url, status;
    }
}