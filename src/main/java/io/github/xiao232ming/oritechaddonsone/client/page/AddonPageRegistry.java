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
 *     <li>the Extension Transfer page only makes sense while 扩展传输插件 is stored inside (it is that plugin
 *     that lets the machine's items be fed through the block's faces),</li>
 *     <li>the 传输插件 page only makes sense while 传输插件 is stored inside (it is that plugin that transfers
 *     the machine's items, and the page picks the faces on a model of that machine).</li>
 * </ul>
 * A page therefore appears as another tab as soon as its addon is put in and is gone again when the last one
 * is taken out. Both variants - wired addon and wireless dock - get them.
 * <p>
 * The one exception is a transfer plugin that is <b>placed</b> in the world: that block is not a container at
 * all, its only page is the page of the plugin itself, so it gets exactly that one tab (see
 * {@link #pages(ExtensionAddonMenu)}).
 */
public final class AddonPageRegistry {

    /** The plugin slots every addon and dock has. */
    private static final AddonPage PLUGINS = new PluginAddonPage();

    /** The wireless page: the connected machine and the reserved single item slot. */
    private static final AddonPage WIRELESS = new WirelessAddonPage();

    /** The Item Proxy page: the face net and the machine inventory a face proxies. */
    private static final AddonPage PROXY = new ItemProxyAddonPage();

    /** The Extension Transfer page: the face net and what each face does with the machine's items. */
    private static final AddonPage EXTENSION_TRANSFER = new ExtensionTransferAddonPage();

    /**
     * The page of 传输插件: the machine the plugin serves as a rotatable 3D model, the faces configurable on it.
     * <p>
     * It is the only page of that plugin while the plugin is <b>placed</b>, and it also appears inside an Extension
     * Addon's own screen as soon as one is stored in its plugin slots - the addon's screen then offers the plugin's
     * page next to its own pages. Either way the page configures whichever block the menu addresses: the placed
     * plugin itself, or the addon the plugin is stored in (see
     * {@code ExtensionAddonMenu#transferMachinePos()}).
     */
    private static final AddonPage TRANSFER = new TransferAddonPage();

    private AddonPageRegistry() {
    }

    /**
     * All pages of the given menu, in tab order.
     * <p>
     * The list is per menu and not a constant, because a page can depend on what the block holds - see
     * {@link #pages(boolean, boolean, boolean)} - and because two blocks have exactly one page: a placed
     * 扩展传输插件 is not a container of plugins, so its GUI shows the transfer page of the extender's faces
     * and nothing else, and a placed 传输插件 shows the 3D page of the machine it serves and nothing else. The
     * screen compares this list with the pages it currently shows on every container tick, so taking the last
     * inventory proxy or transfer plugin out removes its tab right away and putting one in adds it right away,
     * without reopening the GUI.
     */
    public static List<AddonPage> pages(ExtensionAddonMenu menu) {
        if (menu.transferOnly()) return List.of(TRANSFER);
        if (menu.extensionTransferOnly()) return List.of(EXTENSION_TRANSFER);

        return pages(menu.hasInventoryProxy(), menu.hasExtensionTransferAddon(), menu.hasTransferAddon());
    }

    /**
     * The pages of a block that does or does not hold an inventory proxy addon and one of the two transfer plugins.
     * <p>
     * All optional pages are offered by both variants - wired addon and wireless dock - and only while the addon they
     * belong to is stored inside: the inventory proxy addon gives the block the ability to proxy one machine slot per
     * face, 扩展传输插件 the ability to feed and empty the machine through the block's faces, and 传输插件 the same
     * transfer with a 3D model of the machine as the way to pick a face. The two transfer plugins are counted
     * separately because their pages are separate: a block that holds both offers both tabs, and neither plugin ever
     * opens the other's page. Keeping the decision in this pure function is what lets the screen ask for the page list
     * of a contents change without building a menu.
     */
    public static List<AddonPage> pages(boolean hasInventoryProxy, boolean hasExtensionTransferAddon,
            boolean hasTransferAddon) {
        var pages = new ArrayList<AddonPage>(5);
        pages.add(PLUGINS);
        pages.add(WIRELESS);
        if (hasInventoryProxy) pages.add(PROXY);
        if (hasExtensionTransferAddon) pages.add(EXTENSION_TRANSFER);
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

    /** The Extension Transfer page instance, the only page of a placed 扩展传输插件. */
    public static AddonPage extensionTransferPage() {
        return EXTENSION_TRANSFER;
    }

    /**
     * The transfer page instance, the only page of 传输插件. The screen compares against it to recognise the
     * page that drags its own model and that hides the player inventory while it is shown.
     */
    public static AddonPage transferPage() {
        return TRANSFER;
    }

    /** True while the given page is the Item Proxy page. */
    public static boolean isProxyPage(AddonPage page) {
        return page == PROXY;
    }
}
