package com.example.tomrenn.textwrapper;

import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by tomrenn on 2/21/15.
 */
public class TextWrapHelper {
    private static final Pattern DEFAULT_END_PUNCTUATION = Pattern.compile("[\\.,\u2026;\\:\\s]*$", Pattern.DOTALL);


    public static CharSequence preformWrapping(TextView textView, int availableHeight, CharSequence fullText) {
        CharSequence workingText = fullText;
        CharSequence clippedText = "";

        Layout layout = createWorkingLayout(textView, workingText);
        int linesCount = getLinesCount(textView, availableHeight);
        if (layout.getLineCount() > linesCount) {
            // We have more lines of text than we are allowed to display.
            int lineEnd = layout.getLineEnd(linesCount-1);
            workingText = fullText.subSequence(0, lineEnd);
            clippedText = fullText.subSequence(lineEnd, fullText.length());

            while (createWorkingLayout(textView, workingText).getLineCount() > linesCount) {
                int lastSpace = workingText.toString().lastIndexOf(' ');
                if (lastSpace == -1) {
                    break;
                }
                workingText = workingText.subSequence(0, lastSpace);
                clippedText = fullText.subSequence(lastSpace, fullText.length());
            }
            // We should do this in the loop above, but it's cheaper this way.
            if(workingText instanceof Spannable) {
                SpannableStringBuilder sb = new SpannableStringBuilder(workingText);

                Matcher m = DEFAULT_END_PUNCTUATION.matcher(workingText);
                if(m.find()) {
                    int start = m.start();
                    sb.replace(start, workingText.length(), "");
                }

                workingText = sb;

            } else {
                workingText = DEFAULT_END_PUNCTUATION.matcher(workingText).replaceFirst("");
                workingText = workingText + "";
            }

//            ellipsized = true;
        }
        if (!workingText.equals(textView.getText())) {
            textView.setText(workingText);
        }
        return clippedText;
    }

    /**
     * Get how many lines of text we are allowed to display.
     */
    private static int getLinesCount(TextView textView, int availableHeight) {
        if (Build.VERSION.SDK_INT > 15 && textView.getMaxLines() != Integer.MAX_VALUE) {
            return textView.getMaxLines();
        }
        int fullyVisibleLinesCount = getFullyVisibleLinesCount(textView, availableHeight);
        if (fullyVisibleLinesCount == -1) {
            return 1;
        } else {
            return fullyVisibleLinesCount;
        }
    }

    private static int getFullyVisibleLinesCount(TextView textView, int availableHeight) {
        Layout layout = createWorkingLayout(textView, "");
        int lineHeight = layout.getLineBottom(0);
        return availableHeight / lineHeight;
    }

    private static Layout createWorkingLayout(TextView textView, CharSequence workingText) {
        float lineSpacingMult = 1.0f;
        float lineSpacingAdd = 0f;

        if (Build.VERSION.SDK_INT > 15){
            lineSpacingMult = textView.getLineSpacingMultiplier();
            lineSpacingAdd = textView.getLineSpacingExtra();
        }

        return new StaticLayout(workingText, textView.getPaint(),
                textView.getMeasuredWidth() - textView.getPaddingLeft() - textView.getPaddingRight(),
                Layout.Alignment.ALIGN_NORMAL, lineSpacingMult,
                lineSpacingAdd, false);
    }
}
