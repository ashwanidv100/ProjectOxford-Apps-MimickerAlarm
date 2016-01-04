package com.microsoft.mimicker;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.graphics.ColorUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalyzeResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

public class GameColorFinderFragment extends GameWithCameraFragment {
    private VisionServiceRestClient mVisionServiceRestClient;
    private String mQuestionColorName;
    private float[] mQuestionColorRangeLower;
    private float[] mQuestionColorRangeUpper;

    public GameColorFinderFragment() {
        CameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        Resources resources = getResources();

        String subscriptionKey = Util.getToken(getActivity(), "vision");
        mVisionServiceRestClient = new VisionServiceRestClient(subscriptionKey);

        String[] questions = resources.getStringArray(R.array.vision_color_questions);
        TextView instruction = (TextView) view.findViewById(R.id.instruction_text);
        mQuestionColorName = questions[new Random().nextInt(questions.length)];
        instruction.setText(String.format(resources.getString(R.string.game_vision_prompt), mQuestionColorName));

        TypedArray colorCodeLower = resources.obtainTypedArray(resources.getIdentifier(mQuestionColorName + "_range_lower", "array", getActivity().getPackageName()));
        mQuestionColorRangeLower = new float[]{colorCodeLower.getFloat(0, 0f), colorCodeLower.getFloat(1, 0f), colorCodeLower.getFloat(2, 0f)};
        colorCodeLower.recycle();
        TypedArray colorCodeUpper = resources.obtainTypedArray(resources.getIdentifier(mQuestionColorName + "_range_upper", "array", getActivity().getPackageName()));
        mQuestionColorRangeUpper = new float[]{colorCodeUpper.getFloat(0, 0f), colorCodeUpper.getFloat(1, 0f), colorCodeUpper.getFloat(2, 0f)};
        colorCodeUpper.recycle();

        Logger.init(getActivity());
        Loggable playGameEvent = new Loggable.UserAction(Loggable.Key.ACTION_GAME_COLOR);
        Logger.track(playGameEvent);

        return view;
    }

    @Override
    public GameResult verify(Bitmap bitmap) {
        GameResult gameResult = new GameResult();
        gameResult.question = ((TextView) getView().findViewById(R.id.instruction_text)).getText().toString();

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());
            String[] features = {"Color"};
            Loggable.AppAction appAction = new Loggable.AppAction(Loggable.Key.APP_API_VISION);
            Logger.trackDurationStart(appAction);
            AnalyzeResult result = mVisionServiceRestClient.analyzeImage(inputStream, features);
            Logger.track(appAction);

            float[] accentHsl = new float[3];
            int[] accentRgb = hexStringToRgb(result.color.accentColor);
            ColorUtils.RGBToHSL(accentRgb[0], accentRgb[1], accentRgb[2], accentHsl);
            boolean colorInRange = isColorInRange(mQuestionColorRangeLower, mQuestionColorRangeUpper, accentHsl);
            Loggable.UserAction userAction = new Loggable.UserAction(Loggable.Key.ACTION_GAME_COLOR_SUCCESS);
            userAction.putProp(Loggable.Key.PROP_QUESTION, mQuestionColorName);
            userAction.putVision(result);

            //TODO: this will not work for languages other than English.
            if (colorInRange ||
                    result.color.dominantColorForeground.toLowerCase().equals(mQuestionColorName) ||
                    result.color.dominantColorBackground.toLowerCase().equals(mQuestionColorName)) {
                gameResult.success = true;
            }

            for (String color : result.color.dominantColors) {
                if (color.toLowerCase().equals(mQuestionColorName)) {
                    gameResult.success = true;
                    break;
                }
            }

            if (!gameResult.success) {
                userAction.Name = Loggable.Key.ACTION_GAME_COLOR_FAIL;
            }

            Logger.track(userAction);
        } catch (Exception ex) {
            Logger.trackException(ex);
        }

        return gameResult;
    }

    @Override
    protected void gameFailure(GameResult gameResult, boolean allowRetry) {
        if (!allowRetry) {
            Loggable.UserAction userAction = new Loggable.UserAction(Loggable.Key.ACTION_GAME_COLOR_TIMEOUT);
            userAction.putProp(Loggable.Key.PROP_QUESTION, mQuestionColorName);
            Logger.track(userAction);
        }
        super.gameFailure(gameResult, allowRetry);
    }

    private int[] hexStringToRgb(String hex) {
        int color = (int) Long.parseLong(hex, 16);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = (color) & 0xFF;
        return new int[]{r, g, b};
    }

    private boolean isColorInRange(float[] lowerHsl, float[] upperHsl, float[] queryHsl) {
        boolean result = true;
        for (int i = 0; i < 3; i++) {
            //looped around the color wheel
            if (upperHsl[i] < lowerHsl[i]) {
                result &= (queryHsl[i] >= lowerHsl[i] && queryHsl[i] <= 360)
                        || (queryHsl[i] >= 0 && queryHsl[i] <= upperHsl[i]);
            } else {
                result &= (queryHsl[i] >= lowerHsl[i] && queryHsl[i] <= upperHsl[i]);
            }
        }

        Logger.local("HSL 1: " + lowerHsl[0] + " " + lowerHsl[1] + " " + lowerHsl[2]);
        Logger.local("HSL 2: " + upperHsl[0] + " " + upperHsl[1] + " " + upperHsl[2]);
        Logger.local("question HSL 2: " + queryHsl[0] + " " + queryHsl[1] + " " + queryHsl[2]);
        Logger.local("result: " + result);

        return result;
    }
}