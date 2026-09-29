package com.example.comiku.core;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.comiku.core.firebase.FirebaseProvider;
import com.example.comiku.core.ui.ReactionEmojiProvider;
import com.example.comiku.core.ui.WebmAudioAttachmentFactory;

import java.util.Arrays;

import io.getstream.chat.android.ui.ChatUI;
import io.getstream.chat.android.ui.feature.messages.list.adapter.viewholder.attachment.AttachmentFactoryManager;
import io.getstream.chat.android.ui.feature.messages.list.adapter.viewholder.attachment.UnsupportedAttachmentFactory;

public class ComikuApplication extends Application {

    // Inicia servicios globales
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        FirebaseProvider.initialize(this);
        ChatUI.setSupportedReactions(ReactionEmojiProvider.createSupportedReactions(this));
        ChatUI.setAttachmentFactoryManager(new AttachmentFactoryManager(Arrays.asList(
                new WebmAudioAttachmentFactory(),
                new UnsupportedAttachmentFactory()
        )));
    }
}
