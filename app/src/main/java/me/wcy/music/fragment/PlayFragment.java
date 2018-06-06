package me.wcy.music.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import me.wcy.lrcview.LrcView;
import me.wcy.music.R;
import me.wcy.music.adapter.PlayPagerAdapter;
import me.wcy.music.application.MusicApplication;
import me.wcy.music.constants.Actions;
import me.wcy.music.enums.PlayModeEnum;
import me.wcy.music.executor.SearchLrc;
import me.wcy.music.model.Music;
import me.wcy.music.service.AudioPlayer;
import me.wcy.music.service.OnPlayerEventListener;
import me.wcy.music.storage.preference.Preferences;
import me.wcy.music.utils.CoverLoader;
import me.wcy.music.utils.FileUtils;
import me.wcy.music.utils.Locus;
import me.wcy.music.utils.ScreenUtils;
import me.wcy.music.utils.SystemUtils;
import me.wcy.music.utils.ToastUtils;
import me.wcy.music.utils.binding.Bind;
import me.wcy.music.widget.AlbumCoverView;
import me.wcy.music.widget.IndicatorLayout;

import static me.wcy.music.utils.Locus.FindTargets;
import static me.wcy.music.utils.Locus.FramePreHandle;
import static org.opencv.core.CvType.CV_8UC3;

/**
 * 正在播放界面
 * Created by wcy on 2015/11/27.
 */
