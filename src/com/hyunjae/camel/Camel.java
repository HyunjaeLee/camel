package com.hyunjae.camel;

public class Camel {

    private Camel() {
    }

    public static void build() {
        Builder builder = null;
        try {
            builder = new Builder();
            builder.buildIndex();
            builder.buildFinale();
            builder.buildEpisode();
            builder.buildVideoUrl();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (builder != null)
                builder.close();
        }
    }
}
