package com.microsoft.mimicker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class AlarmSnoozeFragment extends Fragment {
    private static final int SNOOZE_SCREEN_TIMEOUT_DURATION = 3 * 1000;
    SnoozeResultListener mCallback;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_snooze, container, false);
        TextView snoozeDuration = (TextView) view.findViewById(R.id.alarm_snoozed_duration);
        snoozeDuration.setText(getAlarmSnoozeDuration());

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mCallback.onSnoozeDismiss();
            }
        }, SNOOZE_SCREEN_TIMEOUT_DURATION);

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mCallback = (SnoozeResultListener) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback = null;
    }

    private String getAlarmSnoozeDuration() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return preferences.getString("KEY_SNOOZE_DURATION_DISPLAY", getString(R.string.pref_default_snooze_duration));
    }

    public interface SnoozeResultListener {
        void onSnoozeDismiss();
    }
}