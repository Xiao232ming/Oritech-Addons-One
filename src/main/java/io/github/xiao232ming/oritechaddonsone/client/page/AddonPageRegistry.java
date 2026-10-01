package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.List;

import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * The pages of the Extension Addon GUI, in the order their tabs appear on the right edge of the panel.
 * <p>
 * This is the only place a new page has to be registered: the screen asks for the list of the menu it is
 * showing, builds one tab per entry and draws whichever entry is selected. The plugin page is the first
 * page, so it is also the page a freshly opened GUI shows.
 * <p>
 * The list depends on the block the menu belongs to ({@link ExtensionAddonMenu#wireless()}), so a page
 * that only makes sense for one of the two variants is one entry away. Both variants currently show the
 * same two pages: the wireless page reads what it shows from the menu, so a wired addon shows the machine
 * it is attached to while a dock adds the link's coordinates and the chunk state.
 */
public final class AddonPageRegistry {

    /** The plugin slots every addon and dock has. */
    private static final AddonPage PLUGINS = new PluginAddonPage();

    /** The wireless page: the connected machine and the reserved single item slot. */
    private static final AddonPage WIRELESS = new WirelessAddonPage();

    /** Pages of a wired Extension Addon. */
    private static final List<AddonPage> WIRED_PAGES = List.of(PLUGINS, WIRELESS);

    /** Pages of a Wireless Extension Dock. */
    private static final List<AddonPage> DOCK_PAGES = List.of(PLUGINS, WIRELESS);

    private AddonPageRegistry() {
    }

    /** All pages of the given menu, in tab order. */
    public static List<AddonPage> pages(ExtensionAddonMenu menu) {
        return menu.wireless() ? DOCK_PAGES : WIRED_PAGES;
    }

    /**
     * The wireless page instance. Used by the screen to recognise the page whose slots are the reserved
     * ones; the page itself is stateless, so a single instance serves every block.
     */
    public static AddonPage wirelessPage() {
        return WIRELESS;
    }

    /** The plugin page instance, the page a freshly opened GUI shows. */
    public static AddonPage pluginPage() {
        return PLUGINS;
    }
}
