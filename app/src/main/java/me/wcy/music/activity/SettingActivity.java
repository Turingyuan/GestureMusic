package me.wcy.music.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.hwangjr.rxbus.RxBus;
import com.leon.lfilepickerlibrary.LFilePicker;
import com.leon.lfilepickerlibrary.utils.Constant;

import java.util.List;

import me.wcy.music.R;
import me.wcy.music.constants.RxBusTags;
import me.wcy.music.service.AudioPlayer;
import me.wcy.music.utils.FileUtils;
import me.wcy.music.utils.MusicUtils;
import me.wcy.music.storage.preference.Preferences;
import me.wcy.music.utils.ToastUtils;

public class SettingActivity extends BaseActivity {



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
    }

    @Override
    protected void onServiceBound() {
        SettingFragment settingFragment = new SettingFragment();
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.ll_fragment_container, settingFragment)
                .commit();
    }

    public static class SettingFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
        private Preference mSoundEffect;
        private Preference mFilterSize;
        private Preference mFilterTime;
        private Preference mDownloadPath;
        private Preference getmDownloadPathSwitch;
        int REQUESTCODE_FROM_FRAGMENT=1001;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preference_setting);

            mSoundEffect = findPreference(getString(R.string.setting_key_sound_effect));
            mFilterSize = findPreference(getString(R.string.setting_key_filter_size));
            mFilterTime = findPreference(getString(R.string.setting_key_filter_time));
            mDownloadPath=findPreference("file_path");
            getmDownloadPathSwitch=findPreference("file_switch");
            mSoundEffect.setOnPreferenceClickListener(this);
            mFilterSize.setOnPreferenceChangeListener(this);
            mFilterTime.setOnPreferenceChangeListener(this);
            mDownloadPath.setOnPreferenceClickListener(this);
            getmDownloadPathSwitch.setOnPreferenceChangeListener(this);

            mFilterSize.setSummary(getSummary(Preferences.getFilterSize(), R.array.filter_size_entries, R.array.filter_size_entry_values));
            mFilterTime.setSummary(getSummary(Preferences.getFilterTime(), R.array.filter_time_entries, R.array.filter_time_entry_values));
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == mSoundEffect) {
                startEqualizer();
                return true;
            }
            if(preference==mDownloadPath){
                new LFilePicker().withFragment(this)
                        .withRequestCode(REQUESTCODE_FROM_FRAGMENT)
                        .withTitle("文件选择")
                        //.withStartPath("/storage/emulated/0")//指定初始显示路径
                        //.withFileFilter(new String[]{"txt", "png", "docx"})
                        .start();
                return true;
            }
            return false;
        }


        private void startEqualizer() {
            if (MusicUtils.isAudioControlPanelAvailable(getActivity())) {
                Intent intent = new Intent();
                String packageName = getActivity().getPackageName();
                intent.setAction(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName);
                intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioPlayer.get().getAudioSessionId());

                try {
                    startActivityForResult(intent, 1);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                    ToastUtils.show(R.string.device_not_support);
                }
            } else {
                ToastUtils.show(R.string.device_not_support);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == mFilterSize) {
                Preferences.saveFilterSize((String) newValue);
                mFilterSize.setSummary(getSummary(Preferences.getFilterSize(), R.array.filter_size_entries, R.array.filter_size_entry_values));
                RxBus.get().post(RxBusTags.SCAN_MUSIC, new Object());
                return true;
            } else if (preference == mFilterTime) {
                Preferences.saveFilterTime((String) newValue);
                mFilterTime.setSummary(getSummary(Preferences.getFilterTime(), R.array.filter_time_entries, R.array.filter_time_entry_values));
                RxBus.get().post(RxBusTags.SCAN_MUSIC, new Object());
                return true;
            }else if(preference==getmDownloadPathSwitch){
                if((boolean)newValue){
                    mDownloadPath.setEnabled(true);
                }
                else{
                    mDownloadPath.setEnabled(false);
                }
                return true;
            }

            return false;
        }

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == RESULT_OK) {
                if (requestCode == REQUESTCODE_FROM_FRAGMENT) {
                    List<String> list = data.getStringArrayListExtra(Constant.RESULT_INFO);
                    //for (String s : list) {
                    //    Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
                    //}
//                Toast.makeText(getApplicationContext(), "选中了" + list.size() + "个文件", Toast.LENGTH_SHORT).show();
                    String path = data.getStringExtra("path");
                    //Toast.makeText(getApplicationContext(), "选中的路径为" + path, Toast.LENGTH_SHORT).show();
                    Log.i("LeonFilePicker", path);
                    FileUtils.appDir=path;
                    //mDownloadPath
                }
            }
        }

        private String getSummary(String value, int entries, int entryValues) {
            String[] entryArray = getResources().getStringArray(entries);
            String[] entryValueArray = getResources().getStringArray(entryValues);
            for (int i = 0; i < entryValueArray.length; i++) {
                String v = entryValueArray[i];
                if (TextUtils.equals(v, value)) {
                    return entryArray[i];
                }
            }
            return entryArray[0];
        }
    }
}