public class PlayFragment extends BaseFragment implements View.OnClickListener,
        ViewPager.OnPageChangeListener, SeekBar.OnSeekBarChangeListener, OnPlayerEventListener,CameraBridgeViewBase.CvCameraViewListener2,
        LrcView.OnPlayClickListener {
    @Bind(R.id.ll_content)
    private LinearLayout llContent;
    @Bind(R.id.iv_play_page_bg)
    private ImageView ivPlayingBg;
    @Bind(R.id.iv_back)
    private ImageView ivBack;
    @Bind(R.id.tv_title)
    private TextView tvTitle;
    @Bind(R.id.tv_artist)
    private TextView tvArtist;
    @Bind(R.id.vp_play_page)
    private ViewPager vpPlay;
    @Bind(R.id.il_indicator)
    private IndicatorLayout ilIndicator;
    @Bind(R.id.sb_progress)
    private SeekBar sbProgress;
    @Bind(R.id.tv_current_time)
    private TextView tvCurrentTime;
    @Bind(R.id.tv_total_time)
    private TextView tvTotalTime;
    @Bind(R.id.iv_mode)
    private ImageView ivMode;
    @Bind(R.id.iv_play)
    private ImageView ivPlay;
    @Bind(R.id.iv_next)
    private ImageView ivNext;
    @Bind(R.id.iv_prev)
    private ImageView ivPrev;
    private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(MusicApplication.getContext()) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    //DO YOUR WORK/STUFF HERE
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    //@Bind(R.id.carmra_surface)
    private CameraBridgeViewBase mCameraBridgeViewBase;
    private AlbumCoverView mAlbumCoverView;
    private LrcView mLrcViewSingle;
    private LrcView mLrcViewFull;
    private SeekBar sbVolume;
    private int count;

    private AudioManager mAudioManager;
    private List<View> mViewPagerContent;
    private int mLastProgress;
    private boolean isDraggingProgress;
    private File mCascadeFile;
    private CascadeClassifier lbpCascade;
    Mat ImgSkin;
    Locus locusAnalyser=new Locus();



    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_play, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initSystemBar();
        initViewPager();
        ilIndicator.create(mViewPagerContent.size());
        initPlayMode();
        onChangeImpl(AudioPlayer.get().getPlayMusic());
        AudioPlayer.get().addOnPlayEventListener(this);
        loaddetectmodel();
    }

    @Override
    public void onResume() {
        super.onResume();
        mOpenCVCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        IntentFilter filter = new IntentFilter(Actions.VOLUME_CHANGED_ACTION);
        getContext().registerReceiver(mVolumeReceiver, filter);
    }

    static{
        if(!OpenCVLoader.initDebug()){
            //handle initialization error
        }
    }

    @Override
    protected void setListener() {
        ivBack.setOnClickListener(this);
        ivMode.setOnClickListener(this);
        ivPlay.setOnClickListener(this);
        ivPrev.setOnClickListener(this);
        ivNext.setOnClickListener(this);
        sbProgress.setOnSeekBarChangeListener(this);
        sbVolume.setOnSeekBarChangeListener(this);
        vpPlay.addOnPageChangeListener(this);
    }

    /**
     * 沉浸式状态栏
     */
    private void initSystemBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int top = ScreenUtils.getStatusBarHeight();
            llContent.setPadding(0, top, 0, 0);
        }
    }

    private void initViewPager() {
        View coverView = LayoutInflater.from(getContext()).inflate(R.layout.fragment_play_page_cover, null);
        View lrcView = LayoutInflater.from(getContext()).inflate(R.layout.fragment_play_page_lrc, null);
        View newContralView=LayoutInflater.from(getContext()).inflate(R.layout.fragment_play_page_newcontral,null);
        mAlbumCoverView = coverView.findViewById(R.id.album_cover_view);
        mLrcViewSingle = coverView.findViewById(R.id.lrc_view_single);
        mLrcViewFull = lrcView.findViewById(R.id.lrc_view_full);
        sbVolume = lrcView.findViewById(R.id.sb_volume);
        mAlbumCoverView.initNeedle(AudioPlayer.get().isPlaying());
        mLrcViewFull.setOnPlayClickListener(this);
        //cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        mCameraBridgeViewBase=newContralView.findViewById(R.id.carmra_surface);
        mCameraBridgeViewBase.enableView();
        mCameraBridgeViewBase.setCvCameraViewListener(this);
        initVolume();

        mViewPagerContent = new ArrayList<>(3);
        mViewPagerContent.add(coverView);
        mViewPagerContent.add(lrcView);
        mViewPagerContent.add(newContralView);
        vpPlay.setAdapter(new PlayPagerAdapter(mViewPagerContent));
    }

    private void initVolume() {
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        sbVolume.setMax(mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        sbVolume.setProgress(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    private void initPlayMode() {
        int mode = Preferences.getPlayMode();
        ivMode.setImageLevel(mode);
    }

    @Override
    public void onChange(Music music) {
        onChangeImpl(music);
    }

    @Override
    public void onPlayerStart() {
        ivPlay.setSelected(true);
        mAlbumCoverView.start();
    }

    @Override
    public void onPlayerPause() {
        ivPlay.setSelected(false);
        mAlbumCoverView.pause();
    }

    /**
     * 更新播放进度
     */
    @Override
    public void onPublish(int progress) {
        if (!isDraggingProgress) {
            sbProgress.setProgress(progress);
        }

        if (mLrcViewSingle.hasLrc()) {
            mLrcViewSingle.updateTime(progress);
            mLrcViewFull.updateTime(progress);
        }
    }

    @Override
    public void onBufferingUpdate(int percent) {
        sbProgress.setSecondaryProgress(sbProgress.getMax() * 100 / percent);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_back:
                onBackPressed();
                break;
            case R.id.iv_mode:
                switchPlayMode();
                break;
            case R.id.iv_play:
                play();
                break;
            case R.id.iv_next:
                next();
                break;
            case R.id.iv_prev:
                prev();
                break;
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        ilIndicator.setCurrent(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == sbProgress) {
            if (Math.abs(progress - mLastProgress) >= DateUtils.SECOND_IN_MILLIS) {
                tvCurrentTime.setText(formatTime(progress));
                mLastProgress = progress;
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        if (seekBar == sbProgress) {
            isDraggingProgress = true;
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar == sbProgress) {
            isDraggingProgress = false;
            if (AudioPlayer.get().isPlaying() || AudioPlayer.get().isPausing()) {
                int progress = seekBar.getProgress();
                AudioPlayer.get().seekTo(progress);

                if (mLrcViewSingle.hasLrc()) {
                    mLrcViewSingle.updateTime(progress);
                    mLrcViewFull.updateTime(progress);
                }
            } else {
                seekBar.setProgress(0);
            }
        } else if (seekBar == sbVolume) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, seekBar.getProgress(),
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        }
    }

    @Override
    public boolean onPlayClick(long time) {
        if (AudioPlayer.get().isPlaying() || AudioPlayer.get().isPausing()) {
            AudioPlayer.get().seekTo((int) time);
            if (AudioPlayer.get().isPausing()) {
                AudioPlayer.get().playPause();
            }
            return true;
        }
        return false;
    }

    private void onChangeImpl(Music music) {
        if (music == null) {
            return;
        }

        tvTitle.setText(music.getTitle());
        tvArtist.setText(music.getArtist());
        sbProgress.setProgress((int) AudioPlayer.get().getAudioPosition());
        sbProgress.setSecondaryProgress(0);
        sbProgress.setMax((int) music.getDuration());
        mLastProgress = 0;
        tvCurrentTime.setText(R.string.play_time_start);
        tvTotalTime.setText(formatTime(music.getDuration()));
        setCoverAndBg(music);
        setLrc(music);
        if (AudioPlayer.get().isPlaying() || AudioPlayer.get().isPreparing()) {
            ivPlay.setSelected(true);
            mAlbumCoverView.start();
        } else {
            ivPlay.setSelected(false);
            mAlbumCoverView.pause();
        }
    }

    private void play() {
        AudioPlayer.get().playPause();
    }

    private void pause(){
        AudioPlayer.get().pausePlayer();
    }

    private void next() {
        AudioPlayer.get().next();
    }

    private void prev() {
        AudioPlayer.get().prev();
    }

    private void switchPlayMode() {
        PlayModeEnum mode = PlayModeEnum.valueOf(Preferences.getPlayMode());
        switch (mode) {
            case LOOP:
                mode = PlayModeEnum.SHUFFLE;
                ToastUtils.show(R.string.mode_shuffle);
                break;
            case SHUFFLE:
                mode = PlayModeEnum.SINGLE;
                ToastUtils.show(R.string.mode_one);
                break;
            case SINGLE:
                mode = PlayModeEnum.LOOP;
                ToastUtils.show(R.string.mode_loop);
                break;
        }
        Preferences.savePlayMode(mode.value());
        initPlayMode();
    }

    private void onBackPressed() {
        getActivity().onBackPressed();
        ivBack.setEnabled(false);
        handler.postDelayed(() -> ivBack.setEnabled(true), 300);
    }

    private void setCoverAndBg(Music music) {
        mAlbumCoverView.setCoverBitmap(CoverLoader.getInstance().loadRound(music));
        ivPlayingBg.setImageBitmap(CoverLoader.getInstance().loadBlur(music));
    }

    private void setLrc(final Music music) {
        if (music.getType() == Music.Type.LOCAL) {
            String lrcPath = FileUtils.getLrcFilePath(music);
            if (!TextUtils.isEmpty(lrcPath)) {
                loadLrc(lrcPath);
            } else {
                new SearchLrc(music.getArtist(), music.getTitle()) {
                    @Override
                    public void onPrepare() {
                        // 设置tag防止歌词下载完成后已切换歌曲
                        vpPlay.setTag(music);

                        loadLrc("");
                        setLrcLabel("正在搜索歌词");
                    }

                    @Override
                    public void onExecuteSuccess(@NonNull String lrcPath) {
                        if (vpPlay.getTag() != music) {
                            return;
                        }

                        // 清除tag
                        vpPlay.setTag(null);

                        loadLrc(lrcPath);
                        setLrcLabel("暂无歌词");
                    }

                    @Override
                    public void onExecuteFail(Exception e) {
                        if (vpPlay.getTag() != music) {
                            return;
                        }

                        // 清除tag
                        vpPlay.setTag(null);
                        setLrcLabel("暂无歌词");
                    }
                }.execute();
            }
        } else {
            String lrcPath = FileUtils.getLrcDir() + FileUtils.getLrcFileName(music.getArtist(), music.getTitle());
            loadLrc(lrcPath);
        }
    }

    private void loadLrc(String path) {
        File file = new File(path);
        mLrcViewSingle.loadLrc(file);
        mLrcViewFull.loadLrc(file);

    }

    private void setLrcLabel(String label) {
        mLrcViewSingle.setLabel(label);
        mLrcViewFull.setLabel(label);
    }

    private String formatTime(long time) {
        return SystemUtils.formatTime("mm:ss", time);
    }

    private BroadcastReceiver mVolumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            sbVolume.setProgress(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        }
    };

    @Override
    public void onDestroy() {
        getContext().unregisterReceiver(mVolumeReceiver);
        AudioPlayer.get().removeOnPlayEventListener(this);
        super.onDestroy();
        disableCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        disableCamera();

    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }
    Locus.Gesture g;


    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat matSrc;
        matSrc=inputFrame.rgba().clone();

//并行处理异步线程1
        StaticDetectTask task1=new StaticDetectTask();
        task1.execute(matSrc);
        Size winSize=matSrc.size();

        locusAnalyser.setWinSize(winSize);
        Mat cb,  ImgFrameSmall, ImgSkin_s;
        //FramePreHandle
        ImgFrameSmall=new Mat();
        cb=new Mat();
        ImgSkin=Mat.zeros(matSrc.size(),CV_8UC3);
        List<Mat> mats=new ArrayList<Mat>();
        mats.add(cb);mats.add(ImgFrameSmall);
        FramePreHandle(matSrc,0,122,mats,matSrc.size());
        Rect target_rect=new Rect();
        List<Mat> targets=new ArrayList<Mat>();
        cb=mats.get(2);
        targets.add(cb);
        List<Rect> rects=new ArrayList<Rect>();
        FindTargets(targets, 1000, rects);
        target_rect=rects.get(0);
        ImgSkin_s=targets.get(1);
        ImgSkin=inputFrame.rgba();
        if (target_rect.width > 0){
            Imgproc.rectangle(ImgFrameSmall, target_rect.tl(),target_rect.br(), new Scalar(255, 0, 0), 3);
            Point center=new Point(target_rect.x + target_rect.width / 2, target_rect.y + target_rect.height / 2);

            //Core.add(ImgSkin,ImgSkin_s,ImgSkin);



            //draw cross at target center
            Imgproc.line(ImgSkin, new Point(center.x - 5, center.y - 5), new Point(center.x + 5, center.y + 5),new  Scalar(0, 0, 255), 3);
            Imgproc.line(ImgSkin, new Point(center.x + 5, center.y - 5), new Point(center.x - 5, center.y + 5), new Scalar(0, 0, 255), 3);

            // locus
            locusAnalyser.addPoint(center, winSize);
            g = locusAnalyser.analyseLocus();
            new MyAsyncTask().execute();

            // post gesture
            //postCommand(g);
        }
        else{
            locusAnalyser.reset();
        }
        return ImgSkin;
        //return inputFrame.rgba();
    }
    class MyAsyncTask extends AsyncTask<Void, Void, Void>
    {
        @Override
        protected Void doInBackground(Void... params) {

//            try {
//                Thread.sleep(200);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            // TODO Auto-generated method stub
            //tv.setText("异步更新TextView内容");
            postCommand(g);
            g= Locus.Gesture.NOOPERATION;
        }
    }
    /**
     * 静态手势处理线程
     */
    class StaticDetectTask extends AsyncTask<Mat,Void,Boolean>{
        @Override
        protected Boolean doInBackground(Mat... mats) {
            count++;
            Mat mRGB=mats[0];
            Mat mGray=new Mat();
            Imgproc.cvtColor(mRGB,mGray,Imgproc.COLOR_RGB2GRAY);
            //对得到的帧图像进行旋转，否则无法识别
            Point center=new Point(mGray.cols()/2,mGray.rows()/2);
            Mat rot_mat=Imgproc.getRotationMatrix2D(center,270,1.0);
            Imgproc.warpAffine(mGray,mGray,rot_mat,mGray.size());
            //准本识别
            MatOfRect faces = new MatOfRect();
            if(lbpCascade != null&&count==24)
            {
                lbpCascade.detectMultiScale(mGray, faces, 1.1, 2, 2, new Size(200,200), new Size());
                count=0;
            }
            ImgSkin=mGray;

            Rect[] facesArray = faces.toArray();
            if(facesArray.length==0)
                return false;
            else{
                //Imgproc.rectangle(ImgSkin, facesArray[0].tl(), facesArray[0].br(), new Scalar(100), 3);

                return true;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if(result){
                //tv.setText("暂停in静态方法");
                play();
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
            }
            else{
                //tv.setText("nothing");
            }
        }
    }
    public void postCommand(Locus.Gesture g){
        switch (g){
            case NEXT:
                Log.i("指令：","下一首");
                next();
                //Toast.makeText(this,"下一首",Toast.LENGTH_SHORT);
                break;
            case PREVIOUS:
                Log.i("指令：","上一首");
               prev();
                //Toast.makeText(this,"上一首",Toast.LENGTH_SHORT);
                break;
            case PAUSEPLAY:
                Log.i("指令","播放/暂停");
                play();
                //Toast.makeText(this,"播放/暂停",Toast.LENGTH_SHORT);
                break;
            case NOOPERATION:
                //Log.i("指令：","nothing");
            default:
                return;
        }
    }
    public void disableCamera() {
        if (mCameraBridgeViewBase != null)
            mCameraBridgeViewBase.disableView();
    }
    private void loaddetectmodel() {
        try {
            InputStream is =
                    getResources().openRawResource(R.raw.fist);
            File cascadeDir = MusicApplication.getContext().getDir("cascade", Context.MODE_PRIVATE);
            mCascadeFile = new File(cascadeDir, "fist.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            lbpCascade = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            if (lbpCascade.empty()) {
                Log.i("Cascade Error:", "级联分类器加载失败");
                lbpCascade = null;
            }

        }catch (Exception e){
            Log.i("Cascade Error: ","Cascase not found");
        }
    }

}
