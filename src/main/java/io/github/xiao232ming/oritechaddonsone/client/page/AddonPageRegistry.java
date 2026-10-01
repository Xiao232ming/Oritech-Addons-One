package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

/**
 * The pages of the Extension Addon GUI, in the order their tabs appear on the right edge of the panel.
 * <p>
 * This is the only place a new page has to be registered: the screen asks for the list, builds one tab
 * per entry and draws whichever entry is selected. The plugin page is the first (and currently the only)
 * page, so it is also the page a freshly opened GUI shows.
 */
public final class AddonPageRegistry {

    private static final List<AddonPage> PAGES = List.of(new PluginAddonPage());

    private AddonPageRegistry() {
    }

    /** All pages, in tab order. */
    public static List<AddonPage> pages() {
        return PAGES;
    }
}
