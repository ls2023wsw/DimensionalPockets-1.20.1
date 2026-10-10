package net.gtn.dimensionalpocket.client;

import net.gtn.dimensionalpocket.common.CommonProxy;

/**
 * Client side proxy.
 *
 * <p>1.7.10 origin: {@code client/ClientProxy.java}. In 1.7.10 the proxy did the client wiring
 * ({@code MinecraftForgeClient.registerItemRenderer}, {@code ClientRegistry.bindTileEntitySpecialRenderer},
 * event bus registration, {@code ClientCommandHandler.registerCommand}). All of that is event driven in
 * 1.20.1 and lives in {@link ClientSetup} (mod bus) and
 * {@link net.gtn.dimensionalpocket.client.event.ClientEventHandler} (forge bus), so this class only
 * keeps the one piece of state 1.7.10 stored here:
 *
 * <ul>
 *   <li>{@link #currentPage} - the info book remembers the page it was closed on for the whole client
 *       session. It cannot live in the screen itself: the book is a pure client
 *       {@code Screen} created with {@code new InfoBookScreen()} (no menu, see contract §4.1).</li>
 * </ul>
 *
 * <p>{@code getClientGuiElement} (the 1.7.10 {@code IGuiHandler} entry point) is gone in 1.20.1, so
 * there is nothing to override here.
 */
public class ClientProxy extends CommonProxy {

    /** 1.7.10: {@code public static int currentPage = 0}. */
    public static int currentPage = 0;

    public ClientProxy() {
    }
}
