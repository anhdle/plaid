package io.plaidapp.ui.span;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;

import in.uncod.android.bypass.style.TouchableUrlSpan;

/**
 * A span for marking up a dribbble player
 */
public class PlayerSpan extends TouchableUrlSpan {

    private final String playerName;
    private final long playerId;
    private final String playerUsername;

    public PlayerSpan(String url,
                      String playerName,
                      long playerId,
                      @Nullable String playerUsername,
                      ColorStateList textColor,
                      int pressedBackgroundColor) {
        super(url, textColor, pressedBackgroundColor);
        this.playerName = playerName;
        this.playerId = playerId;
        this.playerUsername = playerUsername;
    }

}
