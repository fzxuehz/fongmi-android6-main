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
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogCloudSourceBinding;
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

public class CloudSourceDialog extends BaseAlertDialog {

    private static final String[] MIRRORS = {
            "https://ghproxy.net/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/sources.json",
            "https://ghfast.top/https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/sources.json",
            "https://raw.githubusercontent.com/fzxuehz/fongmi-android6-main/main/sources.json"
    };

    private DialogCloudSourceBinding binding;
    private SourceAdapter adapter;
    private final List<Item> items = new ArrayList<>();
    private ExecutorService pool;

    public static CloudSourceDialog create() {
        return new CloudSourceDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogCloudSourceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle("云端源库（当前: " + VodConfig.getDesc() + "）").setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        items.clear();
        adapter = new SourceAdapter();
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
                App.post(() -> {
                    Notify.show("云端清单拉取失败，请稍后再试");
                    dismiss();
                });
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
            } catch (Throwable ignored) {
            }
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
                Item item = new Item();
                item.name = s.has("name") ? s.get("name").getAsString() : "";
                item.desc = s.has("desc") ? s.get("desc").getAsString() : "";
                item.zone = s.has("zone") ? s.get("zone").getAsString() : "";
                item.url = s.has("url") ? s.get("url").getAsString() : "";
                item.status = "检测中…";
                items.add(item);
            }
        } catch (Throwable ignored) {
        }
    }

    private void check(Item item) {
        pool.execute(() -> {
            String result = checkOne(item.url);
            item.status = result;
            App.post(adapter::notifyDataSetChanged);
        });
    }

    private String checkOne(String url) {
        try {
            String cfg = OkHttp.string(url);
            if (TextUtils.isEmpty(cfg) || !(cfg.contains("\"sites\"") || cfg.contains("spider"))) return "❌ 失效";
            List<String> apis = extractApis(cfg, 4);
            if (apis.isEmpty()) return "🟡 配置可读（爬虫站点）";
            int ok = 0;
            for (String api : apis) {
                try {
                    String u = api + (api.contains("?") ? "&" : "?") + "ac=videolist&wd=" + java.net.URLEncoder.encode("战", "UTF-8");
                    String resp = OkHttp.string(u);
                    if (!TextUtils.isEmpty(resp) && (resp.contains("vod") || resp.contains("video") || resp.contains("list"))) ok++;
                } catch (Throwable ignored) {
                }
            }
            return ok > 0 ? "✅ 可播放(" + ok + "/" + apis.size() + "站)" : "🟡 站点探测未过（配置可读）";
        } catch (Throwable e) {
            return "❌ 无法访问";
        }
    }

    private List<String> extractApis(String cfg, int max) {
        List<String> apis = new ArrayList<>();
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^{}]*?\"api\"\\s*:\\s*\"(https?://[^\"]+)\"[^{}]*?\\}").matcher(cfg);
            while (m.find() && apis.size() < max) {
                if (m.group().contains("csp_")) continue;
                apis.add(m.group(1));
            }
        } catch (Throwable ignored) {
        }
        return apis;
    }

    private void onItemClick(Item item) {
        if (TextUtils.isEmpty(item.url)) return;
        Config config = Config.find(item.url, item.name, 0);
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                Notify.show("已切换:" + item.name);
                dismiss();
            }

            @Override
            public void error(String msg) {
                Notify.show("加载失败:" + msg);
            }
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

    private class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.ViewHolder> {

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemCloudSourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Item item = items.get(position);
            holder.binding.name.setText(item.name + (item.url.equals(VodConfig.getUrl()) ? "（当前）" : ""));
            holder.binding.desc.setText(item.desc + "  " + item.status);
            holder.binding.getRoot().setOnClickListener(v -> onItemClick(item));
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            private final ItemCloudSourceBinding binding;

            ViewHolder(ItemCloudSourceBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static class Item {
        String name;
        String desc;
        String zone;
        String url;
        String status;
    }
}