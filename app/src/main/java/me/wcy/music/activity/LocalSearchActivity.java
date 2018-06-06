package me.wcy.music.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import me.wcy.music.R;
import me.wcy.music.adapter.OnMoreClickListener;
import me.wcy.music.adapter.PlaylistAdapter;
import me.wcy.music.application.AppCache;
import me.wcy.music.enums.LoadStateEnum;
import me.wcy.music.executor.PlayMusic;
import me.wcy.music.executor.PlaySearchedMusic;
import me.wcy.music.model.Music;
import me.wcy.music.service.AudioPlayer;
import me.wcy.music.utils.MusicUtils;
import me.wcy.music.utils.PermissionReq;
import me.wcy.music.utils.ToastUtils;
import me.wcy.music.utils.ViewUtils;
import me.wcy.music.utils.binding.Bind;

public class LocalSearchActivity extends BaseActivity implements SearchView.OnQueryTextListener
        , AdapterView.OnItemClickListener, OnMoreClickListener {

    @Bind(R.id.lv_search_local_list)
    private ListView lvSearchLocalMusic;
    @Bind(R.id.ll_loading_local)
    private LinearLayout llLoading;
    @Bind(R.id.ll_load_fail_local)
    private LinearLayout llLoadFail;
    private List<Music> localsearchlist=new ArrayList<>();
    private PlaylistAdapter mAdapter=new PlaylistAdapter(localsearchlist);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_search);
    }
    @Override
    protected void onServiceBound() {
        lvSearchLocalMusic.setAdapter(mAdapter);
        TextView tvLoadFail = llLoadFail.findViewById(R.id.tv_load_fail_text);
        tvLoadFail.setText(R.string.search_empty);

        lvSearchLocalMusic.setOnItemClickListener(this);
        mAdapter.setOnMoreClickListener(this);
    }
    @Override
    protected int getDarkTheme() {
        return R.style.AppThemeDark_Search;
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search_music, menu);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.onActionViewExpanded();
        searchView.setQueryHint(getString(R.string.search_tips));
        searchView.setOnQueryTextListener(this);
        searchView.setSubmitButtonEnabled(true);
        try {
            Field field = searchView.getClass().getDeclaredField("mGoButton");
            field.setAccessible(true);
            ImageView mGoButton = (ImageView) field.get(searchView);
            mGoButton.setImageResource(R.drawable.ic_menu_search);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onQueryTextSubmit(String query) {
        //ViewUtils.changeViewState(lvSearchLocalMusic, llLoading, llLoadFail, LoadStateEnum.LOADING);
        searchMusic(query);
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
       Music music=localsearchlist.get(i);
        AudioPlayer.get().addAndPlay(music);
        ToastUtils.show("已添加到播放列表");

    }

    @Override
    public void onMoreClick(int position) {

    }
    private void searchMusic(final String keyword) {
        PermissionReq.with(this)
                .permissions(Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .result(new PermissionReq.Result() {
                    @SuppressLint("StaticFieldLeak")
                    @Override
                    public void onGranted() {
                        new AsyncTask<Void, Void, List<Music>>() {
                            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
                            @Override
                            protected List<Music> doInBackground(Void... params) {
                                return MusicUtils.searchLocalMusic(getBaseContext(),keyword);
                            }

                            @Override
                            protected void onPostExecute(List<Music> musicList) {
                                localsearchlist.clear();
                                localsearchlist.addAll(musicList);
                                lvSearchLocalMusic.setVisibility(View.VISIBLE);
                                mAdapter.notifyDataSetChanged();
                            }
                        }.execute();
                    }

                    @Override
                    public void onDenied() {

                    }
                })
                .request();
    }
}
