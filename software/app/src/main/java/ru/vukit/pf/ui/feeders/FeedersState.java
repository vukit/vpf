package ru.vukit.pf.ui.feeders;

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Keep
public class FeedersState {
    private static FeedersState INSTANCE = null;

    final List<HashMap<String, String>> feeders = new ArrayList<>();

    public static synchronized FeedersState getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FeedersState();
        }
        return (INSTANCE);
    }
}
