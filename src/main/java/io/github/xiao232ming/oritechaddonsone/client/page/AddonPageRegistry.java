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
 * The list is built per menu, because a page can depend on what the block holds: the Item Proxy page only
 * makes sense while an Oritech inventory proxy addon is stored inside (it is that addon that gives the
 * block the ability to proxy a machine inventory), so it appears as a third tab when it does and is gone
 * again when the last proxy addon is taken out. Both variants - wired addon and wireless dock - get it.
 */
public final class AddonPageRegistry {

    /** The plugin slots every addon and dock has. */
    private static final AddonPage PLUGINS = new PluginAddonPage();

    /** The wireless page: the connected machine and the reserved single item slot. */
    private static final AddonPage WIRELESS = new WirelessAddonPage();

    /** The Item Proxy page: the face net and the machine inventory a face proxies. */
    private static final AddonPage PROXY = new ItemProxyAddonPage();

    private AddonPageRegistry() {
    }

    /** All pages of the given menu, in tab order. */
    public static List<AddonPage> pages(ExtensionAddonMenu menu) {
        // the proxy page is offered by both variants, and only while an inventory proxy addon is inside
        if (!menu.hasInventoryProxy()) return List.of(PLUGINS, WIRELESS);
        return List.of(PLUGINS, WIRELESS, PROXY);
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

    /** The Item Proxy page instance. */
    public static AddonPage proxyPage() {
        return PROXY;
    }

    /** True while the given page is the Item Proxy page. */
    public static boolean isProxyPage(AddonPage page) {
        return page == PROXY;
    }
}
