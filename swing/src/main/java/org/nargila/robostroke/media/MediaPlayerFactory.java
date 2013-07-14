package org.nargila.robostroke.media;

import java.awt.Container;
import java.io.File;

import org.nargila.robostroke.app.Settings;
import org.nargila.robostroke.data.media.ExternalMedia;
import org.nargila.robostroke.data.media.ExternalMedia.EventListener;
import org.nargila.robostroke.data.media.ExternalMedia.MediaFramework;
import org.nargila.robostroke.data.media.ExternalMedia.VideoEffect;
import org.nargila.robostroke.media.gst.GstExternalMedia;
import org.nargila.robostroke.media.gst.GstFindQrMarkPipeline;
import org.nargila.robostroke.media.jst.JstExternalMedia;
import org.nargila.robostroke.media.jst.JstFindQrMarkPipeline;
import org.nargila.robostroke.media.vlc.VlcExternalMedia;
import org.nargila.robostroke.media.vlc.VlcFindQrMarkPipeline;

public class MediaPlayerFactory {

    private MediaPlayerFactory() {
    }

    public static FindQrMarkPipeline createFindQrMarkPipeline(File video) throws Exception {
        
        switch (getFramework()) {
            case GST:
                return new GstFindQrMarkPipeline(video);
            case VLC:
                return new VlcFindQrMarkPipeline(video);
            case JST:
                return new JstFindQrMarkPipeline(video);
                default:
                    throw new AssertionError("HDIGH!");
        }
    }

    private static MediaFramework getFramework() {
        return Settings.getInstance().getMediaFramework();
    }
    
    public static ExternalMedia createMediaPlayer(File videoFile, Container container, VideoEffect videoEffect, EventListener eventListener) throws Exception {
        ExternalMedia res;
        
        switch (getFramework()) {
            case GST:
                res = new GstExternalMedia(videoFile, container, videoEffect);
                break;
            case VLC:
                res = new VlcExternalMedia(videoFile, container, videoEffect);
                break;
            case JST:
                res = new JstExternalMedia(videoFile, container, videoEffect);
                break;
                default:
                    throw new AssertionError("HDIGH!");
        }        
        
        res.addEventListener(eventListener);
        
        return res;
    }
}