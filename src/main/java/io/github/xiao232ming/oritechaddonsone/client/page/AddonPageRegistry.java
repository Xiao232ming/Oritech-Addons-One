package io.github.xiao232ming.oritechaddonsone.client.page;

import java.util.ArrayList;
import java.util.List;

import io.github.xiao232ming.oritechaddonsone.menu.ExtensionAddonMenu;

/**
 * The pages of the Extension Addon GUI, in the order their tabs appear on the right edge of the panel.
 * <p>
 * This is the only place a new page has to be registered: the screen asks for the list of the menu it is
 * showing, builds one tab per entry and draws whichever entry is selected. The plugin page is the first
 * page, so it is also the page a freshly opened GUI shows.
 * <p>
 * The list is built per menu, because a page can depend on what the block holds:
 * <ul>
 *     <li>the Item Proxy page only makes sense while an Oritech inventory proxy addon is stored inside (it is
 *     that addon that gives the block the ability to proxy a machine inventory),</li>
 *     <li>the Extension Transfer page only makes sense while a transfer addon of this mod is stored inside
 *     (it is that addon that lets the machine's items be fed through the block's faces).</li>
 * </ul>
 * A page therefore appears as another tab as soon as its addon is put in and is gone again when the last one
 * is taken out. Both variants - wired addon and wireless dock - get them.
 * <p>
 * The one exception is a transfer addon that is <b>placed</b> on an Oritech machine extender: that block is
 * not a container at all, its only page is the transfer page of the extender's faces, so it gets exactly that
 * one tab (see {@link #pages(ExtensionAddonMenu)}).
 */
public final class AddonPageRegistry {

    /** The plugin slots every addon and dock has. */
    private static final AddonPage PLUGINS = new PluginAddonPage();

    /** The wireless page: the connected machine and the reserved single item slot. */
    private static final AddonPage WIRELESS = new WirelessAddonPage();

    /** The Item Proxy page: the face net and the machine inventory a face proxies. */
    private static final AddonPage PROXY = new ItemProxyAddonPage();

    /** The Extension Transfer page: the face net and what each face does with the machine's items. */
    private static final AddonPage TRANSFER = new TransferAddonPage();

    /**
     * The page of 传输插件: the machine the plugin serves as a rotatable 3D model, the faces configurable on it. It is
     * the only page of that plugin, and it never appears for any other block - the plugin is not a container of
     * plugins and has no machine of its own to wire up (see {@link #pages(ExtensionAddonMenu)}).
     */
    private static final AddonPage TRANSFER_PREVIEW = new TransferPreviewAddonPage();

    private AddonPageRegistry() {
    }

    /**
     * All pages of the given menu, in tab order.
     * <p>
     * The list is per menu and not a constant, because a page can depend on what the block holds - see
     * {@link #pages(boolean, boolean)} - and because two blocks have exactly one page: a transfer addon that is
     * <b>placed</b> on an Oritech machine extender is not a container of plugins, so its GUI shows the transfer page
     * of the extender's faces and nothing else, and 传输插件 shows the 3D preview of the machine it serves and nothing
     * else. The screen compares this list with the pages it currently shows on every container tick, so taking the
     * last inventory proxy or transfer addon out removes its tab right away and putting one in adds it right away,
     * without reopening the GUI.
     */
    public static List<AddonPage> pages(ExtensionAddonMenu menu) {
        if (menu.previewOnly()) return List.of(TRANSFER_PREVIEW);
        if (menu.transferOnly()) return List.of(TRANSFER);

        return pages(menu.hasInventoryProxy(), menu.hasTransferAddon());
    }

    /**
     * The pages of a block that does or does not hold an inventory proxy addon and a transfer addon.
     * <p>
     * Both optional pages are offered by both variants - wired addon and wireless dock - and only while the
     * addon they belong to is stored inside: the inventory proxy addon gives the block the ability to proxy
     * one machine slot per face, the transfer addon the ability to feed and empty the machine through its
     * faces. Keeping the decision in this pure function is what lets the screen ask for the page list of a
     * contents change without building a menu.
     */
    public static List<AddonPage> pages(boolean hasInventoryProxy, boolean hasTransferAddon) {
        var pages = new ArrayList<AddonPage>(4);
        pages.add(PLUGINS);
        pages.add(WIRELESS);
        if (hasInventoryProxy) pages.add(PROXY);
        if (hasTransferAddon) pages.add(TRANSFER);
        return List.copyOf(pages);
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

    /** The Extension Transfer page instance. */
    public static AddonPage transferPage() {
        return TRANSFER;
    }

    /**
     * The transfer preview page instance, the only page of 传输插件. The screen compares against it to recognise the
     * page that drags its own model and that hides the player inventory while it is shown.
     */
    public static AddonPage transferPreviewPage() {
        return TRANSFER_PREVIEW;
    }

    /** True while the given page is the Item Proxy page. */
    public static boolean isProxyPage(AddonPage page) {
        return page == PROXY;
    }
}
